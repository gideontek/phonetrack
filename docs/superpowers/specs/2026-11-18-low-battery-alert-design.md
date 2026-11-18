# Low-battery alert: design

Branch: `feat/low-battery-alert`. Date: 2026-11-18. Answers issue #7 (send location on low battery).

## Goal

While someone is subscribed to the owner's location, send them one location message when the
phone's battery runs low, so a person searching for the owner has at least a last position.
It is a safety net on top of subscriptions: no new command, flag, registration or stored list.

## Behaviour

- **Who gets it:** every active (non-expired) subscriber. Nobody else, and nothing when there is no subscription.
- **When:** the battery level reaches the threshold or lower while the phone is not charging. Once per crossing.
- **Re-arming:** the alert re-arms when the level rises above the threshold, or when charging starts.
- **Start state:** when the service starts, the first battery reading only sets the armed state (armed if the level is above the threshold). A phone that is already low when a subscription begins does not alert, because the first update already shows the battery level. A process restart while low therefore does not send a second alert.
- **Listener off:** nothing is sent while the SMS listener is switched off, same as the tick loop. Expiry and everything else are unchanged.
- **Unknown level:** a reading outside 0..100 is ignored.

## Setting

One pref, `low_battery_alert_pct` (Int, default 5, 0 = off, coerced to 0..20). On the Settings
screen, General section, a stepper row "Low-battery alert": value "Off" or "5%", supporting text
"Text people tracking you once when the battery runs low". Default 5 because a fix and an SMS
need some charge left. "Restore default settings" puts it back to 5. The key is read by the
service at crossing time, so changing it applies without restarting anything.

## Message

Same contents as the owner chose in Reply contents (coordinates, accuracy, time, geo link, map link), with a
header line instead of the battery part:

- fresh fix: `[PhoneTrack] Battery low: 4%` then the location lines and links
- cached fix: `[PhoneTrack] Battery low: 4%. Last known (12m ago)` then the same
- no location at all: `[PhoneTrack] Battery low: 4%. No location available.`

Built by `SmsComposer.composeLowBattery`, pure like the other builders. It goes out as one SMS when it fits, using the existing
`assemble` layout.

## Components

| Unit | Change |
|---|---|
| `LowBatteryLogic.kt` (new, pure) | `step(armed: Boolean?, percent, charging, threshold)` returns the new armed state and whether to alert. `percent` outside 0..100 and `threshold` 0 never alert. |
| `DeviceStatus.kt` | `batteryFrom(intent)`: percent and charging read from an `ACTION_BATTERY_CHANGED` intent (the service already has the intent, and no extra lookup is needed). |
| `SmsLimits.kt` | `DEFAULT_LOW_BATTERY_PCT = 5`, `MAX_LOW_BATTERY_PCT = 20`, `coerceLowBattery`. |
| `SmsComposer.kt` / `SmsSender.kt` | `composeLowBattery` and `sendLowBattery`. |
| `SubscriptionService.kt` | Registers an `ACTION_BATTERY_CHANGED` receiver (not exported) in `onCreate`, unregisters in `onDestroy`. Android 8+ does not deliver this broadcast to a manifest receiver, so the already-running foreground service is the right place. |
| `HomeViewModel.kt`, `SettingsScreen.kt`, `SettingsLogic.kt` | The setting: flow, pref listener, stepper rules, reset. |
| `FaqScreen.kt`, README | One FAQ entry: what it does, only while subscribed, cannot help if the phone dies before the SMS goes out. |

## Sending an alert

1. The receiver feeds each reading to `LowBatteryLogic.step`. On `alert`: if the listener is off, or there are no active subscribers, stop.
2. If no location fetch is running, start one (the existing `fetchLocation`, 60 s timeout). If one is already running (a tick), mark the alert pending and let that fetch's callback send it: with its fix on success, with the cached fix on failure.
3. A failed or timed-out fetch falls back to `LastKnownLocation` (needs fine location, otherwise none), then to the "no location available" text.
4. Send to each active subscriber. After a fresh fix, update each one's tracking position and time so the next regular update does not repeat it.

## Errors and limits

- The alert is not rate limited: it fires once per crossing and goes only to numbers the owner already approved.
- If the phone dies before the SMS goes out, nothing is sent. The FAQ says so.
- No new permission, no library, no new storage beyond one Int pref. `INTERNET` stays absent.

## Testing

- **JVM unit:** `LowBatteryLogicTest` (crossing, once only, re-arm by rising and by charging, start state low and high, unknown level, threshold 0, threshold change), `SmsComposerTest` additions (three message shapes, fits one SMS), stepper rules, `SmsLimits` coercion.
- **Compose UI:** `SettingsTest` shows the row, steps it, and the value is stored; "Restore default settings" resets it.
- **Host scenario:** `scripts/e2e/low-battery-alert.sh`: subscribe the emulator's own number, `dumpsys battery unplug` and set the level to 50, then to 4; expect exactly one alert; set it back to 50 and to 4 again, expect a second; with the setting at 0, expect none.
- **FAQ:** `FaqTest` counts updated for the new entry.
