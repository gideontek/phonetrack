# PhoneTrack Android 2026 — Claude Code Instructions

## Project Goals

PhoneTrack is an open source Android app that focuses on location sharing over SMS messages without using data. It has a simple interface and defaults to common use cases but provides overrides when needed.

## Design Constraints

These rules must not be violated when making changes:

- **No internet.** The `INTERNET` permission must never be added. All communication is SMS-only.
- **No third-party libraries.** Only standard AndroidX. Do not add external dependencies to `build.gradle.kts`. The only additions allowed beyond the runtime libraries are test-only AndroidX ones (`androidTestImplementation` and `debugImplementation` of `ui-test-manifest`); they must never reach the release build, which F-Droid builds (see "Testing").
- **No databases or files.** SharedPreferences (`"phonetrack_prefs"`) is the only persistent storage. Do not add Room, SQLite, or file I/O.
- **No WorkManager for location.** The periodic location loop uses a Handler-based `ForegroundService` (`SubscriptionService`) — not WorkManager — to avoid Doze-mode deferrals. Keep it that way.
- **No XML layouts.** Jetpack Compose only.

## Project Layout

```
phonetrack-android-2026/
├── CLAUDE.md                   # this file
└── phonetrack/                 # Android Studio project
    ├── scripts/                # test-avd.sh, run-e2e.sh, e2e/*.sh (host-driven scenarios)
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
            │   ├── SettingsScreen.kt      # Settings screen: General, Replies, Limits, Permissions, Security, About
            │   ├── SettingsComponents.kt  # Section/row building blocks (ToggleRow, StepperRow, PermissionRow)
            │   ├── SettingsLogic.kt       # Pure: limit-stepper rules (LimitSteps), reply summary, sample fix
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
            │   ├── ReplySettingsCard.kt   # "Replies" section: expandable Reply contents with live preview
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

## Testing

Four layers, all runnable on the dedicated test emulator (never the one used for manual review):

1. **JVM unit tests** (`app/src/test`): pure logic. `./gradlew testDebugUnitTest`
2. **Instrumented integration tests** (`app/src/androidTest/.../smoke` and `protocol/`): the real `SmsReceiver` driven with synthetic SMS PDUs (`support/SyntheticSms`, multipart for long bodies), replies read back from the emulator's own-number loopback (`support/Loopback`), state seeded and reset with `support/TestState`. `protocol/` is the SMS protocol suite: `CommandMatrixTest`, `SubscribeTest`, `ApprovalGateTest`, `LimitsTest`, `ReplyContentTest`, `LastWithoutFixTest`, `MigrationTest`. A test reads as `Scenario().ready().ask("phonetrack help")` (`support/Scenario`: own number as the sender, `replies(expect)`, `assertSilent()`); `Scenario.rules(mockLocation = true)` grants permissions, resets state and (optionally) fixes the GPS at 37.7749, -122.4194 +-5 m with `support/MockLocationRule` (a test provider; `reportFixes = false` empties every cached fix for the "nothing saved" case).
3. **Compose UI tests** (`app/src/androidTest/.../ui`): the real `MainActivity` under `UiScenario` (`support/UiScenario`: permissions granted, state reset, then `launch { seed }` so the activity starts from exactly that state; dark mode, font scale and location mode are restored afterwards). `RichData` seeds a full screen. Classes: `MainStatusTest`, `DecisionsTest`, `SubscriptionsTest`, `KnownNumbersTest`, `LockTest`, `SettingsTest`, `NavigationTest`, `AccessibilityTest` (labels, 48 dp touch areas in light, dark and 1.3x text) and `ScreenshotTour` (no assertions: writes PNGs to `/sdcard/phonetrack-screens`, which `run-e2e.sh` pulls to `build/e2e-report/screens/` for a person to look through). There are no `testTag`s: nodes are found by text, content description, role and state, as a screen reader would. Seed state before launch (`ResetStateRule` clears prefs first); check results in the stored prefs, not toasts.
4. **Host scenarios** (`scripts/e2e/*.sh`): bash + adb for what an in-app test cannot do: runtime permission revokes (they kill the app process), a real reboot, process kill, wall-clock timing, the real emulator radio (`adb emu sms send`) and GPS (`adb emu geo fix`). Each prints PASS/FAIL lines and exits non-zero on failure; `slow-*.sh` are skipped by `--fast`. `lib.sh` has the helpers (`clean_start`, `revoke`/`grant`, `seed_prefs`, `phase`, `sms_max_id`/`sms_rows_since`, `tap_text`, `notifications`, `location_services`, `home_and_kill`). Scenarios: `perm-no-background-location`, `perm-no-location`, `perm-no-sms`, `perm-no-notifications`, `onboarding`, `real-sms-flow`, `location-services`, `process-death`, `upgrade-from-previous-release` (builds the last tag in a throwaway worktree, installs it, gives it legacy data, installs the current build over it), `slow-location-timeout`, `slow-boot-persistence`, `slow-cadence` (about 14 minutes, API 35 only).

   **Phase tests** (`androidTest/.../phase/`) are the in-app half of a scenario: the script sets the device up (e.g. revokes background location), then runs one class with `am instrument -e phase <name>` (`support/Phase.require`, so a plain `connectedDebugAndroidTest` skips them). They read replies and `support/Notifications` (the owner alerts) in-process and use `UiScenario(grant = false)` for the banner/Fix checks. Scenarios call `launch` (wakes the device, waits for the app window and its title, retries; `LAUNCH_READY=""` for an older release's screen) and use `assert_ui` (polls, and prints the focused window and screen text when it fails); a headless emulator can show a System UI ANR dialog after a reboot, so `hide_error_dialogs` is set. Scenarios need the app launched once first (an app that was force-stopped receives no SMS broadcast), and must take an SMS baseline (`sms_max_id`) because the provider keeps rows between runs; message bodies span several lines in `content query` output.

```bash
cd phonetrack
scripts/test-avd.sh start 35 5556      # create + boot the dedicated headless test AVD (API 26/29/33/35 images installed)
scripts/run-e2e.sh --serial emulator-5556 --no-boot   # unit + instrumented + host scenarios, one summary table
scripts/test-avd.sh stop 5556
```

Notes: debug builds (only) declare `READ_SMS` in `app/src/debug/AndroidManifest.xml` so tests can read the reply loopback in-process (the shell user cannot on API 26-28); the release manifest is unchanged. Every reply to the own number lands twice in the SMS provider (a sent row, type 2, then a looped-back inbox row, garbled on some images), so `Loopback` reads only the sent rows and `Loopback.lastId()` waits for the inbox to go quiet before a test takes its baseline. The own number is port-based on API 26 (`+15555215556` on port 5556) and fixed (`+15551234567`) on newer images; `Loopback.ownNumber` asks the device.

Silence is checked two ways: nothing newer in the loopback inbox, and `last_send_at` unchanged (the app stamps it synchronously on every send, so it also covers numbers the loopback cannot see). Time-based rules (rate windows, 30-day prune) are tested by seeding timestamps.

Known issue (skipped, not hidden): on the API 26 AOSP image outbound SMS throws `SecurityException ... READ_PHONE_STATE` and `SmsSender` swallows it, so no reply is sent; see `support/KnownIssues.kt`.

Not automated, so on the release checklist: whether focus really moves to Block when a pending row is expanded (Compose focus cannot be observed on the headless emulator), and real TalkBack use.

Replies can only be observed for the emulator's own number (`+15551234567`); tests that need "no reply" use a foreign number. Revoking a runtime permission kills the app process, so permission phases are separate runs. `UiAutomation.executeShellCommand` does not interpret quotes: use `support/Shell`, which feeds a real `sh`.

**Release gate.** `scripts/release-gate.sh` (full, about 1 h 45 min) or `--quick` (API 35 only, about 25 min) runs lint, the unit tests, the F-Droid guard, then `run-e2e.sh` on API 35 (everything) and 33, 29 and 26 (fast scenarios), and writes `build/release-gate/<version>-<sha>/report.md`. A skipped test fails the gate unless it is a phase test or is listed in `scripts/gate-baseline/skips-api<N>.txt`. The manual items and the release steps are in `RELEASE_CHECKLIST.md`.

**F-Droid guard.** F-Droid builds the release variant from Google/Maven Central only. `scripts/fdroid-guard.sh` checks the real release APK against committed baselines (`scripts/fdroid-baseline/`: release runtime/compile classpaths, permissions, APK file list) and enforces that there is no `INTERNET` permission and no test class in the dex. Run it after touching `app/build.gradle.kts` or the manifest; a deliberate change is committed with `--update`.

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
