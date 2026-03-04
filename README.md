# PhoneTrack

**Location sharing over SMS — no internet required.**

PhoneTrack turns your Android phone into an SMS location beacon. Anyone who knows your keyword can send a text to request your location, and the phone replies automatically with coordinates, accuracy, battery level, and a map link — all over plain SMS, with no data connection needed.

---

## Why PhoneTrack?

Most location-sharing apps require both parties to have internet, accounts, and the same app installed. PhoneTrack has no such dependencies. It works anywhere your phone can send and receive a text message.

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

- New senders are logged as **DEFAULT**. If *Block all* is off (the default), they receive a reply. If *Block all* is on, they are silently ignored until you explicitly approve them.
- You can mark any number as **APPROVED** (always responds) or **BLOCKED** (always ignored) from within the app.
- The app only responds when you have it enabled. You can disable it instantly from the main screen.

---

## Requirements

- Android 8.0 (API 26) or later
- A SIM card with SMS capability
- Location permission (including background location for subscriptions)

---

## Installation

PhoneTrack will be available on [F-Droid](https://f-droid.org/). Until then, see [Building from source](#building-from-source) below.

---

## Setup

1. Open PhoneTrack and tap **Grant permissions** to allow SMS and location access.
2. Toggle **SMS responding** on.
3. Optionally change the **keyword** (default: `phonetrack`) to something private.
4. Enable **Auto-start on boot** if you want the app to resume automatically after a reboot.

---

## SMS command reference

All commands start with your keyword (shown here as `phonetrack`). Commands are case-insensitive.

### One-shot location request

```
phonetrack
```

The phone acquires a GPS fix and replies with three SMS messages:

```
[PhoneTrack] Lat: 51.5074, Lon: -0.1278
Acc: 8m, Bat: 73%

geo:51.5074,-0.1278

https://www.openstreetmap.org/?mlat=51.5074&mlon=-0.1278#map=10/51.5074/-0.1278
```

- **Acc** — GPS accuracy radius in metres
- **Bat** — current battery percentage
- The `geo:` URI opens directly in any maps app
- The OpenStreetMap link works in any browser

If location services are turned off when the request arrives, the phone posts a high-priority notification with a 60-second countdown. If you re-enable location services within that window, the fix is sent automatically.

### Subscribe (periodic updates)

```
phonetrack subscribe [--dist N] [--freq N] [--hours N]
```

Starts a recurring location subscription. The phone sends an immediate fix, then continues sending updates on a schedule until the subscription expires or you cancel it.

| Option | Default | Meaning |
|--------|---------|---------|
| `--dist N` | 200 m | Only send an update if you have moved more than N metres since the last one |
| `--freq N` | 15 min | Send an update at most every N minutes (minimum: 1) |
| `--hours N` | 4 h | Cancel the subscription automatically after N hours |

**Examples:**

```
phonetrack subscribe
```
Updates every 15 minutes for 4 hours, skipped if you haven't moved 200 m.

```
phonetrack subscribe --freq 5 --hours 1
```
Updates every 5 minutes for 1 hour.

```
phonetrack subscribe --dist 0 --freq 10 --hours 8
```
Updates every 10 minutes for 8 hours regardless of movement.

If the subscription parameters are invalid, the phone replies with a usage hint.

### Unsubscribe

```
phonetrack unsubscribe
```

Cancels your active subscription. The phone replies to confirm cancellation.

---

## App settings

| Setting | Description |
|---------|-------------|
| SMS responding | Master on/off switch |
| Keyword | The trigger word the phone listens for (default: `phonetrack`) |
| Auto-start on boot | Resume responding automatically after the phone restarts |
| Block all unknown numbers | Ignore DEFAULT-state numbers; only APPROVED numbers get replies |
| Contacts list | Per-number approval state: DEFAULT / APPROVED / BLOCKED |

Active subscriptions are shown in the main screen and can be cancelled by swiping them away.

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
