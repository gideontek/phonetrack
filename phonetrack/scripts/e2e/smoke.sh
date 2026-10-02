#!/usr/bin/env bash
# Layer 4 smoke: a genuine incoming SMS from an unknown number shows up on Main as pending.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "smoke: install, launch, receive a real SMS"
"${ADB[@]}" install -r "$(dirname "$0")/../../app/build/outputs/apk/debug/app-debug.apk" >/dev/null || { fail "install"; exit 1; }
clean_start
launch
assert_ui "PhoneTrack" "Main screen is showing"

# Turn listening on through the stored state (process is idle), then relaunch.
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell "run-as $PKG sh -c 'mkdir -p shared_prefs; printf \"%s\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><boolean name=\\\"sms_enabled\\\" value=\\\"true\\\" /></map>\" > shared_prefs/phonetrack_prefs.xml'"
launch
CONSOLE_PORT="${SERIAL#emulator-}"
adb ${SERIAL:+-s "$SERIAL"} emu sms send +15550001111 "phonetrack" >/dev/null
wait_for 15 bash -c "source '$(dirname "$0")/lib.sh'; prefs_xml | grep -q 15550001111"
assert_contains "$(prefs_xml)" "15550001111" "number recorded"
assert_contains "$(prefs_xml)" "PENDING" "recorded as PENDING"
assert_ui "+15550001111" "shown under Needs your decision"

exit $SCENARIO_FAILED
