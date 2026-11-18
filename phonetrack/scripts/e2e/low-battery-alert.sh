#!/usr/bin/env bash
# The low-battery alert: a subscriber gets one location message when the battery falls to the
# threshold, again only after the level has risen and fallen once more, and none when the setting
# is Off. The host fakes the battery with `dumpsys battery` and reads the sent messages, so API 29+ only.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "low-battery-alert"
[ "$(API_LEVEL)" -ge 29 ] || { echo "  SKIP  reading sent messages needs API 29+"; exit 0; }
install_apks || { fail "install"; exit 1; }
trap '"${ADB[@]}" shell dumpsys battery reset >/dev/null 2>&1' EXIT
ME=$(own_number); ME=${ME:-+15551234567}
NOW_MS=$(($(date +%s) * 1000))
APPR='[{"number":"'$ME'","state":"APPROVED","firstSeen":'$NOW_MS',"lastSeen":'$NOW_MS'}]'
# A subscription due only in 24 h, so no regular update gets in the way of the alerts.
SUB='[{"number":"'$ME'","distMeters":0,"freqMinutes":1440,"durationHours":1,"subscribedAt":'$NOW_MS',"expiresAt":'$((NOW_MS + 3600000))',"lastLat":37.7749,"lastLon":-122.4194,"lastSentAt":'$NOW_MS'}]'
battery() { "${ADB[@]}" shell dumpsys battery set level "$1" >/dev/null; }
alerts() { sms_rows_since "$BASE" | grep '^2|' | grep -c 'Battery low: '; }
wait_alerts() { wait_for "$1" bash -c "source '$(dirname "$0")/lib.sh'; [ \$(sms_rows_since '$BASE' | grep '^2|' | grep -c 'Battery low: ') -ge $2 ]"; }

start() {
  seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "approvals_list=s:$APPR" "subscriptions_list=s:$SUB" "$@"
  # unplug alone leaves the emulator's status at "charging", which the app (rightly) does not alert on.
  "${ADB[@]}" shell dumpsys battery unplug >/dev/null
  "${ADB[@]}" shell dumpsys battery set status 3 >/dev/null
  battery 50
  launch
  "${ADB[@]}" emu geo fix -122.4194 37.7749 >/dev/null
  sleep 5
}

# --- 1. the default (5%): one alert per crossing ---
clean_start
start
BASE=$(sms_max_id)
battery 4
wait_alerts 40 1 && pass "falling to 4% sends the alert" || fail "no alert at 4%: $(sms_rows_since "$BASE" | head -4)"
sms_rows_since "$BASE" | grep '^2|' | grep -q 'Battery low: 4%' && pass "it names the level" || fail "the level is missing"
sleep 3
battery 3; sleep 5
[ "$(alerts)" = 1 ] && pass "falling further does not send another" || fail "$(alerts) alerts after falling from 4% to 3%"
battery 50; sleep 4
battery 4
wait_alerts 40 2 && pass "after the level rose and fell again, a second alert is sent" || fail "no second alert: $(alerts) in total"

# --- 2. Off: nothing is sent ---
clean_start
start "low_battery_alert_pct=i:0"
BASE=$(sms_max_id)
battery 3; sleep 15
[ "$(alerts)" = 0 ] && pass "no alert while the setting is Off" || fail "an alert was sent while Off"

exit $SCENARIO_FAILED
