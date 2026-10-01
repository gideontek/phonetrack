#!/usr/bin/env bash
# Location services left off: after the 60 s window the requester is told, and the alert goes away.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "slow-location-timeout"
if [ "$(API_LEVEL)" -lt 29 ]; then echo "  SKIP  reading replies needs API 29+"; exit 0; fi
install_apks || { fail "install"; exit 1; }
ME=$(own_number); ME=${ME:-+15551234567}
NOW_MS=$(($(date +%s) * 1000))
APPR='[{"number":"'$ME'","state":"APPROVED","firstSeen":'$NOW_MS',"lastSeen":'$NOW_MS'}]'

clean_start
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "approvals_list=s:$APPR"
launch
BASE=$(sms_max_id)
location_services off
sleep 2
"${ADB[@]}" emu sms send "$ME" "phonetrack" >/dev/null
if wait_for 90 bash -c "source '$(dirname "$0")/lib.sh'; sms_rows_since '$BASE' | grep -q '^2|.*Location unavailable (services disabled)'"; then
  pass "the requester is told location services are disabled after the window"
else
  fail "no timeout reply: $(sms_rows_since $BASE | head -3)"
fi
assert_not_contains "$(notifications)" "Enable location services" "the alert is dismissed"
location_services on
exit $SCENARIO_FAILED
