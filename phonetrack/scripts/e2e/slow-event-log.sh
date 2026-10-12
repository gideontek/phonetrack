#!/usr/bin/env bash
# The event log against the real system: a tick is recorded, a location switch-off shows as
# provider-off, a killed process shows as a sticky restart, and nothing identifying is stored.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "slow-event-log"
install_apks || { fail "install"; exit 1; }
NOW_MS=$(($(date +%s) * 1000))
NUMBER="+15550009001"
# A 1-minute subscription, last sent an hour ago, so the service ticks every minute from the start.
SUBS='[{"number":"'$NUMBER'","distMeters":0,"freqMinutes":1,"durationHours":4,"subscribedAt":'$((NOW_MS - 3600000))',"expiresAt":'$((NOW_MS + 14400000))',"lastLat":0.0,"lastLon":0.0,"lastSentAt":'$((NOW_MS - 3600000))'}]'
clean_start
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "event_log_enabled=b:true" "subscriptions_list=s:$SUBS"
launch

event_log() { prefs_xml | grep 'name="event_log"'; }
log_has() { event_log | grep -q "$1"; }

if wait_for 90 log_has TICK; then pass "a tick is logged"; else fail "no TICK within 90 s"; fi
assert_contains "$(event_log)" "SERVICE_START" "the service start is logged"

# Location switched off: the next tick cannot get a fix and says why.
location_services off
if wait_for 360 log_has provider-off; then pass "switching location off shows as provider-off"; else fail "no provider-off within 360 s"; fi
location_services on

# The process is killed (as the system would): the sticky service comes back and records it.
PID=$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r')
"${ADB[@]}" shell run-as "$PKG" kill -9 "$PID"
if wait_for 180 log_has sticky-restart; then pass "a killed service comes back as a sticky restart"; else fail "no sticky-restart within 180 s"; fi

# Privacy: the stored log never holds the subscriber's number.
assert_not_contains "$(event_log)" "5550009001" "the stored log holds no phone number"
exit $SCENARIO_FAILED
