#!/usr/bin/env bash
# Subscriptions in real time (about 14 minutes): cadence, movement arrow, the --dist threshold,
# unsubscribe and expiry. The host reads the sent messages, so API 29+ only.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "slow-cadence"
[ "$(API_LEVEL)" -ge 29 ] || { echo "  SKIP  reading sent messages needs API 29+"; exit 0; }
install_apks || { fail "install"; exit 1; }
ME=$(own_number); ME=${ME:-+15551234567}
NOW_MS=$(($(date +%s) * 1000))
APPR='[{"number":"'$ME'","state":"APPROVED","firstSeen":'$NOW_MS',"lastSeen":'$NOW_MS'}]'
HOME_LON=-122.4194; HOME_LAT=37.7749
FAR_LON=-122.4094        # about 880 m east
FARTHER_LON=-122.3994    # about 1.8 km east

clean_start
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "approvals_list=s:$APPR"
launch
BASE=$(sms_max_id)
sent() { sms_rows_since "$BASE" | grep '^2|'; }
lat_count() { sent | grep -c 'Lat:'; }
send() { "${ADB[@]}" emu sms send "$ME" "$1" >/dev/null; }
geo() { "${ADB[@]}" emu geo fix "$1" "$HOME_LAT" >/dev/null; }
has() { local f="$1"; shift; sent | grep -q "$f"; }
wait_sent() { local t="$1" pattern="$2"; wait_for "$t" bash -c "source '$(dirname "$0")/lib.sh'; sms_rows_since '$BASE' | grep '^2|' | grep -q '$pattern'"; }

# --- 1. a one-minute subscription keeps updating ---
geo $HOME_LON
send "phonetrack subscribe --dist 0 --freq 1 --time 1"
wait_sent 30 "Subscribed: update every 1 min, regardless of movement" && pass "acknowledged" || fail "no acknowledgement: $(sent | head -3)"
wait_sent 40 "Lat: 37.77" && pass "an immediate fix follows the acknowledgement" || fail "no immediate fix"
N0=$(lat_count)
for i in 1 2 3; do geo $HOME_LON; sleep 55; done
sleep 20
N1=$(lat_count)
[ $((N1 - N0)) -ge 2 ] && pass "at least two further updates in about three minutes ($((N1 - N0)))" || fail "only $((N1 - N0)) further updates in three minutes"

# --- 2. moving shows the arrow and the distance ---
geo $FAR_LON
N2=$(lat_count)
if wait_for 90 bash -c "source '$(dirname "$0")/lib.sh'; [ \$(sms_rows_since '$BASE' | grep '^2|' | grep -c 'Lat:') -gt $N2 ]"; then
  # Message bodies span several output lines, so look at the whole new output for a non-zero move.
  sms_rows_since "$BASE" | python3 -c 'import re,sys; sys.exit(0 if re.search(r"[\u2190-\u21FF][1-9]\d*m", sys.stdin.read()) else 1)' \
    && pass "the update after moving carries the arrow and a non-zero distance" || fail "no movement arrow in: $(sms_rows_since "$BASE" | head -6)"
else
  fail "no update after moving"
fi

# --- 3. unsubscribe ---
send "phonetrack unsubscribe"
wait_sent 20 "Your location subscription has been cancelled." && pass "cancelled" || fail "no cancellation reply"
N3=$(lat_count)
sleep 130
[ "$(lat_count)" = "$N3" ] && pass "no updates after unsubscribing" || fail "updates continued after unsubscribing"

# --- 4. the --dist threshold ---
geo $HOME_LON
send "phonetrack subscribe --dist 500 --freq 1 --time 1"
wait_sent 40 "update every 1 min, only if moved 500m+" && pass "threshold subscription acknowledged" || fail "no acknowledgement for --dist 500"
sleep 5
N4=$(lat_count)
sleep 135
[ "$(lat_count)" = "$N4" ] && pass "no update while standing still" || fail "an update was sent without moving"
geo $FARTHER_LON
if wait_for 90 bash -c "source '$(dirname "$0")/lib.sh'; [ \$(sms_rows_since '$BASE' | grep '^2|' | grep -c 'Lat:') -gt $N4 ]"; then pass "moving past the threshold triggers an update"; else fail "no update after moving 1.8 km"; fi
send "phonetrack unsubscribe"; sleep 5

# --- 5. expiry ---
NOW_MS=$(($(date +%s) * 1000))
SUB='[{"number":"'$ME'","distMeters":0,"freqMinutes":1,"durationHours":1,"subscribedAt":'$NOW_MS',"expiresAt":'$((NOW_MS + 70000))',"lastLat":0.0,"lastLon":0.0,"lastSentAt":'$NOW_MS'}]'
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2" "approvals_list=s:$APPR" "subscriptions_list=s:$SUB"
launch
wait_sent 150 "Your location subscription has ended." && pass "the subscriber is told when it ends" || fail "no 'ended' message"
exit $SCENARIO_FAILED
