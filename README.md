# PhoneTrack SMS

**Location sharing over SMS — no internet, no account, no data. GPS phone tracking with plain text messages.**

PhoneTrack SMS turns an Android phone into an SMS location beacon. A contact you have approved texts your keyword and the phone replies with its coordinates, accuracy, battery level and a map link (all configurable). It works wherever the phone can send and receive a text.

---

## Why Phone Track?

Most location-sharing apps require both parties to have internet, accounts, and the same app installed. PhoneTrack has no such dependencies. Locate a phone even without a data connection. The phone being tracked simply needs to approve the contact number once.

**Common use cases:**

- Checking in on a family member in a low-coverage area
- Parents keeping a safety line open with kids who don't always have data
- Hikers or travellers sending a "where am I?" update to someone at home
- Roadside assistance — share your exact location without fumbling with maps
- Off-grid check-ins where data is expensive or unavailable
- Parents setting up a child's phone (see [Locking the app with a PIN](#locking-the-app-with-a-pin))

---

## Quick start

On the phone to be tracked:

1. Install PhoneTrack, tap the gear (**Settings**) and grant the permissions.
2. Turn **SMS listening** on (on the main screen or in Settings). Optionally change the keyword (default `phonetrack`) to something private.
3. Optionally set a PIN (see [Locking the app with a PIN](#locking-the-app-with-a-pin)).

From any other phone:

4. Text the keyword. The first time, the number appears under **Needs your decision** on the tracked phone, and nothing is sent back until its owner taps **Approve**. After that, every text gets a reply automatically.

PhoneTrack remembers whether SMS responding is on or off and picks up where it left off after the phone restarts. Active subscriptions resume automatically.

---

## Privacy and consent

PhoneTrack is **pull-based**: the tracked phone decides who gets a response, and it never sends its location unprompted. (From the app you can also send your current location to an approved contact yourself.)

- A number that texts your keyword for the first time **waits for your decision** (under **Needs your decision** in the app) and gets no reply until you approve it.
- **Approved** numbers always get a reply; **blocked** numbers are always ignored. You can change either from within the app.
- The app only responds when you have it enabled. You can disable it instantly from the main screen.

---

## Requirements

- Android 8.1 (API 27) or later, and a SIM that can send and receive SMS.
- Location permission set to **Allow all the time**, so a text can start a reply while the app is closed. Without it the requester gets "Location permission not granted" and you get a notification to fix it. A subscription accepted in the meantime is kept and starts once you do.
- **RCS chats turned off** for the tracked phone's conversations with anyone who sends it commands. Android delivers RCS only to the default messaging app, so PhoneTrack never sees those texts and the request silently never arrives. In Google Messages, go to **Settings → RCS chats** and turn off **Turn on RCS chats** (or turn RCS off for that one conversation) to fall back to plain SMS.

---

## Installation

PhoneTrack SMS is available on [F-Droid](https://f-droid.org/en/packages/com.gideontek.phonetrack/). You can also build it yourself: see [Building from source](#building-from-source) below.

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

Open **Settings** from the gear icon on the main screen; Back returns to the main screen.

| Setting | Description |
|---------|-------------|
| SMS listening | Master on/off switch (also on the main screen) |
| Keyword | The trigger word the phone listens for (default: `phonetrack`). One word, no spaces, and it can't start with `[` |
| Reply contents | What a location reply contains, with a live preview (see below) |
| Limits | Commands per number per hour, and most active subscriptions at once |
| Permissions | SMS, location, background location and notifications, each with a Grant button |
| PIN | Set, change or remove the PIN that guards Settings and approval changes (see below) |

### The main screen

- **Status:** whether the phone is listening, what it is doing (Idle / Sending / Receiving / Active), and a red **Fix** banner when something stops replies from working (for example background location is off).
- **Needs your decision:** numbers that have texted your keyword and are waiting. Tap one to show **Approve** and **Block**; nothing is sent to them either way until you approve.
- **Active subscriptions:** who is receiving periodic updates, how long is left, with **Send now** and **Cancel**.
- **Approved and blocked numbers:** collapsed by default; change a number's state or send it your location.

### Locking the app with a PIN

An optional PIN guards the app's settings and approvals: opening Settings, the SMS listening switch, and Approve and Block. It does two jobs:

- **Everyday security.** Anyone else who picks up the phone can't switch PhoneTrack off, change the keyword, or approve their own number.
- **Setting up a child's phone.** Approve your own number, set a PIN and keep it. The app keeps answering you, and your child can't turn it off or change who can locate the phone. Tell them it is there: PhoneTrack replies only to numbers you approved, and it is meant for consent, not secret monitoring.

When locked, tapping a guarded control asks for the PIN and then carries on with what you tapped. The app starts locked each time it is opened from scratch; tap the lock icon to lock it again after you have used it. Sending your location to an approved number and cancelling a subscription never need the PIN, and the PIN does not affect SMS commands. It does not stop someone from uninstalling the app or revoking its permissions in Android's own settings.

### Limits

PhoneTrack protects itself, and your SMS bill, against floods:

| Limit | Default | What happens past it |
|-------|---------|----------------------|
| Commands per approved sender | 20 per hour | The first extra message gets "Too many requests"; the rest are ignored until the hour is up. Every command counts, `help` included. |
| Concurrent subscriptions | 10 | A new subscriber is told "Too many active subscriptions". Re-subscribing replaces your own and is always allowed. |
| New unknown numbers recorded | 10 per hour | Extras are ignored silently. |
| Pending list | 50 numbers | The oldest pending number is dropped to make room (it simply texts again). Approved and blocked numbers are never dropped. |
| Stale pending numbers | 30 days | Pending numbers that haven't contacted you for a month are removed. |

The first two are set in **Settings → Limits** (commands per number 1–100 per hour, active subscriptions 1–20). Lowering the subscription limit never cancels running subscriptions; it only refuses new ones.

### Security notes

- **SMS is not private, and a sender's number can be faked.** Messages travel unencrypted and are stored by the carrier and on each phone. PhoneTrack decides who to answer by the sender's phone number alone, and a determined attacker can send a text that appears to come from an approved number. Approve only people you trust, and don't treat a request from an approved number as proof of who sent it.
- **PIN protection.** The PIN is stored hashed (PBKDF2-HMAC-SHA256 with a random salt). Five wrong PINs in a row lock unlocking for 1 minute, then 5, 15 and 60 for each further round; a correct PIN resets it. A 4-digit PIN is weak against someone who can copy the app's files, so choose a longer one if that matters.
- **No backups.** PhoneTrack opts out of Android's cloud and adb backups, so the PIN hash and your approvals never leave the phone that way. On a new phone you set up and re-approve again.
- There is no PIN reset. Clearing the app's data removes it, along with approvals and subscriptions.

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

### Testing and releasing

Unit tests run with `./gradlew testDebugUnitTest`. The full test suite (instrumented, UI and host scenarios on an emulator) is described in [CLAUDE.md](CLAUDE.md#testing); `phonetrack/scripts/release-gate.sh` runs all of it and prints one verdict, and [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) lists the manual checks and the release steps.

---

## Contributing

Bug reports and pull requests are welcome. Please keep changes consistent with the [design constraints](CLAUDE.md#design-constraints) in `CLAUDE.md` — in particular, no internet permission, no third-party libraries, and no WorkManager for the location loop.

---

## License

GPL-3.0 — see [`LICENSE`](LICENSE).
