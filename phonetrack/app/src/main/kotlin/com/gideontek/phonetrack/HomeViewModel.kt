package com.gideontek.phonetrack

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun lockedOutMessage(remainingMs: Long) =
    "Too many incorrect attempts. Try again in ${PinLockout.formatRemaining(remainingMs)}."

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean("sms_enabled", false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _keyword = MutableStateFlow(
        prefs.getString("sms_keyword", "phonetrack") ?: "phonetrack"
    )
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private val _pinSet = MutableStateFlow(PinStore.isSet(app))
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    // Starts locked whenever a PIN has been set; resets on every process start.
    private val _isLocked = MutableStateFlow(PinStore.isSet(app))
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _replyOptions = MutableStateFlow(ReplyOptionsStore.read(app))
    val replyOptions: StateFlow<ReplyOptions> = _replyOptions.asStateFlow()

    private val _approvalsList = MutableStateFlow(parseApprovalsList())
    val approvalsList: StateFlow<List<Pair<String, ApprovalState>>> = _approvalsList.asStateFlow()

    private val _subscriptions = MutableStateFlow(SubscriptionManager.getAll(app))
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    // Timestamps of the most recent inbound SMS request / outbound SMS reply, written by
    // SmsReceiver and SmsSender respectively. Drives the live StreamStatusIndicator state
    // via StreamActivityLogic — see MainScreen.
    private val _lastReceiveAt = MutableStateFlow(prefs.getLong("last_receive_at", 0L))
    val lastReceiveAt: StateFlow<Long> = _lastReceiveAt.asStateFlow()

    private val _lastSendAt = MutableStateFlow(prefs.getLong("last_send_at", 0L))
    val lastSendAt: StateFlow<Long> = _lastSendAt.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith(ReplyOptionsStore.KEY_PREFIX)) {
            _replyOptions.value = ReplyOptionsStore.read(app)
        }
        when (key) {
            "sms_enabled" -> _enabled.value = prefs.getBoolean("sms_enabled", false)
            "sms_keyword" -> _keyword.value =
                prefs.getString("sms_keyword", "phonetrack") ?: "phonetrack"
            "settings_pin_hash", "settings_pin" -> _pinSet.value = PinStore.isSet(app)
            "approvals_list" -> _approvalsList.value = parseApprovalsList()
            "subscriptions_list" -> _subscriptions.value = SubscriptionManager.getAll(app)
            "last_receive_at" -> _lastReceiveAt.value = prefs.getLong("last_receive_at", 0L)
            "last_send_at" -> _lastSendAt.value = prefs.getLong("last_send_at", 0L)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        // Registered first so the migration's writes refresh the flows above.
        PrefsMigration.run(app)
        // Drop pending numbers that haven't contacted us in a month.
        ApprovalStore.prune(app)
        // The "start on boot" setting was removed: the listener state is persisted and simply
        // restored after a reboot. Drop the stale key left behind by older versions.
        if (prefs.contains("auto_start_on_boot")) {
            prefs.edit().remove("auto_start_on_boot").apply()
        }
    }

    private fun parseApprovalsList(): List<Pair<String, ApprovalState>> =
        ApprovalStore.getAll(getApplication())
            .map { it.number to it.state }
            .sortedBy { (_, state) -> ApprovalLogic.sortKey(state) }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("sms_enabled", value).apply()
    }

    fun setKeyword(value: String) {
        val clean = SmsLimits.sanitizeKeyword(value)
        _keyword.value = clean
        prefs.edit().putString("sms_keyword", clean).apply()
    }

    /** Saves what location replies contain (an all-off set becomes "just the map link"). */
    fun setReplyOptions(options: ReplyOptions) {
        ReplyOptionsStore.write(getApplication(), options)
        _replyOptions.value = options.normalized()
    }

    /** Save a new PIN and leave the session unlocked. */
    fun setPin(pin: String) {
        PinStore.set(getApplication(), pin)
        _pinSet.value = true
        _isLocked.value = false
    }

    /**
     * Unlocks if [entered] matches the stored PIN. Wrong guesses are counted and trigger
     * escalating lockouts (see [PinLockout]); the result says which happened.
     */
    fun unlock(entered: String): PinResult {
        val result = PinStore.verify(getApplication(), entered)
        if (result is PinResult.Success) _isLocked.value = false
        return result
    }

    /** Milliseconds until a PIN guess is accepted again, or 0 if guessing is allowed now. */
    fun pinLockRemainingMs(): Long = PinStore.lockRemainingMs(getApplication())

    fun lock() {
        _isLocked.value = true
    }

    /** Clears the stored PIN, leaving settings permanently unlocked until a new PIN is set. */
    fun removePin() {
        PinStore.remove(getApplication())
        _pinSet.value = false
        _isLocked.value = false
    }

    fun setNumberState(number: String, state: ApprovalState) {
        ApprovalStore.setState(getApplication(), number, state)
        _approvalsList.value = parseApprovalsList()
    }

    /**
     * Debug-only: simulates a receive/send event pair so the real [StreamActivityLogic]
     * window (and decay) can be previewed without a real SMS. Writes through the same
     * prefs keys SmsReceiver/SmsSender use, so the UI reacts exactly as it would in
     * production — see the debug cycle button in MainScreen (BuildConfig.DEBUG only).
     */
    fun debugSimulateStream(target: StreamState) {
        val now = System.currentTimeMillis()
        val past = 0L
        prefs.edit()
            .putLong("last_receive_at", if (target.receivesInbound) now else past)
            .putLong("last_send_at", if (target.sendsOutbound) now else past)
            .apply()
    }

    fun cancelSubscription(number: String) {
        val ctx = getApplication<Application>()
        SmsSender.sendSubscriptionCancelled(ctx, number)
        SubscriptionManager.remove(ctx, number)
        _subscriptions.value = SubscriptionManager.getAll(ctx)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
