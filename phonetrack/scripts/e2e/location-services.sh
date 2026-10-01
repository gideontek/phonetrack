#!/usr/bin/env bash
# A request arrives while location services are off: the owner gets a countdown alert, and the
# request is answered once location is switched back on inside the 60 s window.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "location-services"
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
"${ADB[@]}" emu geo fix -122.4194 37.7749 >/dev/null
"${ADB[@]}" emu sms send "$ME" "phonetrack" >/dev/null
if wait_for 20 bash -c "source '$(dirname "$0")/lib.sh'; notifications | grep -q 'Enable location services'"; then
  pass "the owner is alerted that location services are off"
else
  fail "no owner alert: $(notifications)"
fi
assert_not_contains "$(sms_rows_since $BASE | grep '^2|')" "Lat:" "nothing is sent while location is off"

location_services on
"${ADB[@]}" emu geo fix -122.4194 37.7749 >/dev/null
if wait_for 45 bash -c "source '$(dirname "$0")/lib.sh'; sms_rows_since '$BASE' | grep -q '^2|.*Lat: 37.77'"; then
  pass "the request is answered once location is back on"
else
  fail "no reply after switching location on: $(sms_rows_since $BASE | head -3)"
fi
assert_not_contains "$(notifications)" "Enable location services" "the alert is dismissed"
exit $SCENARIO_FAILED
