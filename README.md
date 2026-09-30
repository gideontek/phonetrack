# PhoneTrack SMS

**Location sharing over SMS — no internet required.**

PhoneTrack SMS turns your Android phone into an SMS location beacon. Anyone who knows your keyword can send a text to request your location, and the phone replies automatically with coordinates, accuracy, battery level and a map link (configurable) — all over plain SMS, with no data connection needed.

---

## Why Phone Track?

Most location-sharing apps require both parties to have internet, accounts, and the same app installed. PhoneTrack has no such dependencies. Track any phone even without a data connection. It works anywhere your phone can send and receive a text message.

**Common use cases:**

- Checking in on a family member in a low-coverage area
- Parents keeping a safety line open with kids who don't always have data
- Hikers or travellers sending a "where am I?" update to someone at home
- Roadside assistance — share your exact location without fumbling with maps
- Off-grid check-ins where data is expensive or unavailable

---

## How it works

1. Install PhoneTrack on the phone you want to track.
2. Enable the app and grant the required permissions.
3. From any other phone, send an SMS with the keyword (default: `phonetrack`).
4. PhoneTrack replies automatically with your location — no user interaction needed.

The tracked phone never pushes location unsolicited. It only responds to inbound requests, and every sender goes through an approval gate that you control.

---

## Privacy and consent

PhoneTrack is **pull-based**: the tracked phone decides who gets a response. Though within the app a user can one-shot **push** their current location to an approved contact.

- New senders are logged as **PENDING** and silently ignored until you explicitly approve them.
- You can mark any number as **APPROVED** (always responds) or **BLOCKED** (always ignored) from within the app.
- The app only responds when you have it enabled. You can disable it instantly from the main screen.

---

## Requirements

- Android 8.0 (API 26) or later
- A SIM card with SMS capability
- Location permission set to **Allow all the time** (background location). Without it Android won't let PhoneTrack start its location service when a text arrives, so it can't send a fix: the requester just gets a "Location permission not granted" reply, and the phone shows a notification that opens PhoneTrack so you can change it. A subscription that was accepted in the meantime is kept and starts sending as soon as you do
- **RCS chats turned off** between PhoneTrack's phone and anyone sending it commands (see below)

RCS messages are invisible to PhoneTrack — and to every other third-party app. Android only delivers RCS content to the device's default messaging app (almost always Google Messages); there's no broadcast or API for other apps to observe it, by design. If RCS is active for a conversation, commands sent from that number will silently never arrive. To fix it, turn off RCS chats for the relevant conversation (or globally): in Google Messages, go to **Settings → RCS chats** and turn off **Turn on RCS chats**. This forces that conversation back to plain SMS, which PhoneTrack can see.

---

## Installation

