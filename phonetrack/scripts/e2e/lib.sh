# Helpers for host-driven scenarios. Source this; expects SERIAL (default $ANDROID_SERIAL).
PKG=com.gideontek.phonetrack
SERIAL="${SERIAL:-${ANDROID_SERIAL:-}}"
ADB=(adb ${SERIAL:+-s "$SERIAL"})
SCENARIO_FAILED=0

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; SCENARIO_FAILED=1; }

# assert_contains "<haystack>" "<needle>" "<description>"
assert_contains() { if grep -qF -- "$2" <<<"$1"; then pass "$3"; else fail "$3 (missing: $2)"; fi; }
assert_not_contains() { if grep -qF -- "$2" <<<"$1"; then fail "$3 (unexpected: $2)"; else pass "$3"; fi; }

# assert_ui "<needle>" "<description>" [timeout_s]: the screen shows the text, waiting for it to appear (screens settle after a launch)
assert_ui() {
  local end=$((SECONDS + ${3:-20})) ui=""
  while [ $SECONDS -lt $end ]; do
    ui="$(ui_text)"
    grep -qF -- "$1" <<<"$ui" && { pass "$2"; return 0; }
    sleep 1
  done
  fail "$2 (missing: $1)"
  # What was on screen instead, to diagnose a scenario that failed in a long run.
  echo "        focus: $(focused_window)"; echo "$ui" | head -8 | sed 's/^/        ui: /'
}

# prefs_xml: the app's shared preferences (debug build, via run-as)
prefs_xml() { "${ADB[@]}" shell run-as "$PKG" cat shared_prefs/phonetrack_prefs.xml 2>/dev/null; }

# ui_text: all visible text and content descriptions on screen
ui_text() {
  "${ADB[@]}" shell uiautomator dump /sdcard/e2e_ui.xml >/dev/null 2>&1
  "${ADB[@]}" shell cat /sdcard/e2e_ui.xml | grep -o '\(text\|content-desc\)="[^"]*"' | sed 's/^[a-z-]*="//; s/"$//' | grep -v '^$'
}

# wait_for <timeout_s> <command...>: poll until the command succeeds
wait_for() {
  local t="$1"; shift
  local end=$((SECONDS + t))
  while [ $SECONDS -lt $end ]; do "$@" && return 0; sleep 1; done
  return 1
}

# clean_start: stop the app and wipe its data, then grant the runtime permissions
clean_start() {
  "${ADB[@]}" shell am force-stop "$PKG"
  "${ADB[@]}" shell pm clear "$PKG" >/dev/null
  for p in RECEIVE_SMS SEND_SMS READ_SMS ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do
    "${ADB[@]}" shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
  done
}

