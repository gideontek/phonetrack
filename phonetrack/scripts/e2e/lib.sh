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
  for p in RECEIVE_SMS SEND_SMS ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do
    "${ADB[@]}" shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
  done
}

launch() { "${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 3; }
