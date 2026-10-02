#!/usr/bin/env bash
# A genuine radio-delivered SMS end to end: pending on Main, approved by tapping, answered with the
# emulator's real GPS fix. Reading replies from the shell needs API 29+.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "real-sms-flow"
install_apks || { fail "install"; exit 1; }
ME=$("${ADB[@]}" shell service call iphonesubinfo 15 | python3 -c '
import re,sys
w=[int(x,16) for x in re.findall(r"\b[0-9a-f]{8}\b", re.sub(r"0x[0-9a-f]{8}:","",sys.stdin.read()))]
n=w[1]; ch=[]
for x in w[2:]: ch += [x & 0xFFFF, x >> 16]
print("".join(map(chr, ch[:n])))' 2>/dev/null)
ME=${ME:-+15551234567}
HAVE_SMS_READ=1; [ "$(API_LEVEL)" -ge 29 ] || HAVE_SMS_READ=0

clean_start
seed_prefs "sms_enabled=b:true" "prefs_schema_version=i:2"
launch
BASE=$(sms_max_id)
"${ADB[@]}" emu geo fix -122.4194 37.7749 >/dev/null

"${ADB[@]}" emu sms send "$ME" "phonetrack" >/dev/null
wait_for 15 bash -c "source '$(dirname "$0")/lib.sh'; prefs_xml | grep -q PENDING"
UI="$(ui_text)"
assert_ui "Needs your decision" "the request shows under Needs your decision"
assert_ui "asked" "with how long ago it asked"
tap_text "Approve"; sleep 2
assert_contains "$(prefs_xml)" "APPROVED" "tapping Approve stores the decision"

if [ $HAVE_SMS_READ = 1 ]; then
  "${ADB[@]}" emu sms send "$ME" "phonetrack" >/dev/null
  if wait_for 40 bash -c "source '$(dirname "$0")/lib.sh'; sms_rows_since '$BASE' | grep -q '^2|.*Lat: 37.77'"; then
    pass "the next request is answered with the GPS fix"
  else
    fail "no location reply arrived: $(sms_rows_since $BASE | head -3)"
  fi
  "${ADB[@]}" emu sms send "$ME" "phonetrack last" >/dev/null
  if wait_for 20 bash -c "source '$(dirname "$0")/lib.sh'; sms_rows_since '$BASE' | grep -q '^2|.*Last known ('"; then
    pass "last answers from the cached fix with its age"
  else
    fail "no last reply: $(sms_rows_since $BASE | head -3)"
  fi
else
  echo "  SKIP  reading replies needs API 29+"
fi

# A stranger is recorded and never answered.
"${ADB[@]}" emu sms send +15550009999 "phonetrack" >/dev/null
wait_for 15 bash -c "source '$(dirname "$0")/lib.sh'; prefs_xml | grep -q 15550009999"
assert_contains "$(prefs_xml)" "15550009999" "a stranger is recorded as pending"
sleep 4
# Only sent rows (type 2) count: the stranger's own message is in the inbox.
[ $HAVE_SMS_READ = 1 ] && assert_not_contains "$(sms_rows_since $BASE | grep '^2|')" "15550009999" "and nothing is texted to them"
exit $SCENARIO_FAILED