# wake: screen on, keyguard gone, stays on while plugged in (a long run must not leave the device asleep)
wake() {
  "${ADB[@]}" shell svc power stayon true >/dev/null 2>&1
  # A "System UI isn't responding" dialog (seen after a reboot on a headless emulator) would cover the app.
  "${ADB[@]}" shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
  "${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

# launch: start the app and wait until it is the window in front and its screen is up. A cold start
# can take well over 3 s, and a start can be lost if the device is asleep, so wake it and retry.
# The screen is "up" when it shows the title PhoneTrack; LAUNCH_READY="" launch (an older release with a
# different screen) only waits for the app window.
launch() {
  local attempt ready="${LAUNCH_READY-PhoneTrack}"
  for attempt in 1 2 3; do
    wake
    "${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null
    if wait_for 25 bash -c "source '${BASH_SOURCE[0]}'; focused_window | grep -q '$PKG' && { [ -z '$ready' ] || ui_text | grep -qx '$ready'; }"; then
      sleep 1; return 0
    fi
    echo "  (launch attempt $attempt: app not in front; focus: $(focused_window))"
    "${ADB[@]}" shell input keyevent KEYCODE_HOME; sleep 2
  done
  return 1
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API_LEVEL() { "${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r'; }

# install_apks: the debug app and its test APK (connectedDebugAndroidTest would uninstall them afterwards)
install_apks() { (cd "$ROOT" && ./gradlew -q installDebug installDebugAndroidTest >/dev/null 2>&1); }

# revoke <PERMISSION...>: revoke runtime permissions (this kills the app process, so do it while it is idle)
revoke() { for p in "$@"; do "${ADB[@]}" shell pm revoke "$PKG" "android.permission.$p" 2>/dev/null || true; done; }
grant() { for p in "$@"; do "${ADB[@]}" shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true; done; }

# phase <name> <TestClass>: run one instrumented phase test (androidTest/.../phase) and report it
phase() {
  local out
  out=$("${ADB[@]}" shell am instrument -w -e phase "$1" -e class "com.gideontek.phonetrack.phase.$2" \
        "$PKG.test/androidx.test.runner.AndroidJUnitRunner" 2>&1)
  if grep -q "^OK (" <<<"$out" && ! grep -q "OK (0 tests)" <<<"$out"; then
    pass "phase $1: $(grep '^OK (' <<<"$out")"
  else
    fail "phase $1"; echo "$out" | grep -vE "^INSTRUMENTATION_|^$" | head -40 | sed 's/^/        /'
  fi
}

# sms_rows: recent rows of the SMS provider as "type|address|body" (type 2 = sent). API 29+ only for the shell user.
sms_rows() {
  "${ADB[@]}" shell "content query --uri content://sms --projection type,address,body --sort '_id DESC'" 2>/dev/null \
    | sed -E 's/^Row: [0-9]+ type=([0-9]+), address=([^,]*), body=/\1|\2|/'
}

# sms_max_id / sms_rows_since <id>: the provider keeps rows between runs, so scenarios take a baseline first
sms_max_id() {
  "${ADB[@]}" shell "content query --uri content://sms --projection _id --sort '_id DESC'" 2>/dev/null | head -1 | sed -E 's/.*_id=([0-9]+).*/\1/' | grep -E '^[0-9]+$' || echo 0
}
sms_rows_since() {
  "${ADB[@]}" shell "content query --uri content://sms --projection _id,type,address,body --where '_id>$1' --sort '_id DESC'" 2>/dev/null \
    | sed -E 's/^Row: [0-9]+ _id=[0-9]+, type=([0-9]+), address=([^,]*), body=/\1|\2|/'
}

# running_services: the app's services as the system sees them
running_services() { "${ADB[@]}" shell dumpsys activity services "$PKG" 2>/dev/null; }

# seed_prefs key=type:value ...: write the app's preferences while the app is stopped (types: s, b, i, l)
seed_prefs() {
  "${ADB[@]}" shell am force-stop "$PKG"
  python3 "$ROOT/scripts/e2e/seed_prefs.py" "$@" > /tmp/e2e_prefs.xml
  "${ADB[@]}" push /tmp/e2e_prefs.xml /data/local/tmp/e2e_prefs.xml >/dev/null
  "${ADB[@]}" shell "run-as $PKG sh -c 'mkdir -p shared_prefs; cp /data/local/tmp/e2e_prefs.xml shared_prefs/phonetrack_prefs.xml'"
}

# home_and_kill: send the app to the background and kill its process, as low memory would
home_and_kill() { "${ADB[@]}" shell input keyevent KEYCODE_HOME; sleep 1; "${ADB[@]}" shell am kill "$PKG"; sleep 1; }

# tap_text "<text>": tap the first on-screen node whose text or content description is exactly <text>
tap_text() {
  "${ADB[@]}" shell uiautomator dump /sdcard/e2e_ui.xml >/dev/null 2>&1
  local xy
  xy=$("${ADB[@]}" shell cat /sdcard/e2e_ui.xml | python3 -c '
import re, sys
want = sys.argv[1]
xml = sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", xml):
    node = m.group(0)
    t = re.search(r"\btext=\"([^\"]*)\"", node); d = re.search(r"content-desc=\"([^\"]*)\"", node)
    if want in ((t.group(1) if t else None), (d.group(1) if d else None)):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$1")
  [ -n "$xy" ] && "${ADB[@]}" shell input tap $xy
}

# focused_window: the window that has input focus (to see a system dialog or settings screen)
focused_window() { "${ADB[@]}" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus; }

# location_services on|off: the system location switch
location_services() {
  local on=true mode=3; [ "$1" = off ] && { on=false; mode=0; }
  "${ADB[@]}" shell cmd location set-location-enabled $on >/dev/null 2>&1
  "${ADB[@]}" shell settings put secure location_mode $mode
}

# notifications: what the notification shade holds for the app (title and text lines)
notifications() { "${ADB[@]}" shell dumpsys notification --noredact 2>/dev/null | grep -A30 "pkg=$PKG" | grep -E "android.title|android.text|android.bigText"; }

# approve_own: stored state for listening on and the emulator's own number approved
own_number() {
  "${ADB[@]}" shell service call iphonesubinfo 15 | python3 -c '
import re,sys
w=[int(x,16) for x in re.findall(r"\b[0-9a-f]{8}\b", re.sub(r"0x[0-9a-f]{8}:","",sys.stdin.read()))]
n=w[1]; ch=[]
for x in w[2:]: ch += [x & 0xFFFF, x >> 16]
print("".join(map(chr, ch[:n])))' 2>/dev/null
}
