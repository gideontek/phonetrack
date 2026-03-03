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
            ├── kotlin/net/gideontek/phonetrack/
            │   ├── MainActivity.kt        # Compose UI + ViewModel
            │   ├── SmsReceiver.kt         # BroadcastReceiver (one-shot / subscribe / unsubscribe)
            │   ├── SmsLocationService.kt  # ForegroundService — one-shot location reply
            │   ├── Subscription.kt        # Subscription data class + SubscriptionManager
            │   ├── SubscriptionService.kt # ForegroundService — periodic location loop
            │   └── BootReceiver.kt        # BOOT_COMPLETED: auto-start + resume subscriptions
            └── res/values/
                ├── strings.xml
                └── themes.xml
```

## Key Facts

- Package: `net.gideontek.phonetrack`
- Min SDK: 26 | Target SDK: 35 | Compile SDK: 35
- Kotlin 2.1.10 + AGP 8.8.0 + Gradle 8.12.1
- Jetpack Compose (no XML layouts)
- SharedPreferences file: `"phonetrack_prefs"` — keys:
  - `sms_enabled` (Boolean)
  - `sms_keyword` (String, default `"phonetrack"`)
  - `auto_start_on_boot` (Boolean)
  - `block_all` (Boolean) — reject all DEFAULT-state numbers when true
  - `approvals_list` (JSON array of `{number, state}` where state ∈ DEFAULT/APPROVED/BLOCKED)
  - `subscriptions_list` (JSON array of Subscription objects)
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
