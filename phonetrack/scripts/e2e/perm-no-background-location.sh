#!/usr/bin/env bash
# Background location revoked: generic requester error, owner alert, stored subscription that starts
# once the owner grants the permission and returns to the app.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "perm-no-background-location"
if [ "$(API_LEVEL)" -lt 29 ]; then echo "  SKIP  background location only exists on API 29+"; exit 0; fi
install_apks || { fail "install"; exit 1; }

clean_start
revoke ACCESS_BACKGROUND_LOCATION
phase no-bg-location NoBackgroundLocationPhaseTest

# The resume path: a subscription stored while the permission was missing starts once it is granted.
NOW_MS=$(($(date +%s) * 1000))
SUB='[{"number":"+15550007001","distMeters":200,"freqMinutes":15,"durationHours":4,"subscribedAt":'$NOW_MS',"expiresAt":'$((NOW_MS + 4 * 3600000))',"lastLat":0.0,"lastLon":0.0,"lastSentAt":'$NOW_MS'}]'
clean_start
revoke ACCESS_BACKGROUND_LOCATION
seed_prefs "sms_enabled=b:true" "subscriptions_list=s:$SUB" "prefs_schema_version=i:2"
launch
assert_not_contains "$(running_services)" "SubscriptionService" "no periodic service while the permission is missing"
assert_ui "Background location is off, so replies can't start." "Main shows the problem"
grant ACCESS_BACKGROUND_LOCATION
"${ADB[@]}" shell input keyevent KEYCODE_HOME; sleep 1; launch
if wait_for 15 bash -c "source '$(dirname "$0")/lib.sh'; running_services | grep -q SubscriptionService"; then
  pass "the stored subscription's service started after the grant"
else
  fail "the stored subscription's service did not start after the grant"
fi
assert_not_contains "$(ui_text)" "Background location is off" "the banner is gone"

exit $SCENARIO_FAILED
