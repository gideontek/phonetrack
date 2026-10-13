# Event log: design

Date: 2026-10-11 · Branch: `feat/event-log`

Amended after implementation to match what was built (see the plan's deviations); where this text
and the code differ, the code is right.

## Problem

Subscription updates are sometimes late or never sent, with no visible reason. Several paths
drop or delay an update silently today:

- `SubscriptionService.tick()` skips a due subscriber when movement is under its `dist` threshold.
- The tick is skipped while the SMS listener is off.
- A location failure (no permission, provider off, 60 s timeout) retries after 5 minutes; nobody is told.
- `SmsSender.sendRaw` swallows `RuntimeException` and only calls `Log.w`.
- If Android kills the service, the sticky restart leaves no trace of the gap.
- A `startForeground` `SecurityException` stops the service quietly.

## Goals

- An opt-in, short-lived log of events that explains delays and surfaces bugs.
- Viewable in Settings, copyable into an issue report.
- Private by construction: the log can be shared, so it never contains phone numbers,
  coordinates, message text or the keyword.

## Non-goals (v1)

Inbound-gate drop reasons (keyword mismatch, blocked, pending, rate-limited, etc.), SMS
sent/delivery-report callbacks, a share sheet, export to a file.

## Constraints (from CLAUDE.md)

No new dependencies, no INTERNET, no files or databases (SharedPreferences only), no XML
layouts. No manifest changes.

## Behavior

- Toggle "Event log" in Settings, off by default. Turning it on records an `ENV` snapshot
  first. Turning it off clears the log and the alias map (confirm if the log is non-empty).
- Retention: at most 300 entries and 48 hours. Oldest entries are dropped first; entries
  older than 48 h are pruned on every write and on view.
- Verbosity: one summary `TICK` line per tick. Notable events get their own lines.

## Entry shape

Each entry: `t` (epoch ms), `e` (event code), plus optional typed fields. There is no
free-text field.

| Field | Type | Used by |
|---|---|---|
| `who` | alias (`Contact-A`) | per-subscriber events |
| `n`, `due` | int | TICK (subscriptions, due) |
| `lateS` | int seconds | TICK (actual fire time minus scheduled time) |
| `reason` | enum | skips, fix failures, send failures, service start/stop, rejections |
| `fixS` | int seconds | fix latency |
| `acc` | bucket | `<10m`, `10-50m`, `50-100m`, `100-200m`, `200-500m`, `0.5-1km`, `1-5km`, `>5km` |
| `moved` (bucket), `needM` (exact int metres, the subscription's own distance setting; an outcome's kind is stored under key `k`) | a `not-moved` outcome inside a TICK |
| `parts` | int | SEND_FAIL |
| `freqMin`, `distM`, `hours` | int | SUBSCRIBED, REPLACED (the subscription's settings; configuration, not location data, so exact values are fine) |
| `err` | exception class name | SEND_FAIL |

Event codes:

- Tick and service: `TICK`, `SERVICE_START`, `SERVICE_STOP`, `BOOT_RESUME`
- Subscriptions: `SUBSCRIBED`, `REPLACED`, `EXPIRED`, `CANCELLED`, `SUBSCRIBE_REJECTED`
- Location: `FIX_FAIL` (a successful fix is not an event; its latency and accuracy are TICK fields)
- Send: `SEND_FAIL`
- Environment: `ENV`

A due subscriber's result in a tick is an outcome (`sent`, `not-moved`, `listener-off`) stored as a
field of that `TICK` entry, not as an event of its own.

In the rendered text, per-subscriber results of a tick (sent, skipped, fix result) are folded
into that tick's line, e.g.
`tick (late 2s) · 3 subs, 1 due -> Contact-A sent (fix 4s, <10m)`. `sent` means the update was handed to `SmsSender`; a failed hand-off adds a `SEND_FAIL` line.
Outcomes are part of that line, not separate lines. Separate lines are for
service start/stop, boot resume, subscribe/expiry/cancel/replace, subscribe rejection, `FIX_FAIL`,
`SEND_FAIL` and `ENV`.

Reasons:

- Fix failure: `no-permission`, `provider-off`, `timeout`
- Service start: `requested` (any start request: subscribe, boot, app resume), `sticky-restart` (null intent). `onStartCommand` runs on every `startForegroundService`, so the start is logged once per service instance; a boot start is visible from the `BOOT_RESUME` line before it
- Service stop: `no-subs`, `startForeground-denied`
- Boot resume failure: `cannot-start` (the service could not be started from the boot receiver)
- Rejection: `limit`, `invalid-flags`

## Privacy rules

1. The record API accepts only the typed fields above. It never takes a phone number, a
   `Location` or a message string.
2. Senders are shown as aliases (`Contact-A`, `Contact-B`, ...; a one-shot requester is not a subscriber) assigned in order of first
   appearance. The number-to-alias map is stored in prefs (`event_log_aliases`), is never part
   of the copied text, and is cleared with the log. Aliases are stable for the log's life and
   never reused for a different sender. (The numbers already live in `subscriptions_list` and
   `approvals_list`, so this adds no exposure.)
3. Movement and accuracy are bucketed (the finer bucket list above), never exact. The one exact distance is `needM`, the subscription's configured threshold, which is configuration, not location data.
4. Exceptions record the class name only, never the message.
5. The copied text starts with a header: privacy note, app version, Android API level,
   retention window, log start and end time.

## Architecture

Follows the existing pure-logic + prefs-store pattern (`RateLimiter`/`RateStore`).

- `EventLogEntry.kt`: the typed entry, event-code and reason enums, bucket enums.
- `EventLogLogic.kt` (pure): append with cap and age prune, alias assignment, bucketing,
  rendering (header and lines), and the per-tick accumulator (`TickReport`).
- `EventLogStore.kt`: JSON array in prefs key `event_log`, alias map in `event_log_aliases`,
  toggle in `event_log_enabled`. `synchronized` read-modify-write.
- `EventLog.kt`: the facade call sites use, `EventLog.record(ctx, ...)`. Returns immediately
  (one prefs boolean read) when the toggle is off. Never throws.
- `EventLogScreen.kt`: the viewer.

Prefs keys added: `event_log_enabled` (Boolean, absent = off), `event_log` (JSON array),
`event_log_aliases` (JSON object). No `prefs_schema_version` bump.

## Where events are recorded

**`SubscriptionService`**

- `scheduleTick` stores the expected fire time; at tick start `lateS = now - expected`.
- A `TickReport` accumulates subs, due count, per-subscriber outcome and the fix result, and
  is written once at the point the next tick is scheduled, so every exit path produces
  exactly one `TICK` line.
- `fetchLocation`'s `onFailure()` gains a reason (`no-permission`, `provider-off`,
  `timeout`).
- `EXPIRED` per pruned subscription. `SERVICE_START`/`SERVICE_STOP` with reasons.
- An OS kill can't be logged when it happens; it appears as `SERVICE_START (sticky-restart)`
  after a gap in ticks.

**`SmsReceiver`**: `SUBSCRIBE_REJECTED` (`limit`, `invalid-flags`); `SUBSCRIBED` when a new
subscription is created and `REPLACED` when `SubscriptionManager.add` overwrites an existing
entry for the sender (checked against the list before the add), both with `freqMin`, `distM`
and `hours` so a reader can tell "late" from "working as configured"; `CANCELLED` on
unsubscribe.

**`SmsSender.sendRaw`**: `SEND_FAIL` with exception class and parts count. The recipient is
passed only to look up the alias.

**`BootReceiver`**: `BOOT_RESUME` with the number restored, or the reason it couldn't.

**`ENV`**: booleans only (fine / background location, notifications, location services,
battery-optimization exemption) plus API level, app version, and device manufacturer and
model (OEM battery savers differ a lot; these identify a product line, not a person). Written when the toggle goes
on, at each `SERVICE_START`, and on a tick when it differs from the last snapshot (checked in memory). There is no
app-resume hook. `EventLog.clear` re-snapshots the environment, so Clear leaves a fresh `ENV` line.

**Service lifecycle details**: a service destroyed mid-fetch still writes its `TICK`; a start request
(`onStartCommand`) mid-fetch leaves the pending fetch alone; its `TICK` is written when the fix
arrives or times out.

**Write durability**: routine `TICK` uses `apply()`; `SERVICE_STOP`, `SEND_FAIL` and
`FIX_FAIL` use `commit()` so they survive a kill right after.

## Settings UI

- `Screen` gains `EventLog`. Back from EventLog returns to Settings, from Settings to Main
  (the `BackHandler` uses a parent-of rule).
- New "Diagnostics" section between Security and About: `ToggleRow` "Event log" (supporting
  text: no phone numbers or locations, kept 48 hours) and a "View event log" row (entry count
  as supporting text), shown only while the log is on.
- Viewer: top bar with Back, Copy, Clear. Monospace text, newest first, wrapped (no sideways
  scroll), one scrollable region; the text is selectable. The header block is part of what Copy puts on the
  clipboard. Empty state "No events yet"; off state "Event log is off." as plain text (no Enable button).
  Copy shows brief feedback except where Android 13+ already shows its own. Clear asks for
  confirmation and empties the alias map.
- PIN: the existing Settings pattern applies, controls are disabled while locked (not
  `pinDialogs.guard`). The viewer shows "Unlock to view the event log." with an Unlock button
  while locked, which also covers a restored process.
- Accessibility: 48 dp touch areas, content descriptions "Copy event log" and "Clear event
  log". UI strings are inline like the rest of the UI (no `strings.xml`); the log's own line text is rendered by
  `EventLogLogic`, in English.

## Error handling

- `EventLog.record()` wraps its whole body in try/catch; logging must never stop an update
  from being sent.
- Corrupt `event_log` JSON is reset to empty.
- A failed prefs write drops the event silently.

## Testing

1. **JVM unit** (`EventLogLogic`): cap and age prune; alias stability and no reuse; bucket
   boundaries; golden rendering of every event code; a privacy test that pushes known numbers
   and coordinates through a full tick and asserts neither the rendered text nor the JSON
   contains them; `TickReport` produces exactly one `TICK` per exit path.
2. **Instrumented** (`EventLogTest`, in the style of `protocol/`): off records nothing; on
   records a sent tick and a not-moved skip with the mock fix; `SUBSCRIBED` carries the
   requested settings and a second subscribe from the same sender logs `REPLACED`; toggling
   off clears log and alias map; `SUBSCRIBE_REJECTED` at the limit; `EventLogStore` JSON round-trip (org.json is
   stubbed in JVM tests; confirm against how existing store tests handle it).
3. **Compose UI**: toggle and view row; empty and off states; Copy puts header and entries on
   the clipboard; Clear needs confirmation; the viewer locked while a PIN is set; 48 dp touch areas at 1.0x and 1.3x text (in
   `EventLogUiTest`, not `AccessibilityTest`); the new screen in `ScreenshotTour`.
4. **Host scenario** `scripts/e2e/slow-event-log.sh`: a real tick is logged; location services
   off then `provider-off`; a killed process then `sticky-restart`; no phone number in the stored
   log. (Revoking location kills the process and is covered by `perm-no-location`; the 60 s
   `timeout` reason by `slow-location-timeout`.)

## Housekeeping

- Run `scripts/fdroid-guard.sh` (no build or manifest change is expected; it is cheap
  insurance), `./gradlew lint` (zero errors) and the unit tests.
- Update `CLAUDE.md` (file layout, prefs keys) and add one manual item to
  `RELEASE_CHECKLIST.md`: read a real copied log once for anything identifying.
- Commits: logic and tests first, then the call sites, then the UI.
