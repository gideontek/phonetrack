#!/usr/bin/env bash
# A real reboot: settings and approvals survive, an expired subscription is dropped silently, a live
# one's service restarts; with background location revoked the owner is alerted instead.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "slow-boot-persistence"
[ "$(API_LEVEL)" -ge 29 ] || { echo "  SKIP  reading sent messages needs API 29+"; exit 0; }
install_apks || { fail "install"; exit 1; }
NOW_MS=$(($(date +%s) * 1000))
APPR='[{"number":"+15550009001","state":"APPROVED","firstSeen":'$NOW_MS',"lastSeen":'$NOW_MS'}]'
sub() { echo '{"number":"'$1'","distMeters":200,"freqMinutes":15,"durationHours":4,"subscribedAt":'$NOW_MS',"expiresAt":'$2',"lastLat":0.0,"lastLon":0.0,"lastSentAt":'$NOW_MS'}'; }
SUBS="[$(sub +15550009001 $((NOW_MS + 4 * 3600000))),$(sub +15550009002 $((NOW_MS - 60000)))]"

reboot_and_wait() {
  "${ADB[@]}" reboot
  "${ADB[@]}" wait-for-device
  until [ "$("${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
  sleep 10
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do "${ADB[@]}" shell settings put global $s 0; done
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP; "${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

# --- all permissions granted ---
clean_start
seed_prefs "sms_enabled=b:true" "sms_keyword=s:where" "prefs_schema_version=i:2" "approvals_list=s:$APPR" "subscriptions_list=s:$SUBS"
launch
"${ADB[@]}" shell input keyevent KEYCODE_HOME
BASE=$(sms_max_id)
reboot_and_wait
if wait_for 60 bash -c "source '$(dirname "$0")/lib.sh'; running_services | grep -q SubscriptionService"; then
  pass "the live subscription's service restarted after boot"
else
  fail "no SubscriptionService after boot"
fi
P="$(prefs_xml)"
assert_contains "$P" 'name="sms_keyword">where' "keyword survived"
assert_contains "$P" "15550009001" "approvals survived"
assert_not_contains "$P" "15550009002" "the expired subscription was dropped"
assert_not_contains "$(sms_rows_since $BASE | grep '^2|')" "ended" "and nobody was texted that it ended"

# --- background location revoked: the owner is told ---
clean_start
revoke ACCESS_BACKGROUND_LOCATION
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "approvals_list=s:$APPR" "subscriptions_list=s:$SUBS"
launch
"${ADB[@]}" shell input keyevent KEYCODE_HOME
reboot_and_wait
if wait_for 60 bash -c "source '$(dirname "$0")/lib.sh'; notifications | grep -q \"can't send location\""; then
  pass "the owner is alerted after boot when background location is missing"
else
  fail "no owner alert after boot: $(notifications)"
fi
assert_not_contains "$(running_services)" "SubscriptionService" "and no service was started"
exit $SCENARIO_FAILED
