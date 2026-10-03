# Release checklist

A release is cut only when the automated gate passes **and** every manual item below has been done and
recorded. Copy this file into the release notes (or the release PR) and fill in the blanks.

```
Version: ____    Commit: ____    Date: ____    Done by: ____
```

## 1. The automated gate

```bash
cd phonetrack
scripts/release-gate.sh            # full gate, about 1 h 45 min
scripts/release-gate.sh --quick    # API 35 only, no slow scenarios, about 25 min (pre-merge check)
```

The full gate must run on `master`, on a clean tree, with a `versionName` that has not been tagged yet
(bump the version first, step 4). It runs, and writes to `build/release-gate/<version>-<sha>/report.md`:

| Step | What a pass means |
|---|---|
| Lint | zero errors |
| JVM unit tests | pure logic is green |
| `scripts/fdroid-guard.sh` | the release APK is what F-Droid expects: release dependency lists equal the committed baselines (`scripts/fdroid-baseline/`), no `INTERNET` permission, no test classes in the dex, the APK file list is unchanged |
| API 35, full | every instrumented test (protocol, UI, accessibility) and every scenario, including the real reboot, the 60 s location timeout and the 14-minute cadence run |
| API 33, 29, 27, fast | the same instrumented tests and every non-slow scenario (permissions, onboarding, real radio SMS, location services, process death, upgrade from the previous release) |

**Skips are checked, not ignored.** A skipped test fails the gate unless it is a phase test (skipped by
design) or is listed in `scripts/gate-baseline/skips-api<N>.txt` as a known limit of that API level.
Paste the report's verdict and its list of expected skips into the release notes, so a pass says exactly
what was not exercised.

If the F-Droid guard fails on purpose (you added a dependency or permission deliberately), review the
diff, run `scripts/fdroid-guard.sh --update` and commit the new baselines with the change that caused
them.

- [ ] Gate passed (report attached): ____

## 2. Manual checks (cannot be automated)

Do these on real hardware where noted. Tick each one and write what you saw.

- [ ] **TalkBack walk-through** of Main and Settings: focus lands on **Block** when a pending row is
  expanded (the headless emulator cannot observe Compose focus, so this is checked only here); reading
  order is sensible; the pending row announces Expanded/Collapsed and offers the Approve and Block actions;
  the stepper values are announced when they change; the switches read as one labelled switch each.
- [ ] **Screenshots look-through:** open `build/release-gate/.../api-35/screens/` (Main and Settings, light
  and dark, normal and 1.3x text). Nothing clipped, overlapping or unreadable.
- [ ] **Two real phones** (real SIM cards): phone A texts the keyword to phone B; B shows A as a pending
  number; approve it; A gets a location reply with the map link; `subscribe`, `last`, `unsubscribe` and
  `help` behave. Repeat with one phone that has RCS chat enabled for the contact.
- [ ] **Real GPS outdoors:** the reported accuracy and coordinates are plausible; a 1-minute subscription
  shows the movement arrow and distance while walking.
- [ ] **A 4-hour subscription on battery**, screen off, through Doze: updates keep arriving, then the
  "subscription has ended" message does.
- [ ] **One aggressive-battery phone** (an OEM that kills background apps): after the app has been in the
  background for an hour, a request is still answered, or the failure is understood and noted.
- [ ] **Upgrade over the previous release** on a real device with real data (the gate checks this on the
  emulator): install the signed release APK over the previous version; approvals, keyword, subscriptions
  and the PIN survive.
- [ ] **F-Droid build** of the release commit, if the tooling is available (`fdroid build` with the
  metadata in `metadata/`), or a note that the check was skipped: ____

## 3. Known issues carried into this release

State each one in the release notes. Remove it from here when it is fixed.

- **Android 8.0 is no longer supported.** On Android 8.0.0 (API 26) every outgoing SMS fails with a
  `SecurityException` for `READ_PHONE_STATE` (a framework bug fixed in 8.1; reproduced on the API 26
  emulator, sending works on API 27). `minSdk` is 27 so the app is not installable where it cannot reply;
  say so in the release notes under "Changes to know about".
- **Expanded pending rows on Main** collapse again after a visit to Settings (they survive rotation and a
  process kill). Cosmetic.

- [ ] Known issues listed in the notes: ____

## 4. Release mechanics

Only after sections 1 to 3 are complete (the gate itself needs the version bumped first, so the order is:
bump, commit, gate, tag).

1. Bump `versionName` and `versionCode` in `phonetrack/app/build.gradle.kts` (the next release is
   `1.2` / `3`).
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (F-Droid shows it; keep it short).
3. Refresh `fastlane/metadata/android/en-US/images/phoneScreenshots/` from the new UI (the screenshot tour
   images from the gate are the source) and update the README if anything user-facing changed.
4. Commit `chore: release <versionName>`, push, run the full gate on that commit (section 1).
5. Tag the commit with the bare version (`git tag 1.2`, like `1.1`; F-Droid detects it through
   `UpdateCheckMode: Tags`) and push the tag. Create the GitHub release with the notes, including the gate
   verdict and the known issues.
6. F-Droid picks the tag up (`AutoUpdateMode: Version`); check that its build succeeds.
7. **Only now**, comment on the issues the release answers (#5, #6, #11) with the version that fixes them,
   and close them if the person who opened them is satisfied. Do not comment before the tag exists.

## 5. Results (fill in)

```
Gate report: ____               Verdict: ____
Skips (expected, from the report): ____
Manual items done: TalkBack __  Screenshots __  Two phones __  GPS __  Doze __  OEM __  Upgrade __  F-Droid build __
Known issues in the notes: ____
Tag pushed: ____   GitHub release: ____   F-Droid build: ____   Issues commented: ____
```
