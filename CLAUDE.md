# PhoneTrack Android 2026 — Claude Code Instructions

## Project Goals

PhoneTrack is an open source Android app that focuses on location sharing over SMS messages without using data. It has a simple interface and defaults to common use cases but provides overrides when needed.

## Design Constraints

These rules must not be violated when making changes:

- **No internet.** The `INTERNET` permission must never be added. All communication is SMS-only.
- **No third-party libraries.** Only standard AndroidX. Do not add external dependencies to `build.gradle.kts`.
- **No databases or files.** SharedPreferences (`"phonetrack_prefs"`) is the only persistent storage. Do not add Room, SQLite, or file I/O.
- **No WorkManager for location.** The periodic location loop uses a Handler-based `ForegroundService` (`SubscriptionService`) — not WorkManager — to avoid Doze-mode deferrals. Keep it that way.
- **No XML layouts.** Jetpack Compose only.

## Project Layout

```
phonetrack-android-2026/
├── CLAUDE.md                   # this file
└── phonetrack/                 # Android Studio project
    ├── build.gradle.kts        # root Gradle file (plugin declarations)
    ├── settings.gradle.kts     # module includes + repo config
    ├── gradle.properties
    ├── local.properties        # SDK path (not committed)
    ├── gradlew / gradlew.bat
    ├── gradle/wrapper/
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── kotlin/com/gideontek/phonetrack/
            │   ├── MainActivity.kt        # Activity + theme; hosts AppHost
            │   ├── Navigation.kt          # Screen enum (Main/Settings), AppHost: shared state + Back handling
            │   ├── MainScreen.kt          # Main screen: composes the sections below; PIN guard on approval changes
            │   ├── MainUiState.kt         # Pure: status summary, relative-time text, subscription/approval view data
            │   ├── StatusCard.kt          # Status card: compact stream indicator, Listening switch, Fix banner
            │   ├── DecisionsSection.kt    # Pending numbers: tap-to-reveal Approve/Block (no swiping)
            │   ├── SubscriptionsSection.kt # Active subscriptions with Send now / Cancel
            │   ├── KnownNumbersSection.kt # Collapsed approved + blocked list
            │   ├── SettingsScreen.kt      # Settings screen (switch, keyword, permissions, reply contents, About)
            │   ├── HomeViewModel.kt       # Activity-scoped ViewModel shared by both screens
            │   ├── PermissionsState.kt    # rememberPermissionsState(): grant state, launchers, resume hook
            │   ├── PinDialogs.kt          # Set/unlock PIN dialogs, lock button, guard() for on-demand unlock
            │   ├── SmsReceiver.kt         # BroadcastReceiver (gate + dispatch on SmsCommand)
            │   ├── SmsCommand.kt          # Sealed parsed command (one-shot / subscribe / unsubscribe / last / help)
            │   ├── SmsCommandParser.kt    # Pure parser + subscribe flag validation
            │   ├── SmsLimits.kt           # Command bounds, body cap, keyword sanitizing
            │   ├── SmsComposer.kt         # Pure reply-text builders
            │   ├── SmsSender.kt           # Outgoing SMS (multipart, crash-safe)
            │   ├── LastKnownLocation.kt   # Newest cached fix for the `last` command
            │   ├── PhoneNumber.kt         # Pure number normalize / matches / isReplyable
            │   ├── ApprovalLogic.kt       # Pure approval rules + ApprovalEntry
            │   ├── ApprovalStore.kt       # Prefs/JSON store for the approvals list (synchronized)
            │   ├── NumberMigration.kt     # Pure merge logic for the numbers migration
            │   ├── PrefsMigration.kt      # One-time versioned upgrade of stored data
            │   ├── ReplyOptions.kt        # Pure: which parts a location reply contains (default: coordinates, accuracy, battery, map link)
            │   ├── ReplyOptionsStore.kt   # Prefs store for the reply options
            │   ├── ReplySettingsCard.kt   # Compose "Reply contents" card with live preview
            │   ├── SmsLength.kt           # Pure: does text fit one SMS (GSM-7 160 / UCS-2 70)
            │   ├── DeviceStatus.kt        # Battery percent + charging for replies
            │   ├── PinHasher.kt           # Pure PBKDF2 hashing for the settings PIN
            │   ├── PinLockout.kt          # Pure failed-attempt lockout rules (1/5/15/60 min)
            │   ├── PinMigration.kt        # Pure decision for hashing a legacy plaintext PIN
            │   ├── PinStore.kt            # Prefs store for the PIN hash + lockout state
            │   ├── RateLimiter.kt         # Pure fixed-window rate limiter
            │   ├── RateStore.kt           # Prefs/JSON store for rate-limiter state (synchronized)
            │   ├── SmsLocationService.kt  # ForegroundService — one-shot location reply
            │   ├── Subscription.kt        # Subscription data class + SubscriptionManager
            │   ├── SubscriptionService.kt # ForegroundService — periodic location loop
            │   └── BootReceiver.kt        # BOOT_COMPLETED: resume subscriptions
            └── res/values/
                ├── strings.xml
                └── themes.xml
```

## Key Facts

- Package: `com.gideontek.phonetrack`
- Min SDK: 26 | Target SDK: 35 | Compile SDK: 35
- Kotlin 2.1.10 + AGP 8.8.0 + Gradle 8.12.1
- Jetpack Compose (no XML layouts)
- SharedPreferences file: `"phonetrack_prefs"` — keys:
  - `sms_enabled` (Boolean)
  - `sms_keyword` (String, default `"phonetrack"`)
  - `settings_pin_hash` (String `v1:<iterations>:<saltB64>:<hashB64>`, PBKDF2; locks the settings UI). The legacy plaintext `settings_pin` is migrated away (`prefs_schema_version` 2)
  - `pin_fail_count` / `pin_locked_until` / `pin_lock_level` (Int / Long epoch ms / Int; PIN lockout state, see `PinLockout`)
  - `approvals_list` (JSON array of `{number, state, firstSeen, lastSeen}` where state ∈ PENDING/APPROVED/BLOCKED; numbers stored normalized, timestamps epoch ms)
  - `subscriptions_list` (JSON array of Subscription objects; numbers stored normalized)
  - `prefs_schema_version` (Int; 1 = numbers normalized, 2 = PIN hashed; see `PrefsMigration`)
  - `max_subscriptions` (Int, default 10, coerced to 1..20; concurrent non-expired subscriptions)
  - `rate_limit_per_hour` (Int, default 20, min 1; commands per approved sender per hour)
  - `rate_state` (JSON object of `key -> {start, count, noticed}`; see `RateLimiter`/`RateStore`)
  - `reply_coords` / `reply_accuracy` / `reply_battery` / `reply_time` / `reply_geo` / `reply_osm` (Boolean; what a location reply contains; absent = default, which has `reply_coords`, `reply_accuracy`, `reply_battery` and `reply_osm` on and `reply_time`, `reply_geo` off)
  - `last_receive_at` / `last_send_at` (Long, epoch ms; drive the stream status indicator)
- No third-party libraries; only standard AndroidX

## Build Commands

```bash
cd phonetrack
./gradlew lint           # must be zero errors
./gradlew assembleDebug  # produces app/build/outputs/apk/debug/app-debug.apk
```

## Android SDK

Located at `~/Android/Sdk`. `local.properties` must contain:
```
sdk.dir=/home/user/Android/Sdk
```
