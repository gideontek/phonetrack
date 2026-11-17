# Notification review: design

Branch: `feat/notification-review`. Date: 2026-11-16.

## Goal

Audit every notification PhoneTrack posts, fix the problems found, and add the one
notification that is missing: an alert for a new pending location request.

## Current notifications

| # | Notification | Source | Channel id (importance) | Notification id |
|---|---|---|---|---|
| 1 | Fetching location for SMS reply | `SmsLocationService` | `sms_location` (LOW) | 42 |
| 2 | Location request from <number> (location services off, countdown) | `SmsLocationService` | `sms_location_alert` (HIGH) | 43 |
| 3 | PhoneTrack, N active subscriptions | `SubscriptionService` | `subscription_service` (LOW) | 44 |
| 4 | PhoneTrack can't send location (missing permission) | `HostAlerts` | `host_alerts` (HIGH) | 45 |

## Changes

### 1. Clear the stale permission alert (bug)
Nothing cancels notification 45 once the permission is granted. Add
`HostAlerts.clearLocationPermissionNeeded(ctx)`, which cancels id 45, and call it from:
- the permissions resume hook in `PermissionsState`, when `LocationPermission.canStartLocationService()` is true;
- `SubscriptionService`, after `startForeground` succeeds;
- `SmsLocationService`, after `startForeground` succeeds.

### 2. Tap opens the app
Notifications 1 and 3 get a content intent that opens `MainActivity`. A new
`NotificationSupport.kt` holds the shared open-app `PendingIntent` (same flags as the one now
in `HostAlerts`: `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, `NEW_TASK or CLEAR_TOP`), which
`HostAlerts` also uses. Notification 3 stays ongoing.

### 3. Strings
All titles, bodies, channel names and channel descriptions move to `strings.xml`. Wording is
unchanged, so existing tests that read notification text still pass. Existing channel ids and
importance levels are unchanged: changing either would orphan users' per-channel settings. The
`host_alerts` channel gets a description.

### 4. Icon
Add a monochrome vector drawable `ic_stat_phonetrack.xml` (a location pin) and use it on all
notifications instead of the stock `android.R.drawable` icons. A vector drawable is not an XML
layout, so the Compose-only rule is unaffected.

### 5. Re-post churn
`setOnlyAlertOnce(true)` on notification 3, which is re-posted on every loop tick.

### 6. Lock-screen privacy
Notifications 2, 4 and the new one (7) use `VISIBILITY_PRIVATE` with a public version that
omits the requester's number ("Location request", "PhoneTrack can't send location", "New
location request"). The full text shows once the phone is unlocked.

### 7. New: pending request notification
An unknown number's request is currently added to the pending list silently
(`SmsReceiver`, `ApprovalStore.upsertPending`). The owner only learns of it by opening the app.

- **Channel:** new `pending_requests`, "Pending requests", `IMPORTANCE_DEFAULT`, with a description.
- **Id:** fixed, 46, so there is only ever one such notification.
- **Text:** one pending number: "New location request from <number>. Tap to review". Several: "N numbers waiting for approval". N is the count of PENDING entries in `ApprovalStore`.
- **Tap:** opens `MainActivity` on the Main screen, where the Decisions section lists pending numbers.
- **When it fires:** only when `upsertPending` adds a new PENDING entry. Repeat attempts from a number that is already pending, and attempts the `newpending` rate limit rejects (`SmsLimits.NEW_PENDING_PER_HOUR`), do not post. `upsertPending` must report whether it added an entry (or the receiver compares the pending set before and after); it must not post a notification for a refresh of `lastSeen`.
- **When it updates or clears:** after each Approve or Block, and on app resume, it is re-posted with the current count, or cancelled when no PENDING entries remain. Pruned or evicted pending entries are reflected the next time it is refreshed.
- **Permission:** requires `POST_NOTIFICATIONS` on Android 13+, checked the same way `HostAlerts` does. If denied, nothing is posted and the in-app list is the only signal.
- **Where it lives:** a new `PendingAlerts` object next to `HostAlerts` (`PendingAlerts.update(ctx)`), so the notification logic stays out of `SmsReceiver` and the approval store.

## Not changing

- No new permissions, no change to when notifications 1 to 4 fire, no change to channel ids or importance.
- Owners who deny `POST_NOTIFICATIONS` still get only the in-app Fix banner (already covered by `NoNotificationsPhaseTest` and the FAQ).

## Constraints

No internet permission, no third-party libraries, no databases or files, SharedPreferences
only, no WorkManager, no XML layouts. The pending count is read from the existing approvals
list; nothing new is persisted.

## Testing

- JVM unit test for the count-to-text choice (one number vs. N), if it lands in a pure helper.
- Instrumented/phase checks (`support/Notifications`): the permission alert is gone after the permission is granted; tapping the subscription notification opens `MainActivity`; an unknown sender posts one pending notification, a repeat from that number posts nothing new, a second distinct sender updates it to "2 numbers"; approving or blocking all pending numbers clears it; with `POST_NOTIFICATIONS` revoked, nothing is posted.
- Existing notification tests keep passing unchanged (text is identical).
- Gate: `./gradlew lint` with zero errors, unit tests, and `scripts/fdroid-guard.sh` (a drawable and strings are added, no permission or dependency changes). Notification-related emulator tests run on the dedicated test AVD only.
- `FaqScreen` gets one line saying the owner is notified of new requests waiting for approval.

## Docs to update

`CLAUDE.md` project layout: add `NotificationSupport.kt` and `PendingAlerts.kt`.
