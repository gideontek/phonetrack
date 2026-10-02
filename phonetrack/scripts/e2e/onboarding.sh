#!/usr/bin/env bash
# Fresh install, nothing granted: the Fix button walks SMS, then location, then background location
# through the real system dialogs. Dialog wording differs between images, so the full walk is API 34+ only.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "onboarding"
install_apks || { fail "install"; exit 1; }
clean_start
revoke RECEIVE_SMS SEND_SMS ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2"
launch
assert_ui "SMS permission is off, so requests can't be received." "first problem: SMS"
tap_text "Fix"; sleep 2
# Some images show a dialog for the SMS group, others grant it straight away: accept both.
if grep -qE "ermission|ackageinstaller|GrantPermissions" <<<"$(focused_window)"; then
  pass "Fix opens the SMS permission dialog"; tap_text "Allow" || tap_text "ALLOW"; sleep 3
else
  pass "Fix granted SMS without a dialog on this image"
fi
assert_not_contains "$(ui_text)" "requests can't be received" "the SMS problem is resolved"
[ "$(API_LEVEL)" -ge 34 ] || { echo "  SKIP  the rest of the walk is API 34+ only"; exit $SCENARIO_FAILED; }
assert_ui "Location permission is off, so replies can't find you." "next problem: location"
tap_text "Fix"; sleep 2
tap_text "While using the app" || tap_text "WHILE USING THE APP"; sleep 3
assert_ui "Allow all the time" "location leads on to the all-the-time choice"
tap_text "Allow all the time"; sleep 2
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 2
assert_not_contains "$(ui_text)" "replies can't" "everything granted: no banner left"
assert_not_contains "$(ui_text)" "requests can't be received" "SMS banner gone too"
exit $SCENARIO_FAILED