PhoneTrack will be available on [F-Droid](https://f-droid.org/). Until then, see [Building from source](#building-from-source) below.

---

## Setup

1. Open PhoneTrack and tap **Grant permissions** to allow SMS and location access.
2. Toggle **SMS responding** on.
3. Optionally change the **keyword** (default: `phonetrack`) to something private.

PhoneTrack remembers whether SMS responding is on or off, and picks up where it left off after the phone restarts. Active subscriptions resume automatically.

---

## SMS command reference

All commands start with your keyword (shown here as `phonetrack`). Commands and options are case-insensitive. A phone keyboard that turns `--` into a long dash (`—`) is fine.

| Command | What it does |
|---------|--------------|
| `phonetrack` | Get a fresh location fix |
| `phonetrack last` | Get the last location the phone already has, without waking the GPS |
| `phonetrack subscribe [options]` | Start periodic updates |
| `phonetrack unsubscribe` | Stop periodic updates |
| `phonetrack help` | List the commands |

Any other word after the keyword (for example `phonetrack hello`) gets the help reply, not a location. Messages longer than 320 characters are ignored.

### One-shot location request

```
phonetrack
```

The phone acquires a GPS fix and replies with what you've chosen under **Reply contents** in the app. By default that is one SMS with the coordinates, accuracy, battery level and an OpenStreetMap link (which opens the location in any browser):

```
[PhoneTrack] Lat: 51.5074, Lon: -0.1278
Acc: 8m, Bat: 73%
https://www.openstreetmap.org/?mlat=51.5074&mlon=-0.1278#map=12/51.5074/-0.1278
```

You can switch any of these parts on or off (at least one must stay on; the time of fix and the `geo:` link are off by default):

| Part | Looks like |
|------|------------|
| Coordinates | `Lat: 51.5074, Lon: -0.1278` |
| Accuracy | `Acc: 8m` (GPS accuracy radius in metres) |
| Battery | `Bat: 73%`, with `(charging)` while charging |
| Time of fix | `Time: 14:32Z` (UTC) |
| `geo:` link | `geo:51.5074,-0.1278`, which opens in any maps app |
| OpenStreetMap link | as above |

With everything on, a reply looks like this (two SMS, because the text and the map link fit together in one):

```
[PhoneTrack] Lat: 51.5074, Lon: -0.1278
Acc: 8m, Bat: 73%, Time: 14:32Z
https://www.openstreetmap.org/?mlat=51.5074&mlon=-0.1278#map=12/51.5074/-0.1278

geo:51.5074,-0.1278
```

The text and the map link share one SMS whenever they fit (160 plain characters); otherwise the link goes in its own message. The `geo:` link is always its own message. The in-app preview shows exactly what will be sent. The settings are locked along with the rest when a PIN is set.

If location services are turned off when the request arrives, the phone posts a high-priority notification with a 60-second countdown. If you re-enable location services within that window, the fix is sent automatically.

### Last known location

```
phonetrack last
```

Replies immediately with the newest location the phone already has cached, using the same Reply contents settings as a one-shot request (battery and time of fix are left out), headed with how old the fix is. By default:

```
[PhoneTrack] Last known (12m ago)
Lat: 51.5074, Lon: -0.1278
Acc: 8m
https://www.openstreetmap.org/?mlat=51.5074&mlon=-0.1278#map=12/51.5074/-0.1278
```

This does not turn the GPS on, so it works when a fresh fix can't be obtained (for example indoors), but the position may be stale. If nothing is cached the phone says so.

### Subscribe (periodic updates)

```
phonetrack subscribe [--dist N] [--freq N] [--time N]
```

Starts a recurring location subscription. The phone confirms the settings, sends an immediate fix, then continues sending updates on a schedule until the subscription expires or you cancel it.

| Option | Default | Allowed | Meaning |
|--------|---------|---------|---------|
| `--dist N` | 200 m | 0–50000 | Only send an update if you have moved at least N metres since the last one (0 = always) |
| `--freq N` | 15 min | 1–1440 | Send an update at most every N minutes |
| `--time N` | 4 h | 1–168 | Cancel the subscription automatically after N hours |

**Examples:**

```
phonetrack subscribe
```
Updates every 15 minutes for 4 hours, skipped if you haven't moved 200 m.

```
phonetrack subscribe --freq 5 --time 1
```
Updates every 5 minutes for 1 hour.

```
phonetrack subscribe --dist 0 --freq 10 --time 8
```
Updates every 10 minutes for 8 hours regardless of movement.

The confirmation looks like:

```
[PhoneTrack] Subscribed: update every 15 min, only if moved 200m+, for 4h (ends Sep 30 18:32Z). Text "phonetrack unsubscribe" to stop.
```

Periodic updates use the same Reply contents settings (battery is included if you switch it on) and add a direction arrow and how far you moved since the previous update, e.g. `⇗120m`. The end time is in UTC. If an option is unknown, repeated, not a whole number, or outside the allowed range, nothing is subscribed and the phone replies with what was wrong plus the usage line, for example `--freq must be 1-1440 (minutes)`. Values are never silently adjusted.

### Unsubscribe

```
phonetrack unsubscribe
```

Cancels your active subscription. The phone replies to confirm cancellation, or tells you there was nothing to cancel.

---

## App settings

| Setting | Description |
|---------|-------------|
| SMS responding | Master on/off switch |
| Keyword | The trigger word the phone listens for (default: `phonetrack`). One word, no spaces, and it can't start with `[` |
| Contacts list | Per-number approval state: PENDING / APPROVED / BLOCKED |

Active subscriptions are shown in the main screen and can be cancelled by swiping them away.

### Limits

PhoneTrack protects itself, and your SMS bill, against floods:

| Limit | Default | What happens past it |
|-------|---------|----------------------|
| Commands per approved sender | 20 per hour | The first extra message gets "Too many requests"; the rest are ignored until the hour is up. Every command counts, `help` included. |
| Concurrent subscriptions | 10 | A new subscriber is told "Too many active subscriptions". Re-subscribing replaces your own and is always allowed. |
| New unknown numbers recorded | 10 per hour | Extras are ignored silently. |
| Pending list | 50 numbers | The oldest pending number is dropped to make room (it simply texts again). Approved and blocked numbers are never dropped. |
| Stale pending numbers | 30 days | Pending numbers that haven't contacted you for a month are removed. |

The first two can be changed only through the `max_subscriptions` and `rate_limit_per_hour` preferences; there is no setting for them in the app yet.

### Security notes

- **SMS is not private.** Messages travel in the clear and are stored by the carrier and in each phone's messages. Approval is by the sender's phone number, which a determined attacker can spoof, so treat an approved number as trusted, not authenticated.
- **The PIN guards only the settings screen** (switch, keyword, approvals, cancelling subscriptions). It does not protect the SMS commands.
- **The PIN is stored hashed** (PBKDF2-HMAC-SHA256 with a random salt), never in plain text. Five wrong PINs in a row lock unlocking for 1 minute, then 5, 15 and 60 minutes for each further round of five; a correct PIN resets it. The lockout counters survive closing the app. A lockout uses the phone's clock, so someone who can change the clock could skip one.
- **A short PIN is still weak against someone who can copy the app's files.** A 4-digit PIN has only 10,000 possibilities, so a copied hash could be brute-forced offline in minutes. That is why the app also opts out of Android backups (below); choose a longer PIN if this matters to you.
- **No backups.** PhoneTrack opts out of Android's cloud and adb backups, so the PIN hash, your approvals and who has contacted you never leave the phone that way. The trade-off: on a new phone you set up PhoneTrack again and re-approve your numbers.
- If you forget the PIN there is no reset; clearing the app's data (which also clears approvals and subscriptions) removes it.

---

## Building from source

```bash
git clone https://github.com/gideontek/phonetrack.git
cd phonetrack/phonetrack
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

**Requirements:**

- JDK 17+
- Android SDK with platform `android-35` installed
- Set `sdk.dir` in `local.properties` or export `ANDROID_HOME`

Run lint before submitting changes:

```bash
./gradlew lint   # must report zero errors
```

---

## Contributing

Bug reports and pull requests are welcome. Please keep changes consistent with the [design constraints](CLAUDE.md#design-constraints) in `CLAUDE.md` — in particular, no internet permission, no third-party libraries, and no WorkManager for the location loop.

---

## License

GPL-3.0 — see [`LICENSE`](LICENSE).
