#!/usr/bin/env bash
# Location revoked: generic error + owner alert; the Fix button opens the real permission dialog.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "perm-no-location"
install_apks || { fail "install"; exit 1; }
clean_start
revoke ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION
phase no-location NoLocationPhaseTest

# Fix: the real permission dialog, then the banner moves on to the next missing permission.
clean_start
revoke ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2"
launch
assert_ui "Location permission is off, so replies can't find you." "banner names the problem"
tap_text "Fix"; sleep 2
# The dialog lives in permissioncontroller (newer images) or packageinstaller (API 26-28).
if grep -qE "ermission|ackageinstaller|GrantPermissions" <<<"$(focused_window)"; then pass "Fix opens the system permission dialog"; else fail "Fix opens the system permission dialog (focus: $(focused_window))"; fi
if [ "$(API_LEVEL)" -ge 34 ]; then
  # Granting precise location chains straight into the background-location choice.
  tap_text "While using the app" || tap_text "WHILE USING THE APP"; sleep 3
  assert_ui "Allow all the time" "allowing location leads on to the all-the-time choice"
  tap_text "Allow all the time"; sleep 2
  "${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 2
  assert_not_contains "$(ui_text)" "replies can't" "back in the app, the banner is gone"
fi
exit $SCENARIO_FAILED
