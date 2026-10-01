#!/usr/bin/env bash
# The system kills the app in the background: coming back restores the same screen and its expanded
# state, and stored data is intact.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "process-death"
install_apks || { fail "install"; exit 1; }
NOW_MS=$(($(date +%s) * 1000))
PENDING='[{"number":"+15550008001","state":"PENDING","firstSeen":'$((NOW_MS - 600000))',"lastSeen":'$((NOW_MS - 600000))'},{"number":"+15550008002","state":"PENDING","firstSeen":'$((NOW_MS - 300000))',"lastSeen":'$((NOW_MS - 300000))'}]'
clean_start
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "approvals_list=s:$PENDING"
launch

# Main: expand a pending row, then kill the process.
tap_text "+15550008001"; sleep 1
assert_contains "$(ui_text)" "Approve" "a pending row is expanded"
home_and_kill
assert_not_contains "$("${ADB[@]}" shell pidof "$PKG")" "[0-9]" "the process is gone"
"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 3
UI="$(ui_text)"
assert_contains "$UI" "Needs your decision" "back on Main"
assert_contains "$UI" "Approve" "the row is still expanded"
assert_contains "$(prefs_xml)" "15550008001" "stored data is intact"

# Settings: open Reply contents, then kill again.
tap_text "Settings"; sleep 2
tap_text "Reply contents"; sleep 1
assert_contains "$(ui_text)" "Coordinates" "Reply contents is open on Settings"
home_and_kill
"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 3
UI="$(ui_text)"
assert_contains "$UI" "Reply contents" "back on Settings"
assert_contains "$UI" "Coordinates" "with Reply contents still open"
exit $SCENARIO_FAILED
