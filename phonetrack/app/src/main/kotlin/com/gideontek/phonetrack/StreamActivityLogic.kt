package com.gideontek.phonetrack

/**
 * Derives the [StreamState] shown by [StreamStatusIndicator] from the timestamps of the
 * most recent inbound SMS request and outbound SMS reply, plus whether a subscription is
 * currently active.
 */
object StreamActivityLogic {

    /**
     * How long a receive/send event keeps the indicator active for a one-shot (non-
     * subscription) request. Such a request (SMS received → location fetched → reply
     * sent) can take up to a minute end-to-end — see [SmsLocationService]'s and
     * [SubscriptionService]'s own 60s location-fetch timeouts — so a single window
     * applied to both directions keeps the indicator live for roughly that long even
     * when the actual fetch+send completes quickly.
     */
    const val ACTIVITY_WINDOW_MS = 60_000L

    /**
     * @param hasActiveSubscription True while at least one subscription is live
     * (see [SubscriptionManager.hasActive]). An active subscription keeps the outbound
     * (sending) side on continuously for its whole lifetime — periodic sends are
     * infrequent (default every 15 min), so gating purely on recency would leave the
     * indicator dark between ticks despite the subscription still running. It reverts
     * to the recency window once the subscription ends (expires or is cancelled).
     */
    fun currentState(
        lastReceiveAt: Long,
        lastSendAt: Long,
        now: Long,
        hasActiveSubscription: Boolean
    ): StreamState {
        // TODO: unlike `sending`, `receiving` has no "active for the duration of a
        // subscription" branch. PhoneTrack is currently one-directional — this device
        // only ever replies to requests for its own location; there's no code path
        // where it subscribes to and receives another device's periodic location (no
        // inbound-location parsing, no tracked "remote target"). If that's ever added,
        // `receiving` should get the same hasActiveSubscription-style treatment
        // `sending` has here.
        val receiving = now - lastReceiveAt < ACTIVITY_WINDOW_MS
        val sending = hasActiveSubscription || now - lastSendAt < ACTIVITY_WINDOW_MS
        return when {
            sending && receiving -> StreamState.Both
            sending -> StreamState.Sending
            receiving -> StreamState.Receiving
            else -> StreamState.Inactive
        }
    }
}
