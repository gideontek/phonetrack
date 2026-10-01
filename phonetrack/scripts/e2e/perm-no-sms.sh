#!/usr/bin/env bash
# SMS permission revoked: banner, nothing recorded from a real SMS; SEND_SMS alone revoked: no crash.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "perm-no-sms"
install_apks || { fail "install"; exit 1; }
clean_start
revoke RECEIVE_SMS SEND_SMS
phase no-sms NoSmsPermissionPhaseTest

# A real incoming SMS is not delivered to an app without RECEIVE_SMS.
clean_start
revoke RECEIVE_SMS SEND_SMS
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2"
launch
"${ADB[@]}" emu sms send +15550001111 "phonetrack" >/dev/null
sleep 5
assert_not_contains "$(prefs_xml)" "15550001111" "a real SMS is not recorded without the permission"

clean_start
revoke SEND_SMS
phase no-send-sms NoSmsPermissionPhaseTest
exit $SCENARIO_FAILED
