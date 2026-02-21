# PhoneTrack Android 2026 — Claude Code Instructions

## Plan Reference

The active implementation plan is at `plans/MICRO_MVP_PLAN.md`.
Read it before making any structural changes to the Android project.

## Project Layout

```
phonetrack-android-2026/
├── CLAUDE.md                   # this file
├── plans/
│   └── MICRO_MVP_PLAN.md       # micro-MVP specification
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
            │   ├── SmsReceiver.kt         # BroadcastReceiver
            │   └── SmsLocationService.kt  # ForegroundService
            └── res/values/
                ├── strings.xml
                └── themes.xml
```

## Key Facts

- Package: `net.gideontek.phonetrack`
- Min SDK: 26 | Target SDK: 35 | Compile SDK: 35
- Kotlin 2.1.10 + AGP 8.8.0 + Gradle 8.12.1
- Jetpack Compose (no XML layouts)
- SharedPreferences file: `"phonetrack_prefs"` — keys: `sms_enabled` (Boolean), `sms_keyword` (String), `auto_start_on_boot` (Boolean)
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
