#!/usr/bin/env bash
# The path no other test covers: data written by the last release survives an upgrade. The previous
# tag is built in a throwaway worktree, installed, given legacy data (partly written by its own code
# from a real SMS), then the current build is installed over it.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/lib.sh"

echo "upgrade-from-previous-release"
REPO="$(cd "$ROOT/.." && pwd)"
TAG="${UPGRADE_FROM:-$(git -C "$REPO" describe --tags --abbrev=0 2>/dev/null)}"
[ -n "$TAG" ] || { echo "  SKIP  no previous tag"; exit 0; }
OLD_APK="$ROOT/build/upgrade/old-$TAG.apk"
if [ ! -f "$OLD_APK" ]; then
  mkdir -p "$ROOT/build/upgrade"
  WT="$(mktemp -d /tmp/phonetrack-prev.XXXXXX)"
  git -C "$REPO" worktree add --detach "$WT" "$TAG" >/dev/null 2>&1 || { echo "  SKIP  cannot check out $TAG"; exit 0; }
  cp "$ROOT/local.properties" "$WT/phonetrack/local.properties" 2>/dev/null
  if (cd "$WT/phonetrack" && ./gradlew -q assembleDebug >/dev/null 2>&1); then
    cp "$WT/phonetrack/app/build/outputs/apk/debug/app-debug.apk" "$OLD_APK"
  fi
  git -C "$REPO" worktree remove --force "$WT" >/dev/null 2>&1
  [ -f "$OLD_APK" ] || { echo "  SKIP  could not build $TAG"; exit 0; }
fi
(cd "$ROOT" && ./gradlew -q assembleDebug >/dev/null 2>&1) || { fail "build the current version"; exit 1; }
NEW_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

# --- the old app and its data ---
"${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1
"${ADB[@]}" install "$OLD_APK" >/dev/null || { fail "install $TAG"; exit 1; }
for p in RECEIVE_SMS SEND_SMS ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do
  "${ADB[@]}" shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
done
LEGACY='[{"number":"(555) 000-1111","state":"APPROVED"},{"number":"555-000-1111","state":"BLOCKED"}]'
seed_prefs "sms_enabled=b:true" "sms_keyword=s:where" "settings_pin=s:4321" "approvals_list=s:$LEGACY"
LAUNCH_READY="" launch   # the old release's screen has a different title
"${ADB[@]}" emu sms send +15550007777 "where" >/dev/null     # written by the OLD code
wait_for 15 bash -c "source '$HERE/lib.sh'; prefs_xml | grep -q 15550007777"
P="$(prefs_xml)"
assert_contains "$P" "15550007777" "the old app recorded a pending number"
assert_contains "$P" 'name="settings_pin"' "the old app holds a plaintext PIN"

# --- upgrade in place ---
"${ADB[@]}" install -r "$NEW_APK" >/dev/null || { fail "install the current build over $TAG"; exit 1; }
launch
"${ADB[@]}" emu sms send +15550007778 "where" >/dev/null      # runs the migration in the receiver
wait_for 15 bash -c "source '$HERE/lib.sh'; prefs_xml | grep -q 15550007778"
P="$(prefs_xml)"
assert_contains "$P" 'name="prefs_schema_version" value="2"' "migrated to the current schema"
assert_not_contains "$P" 'name="settings_pin"' "the plaintext PIN is gone"
assert_contains "$P" 'name="settings_pin_hash">v1:' "the PIN is now a hash"
assert_contains "$P" 'name="sms_keyword">where' "the keyword survived"
assert_not_contains "$P" "(555)" "numbers were normalized"
assert_contains "$P" "15550007777" "the number the old app recorded survived"
assert_contains "$P" "15550007778" "and a new one is recorded after the upgrade"
# 555-000-1111 was approved and blocked under two spellings: one entry, the stricter state.
N=$(grep -o '5550001111' <<<"$P" | wc -l)
[ "$N" = 1 ] && pass "the two spellings of one number merged" || fail "the two spellings did not merge ($N entries)"
assert_contains "$P" 'BLOCKED' "and it kept the stricter state"

# --- the UI still works with the migrated data ---
"${ADB[@]}" shell am force-stop "$PKG"; launch
UI="$(ui_text)"
assert_ui "Needs your decision" "Main shows the waiting numbers"
assert_ui "+15550007777" "including the one the old app recorded"
assert_ui "Unlock settings" "settings start locked because a PIN exists"
tap_text "Unlock settings"; sleep 1
tap_text "PIN"; "${ADB[@]}" shell input text 4321; sleep 1
tap_text "Unlock"; sleep 2
assert_ui "Lock settings" "the old PIN still unlocks"
exit $SCENARIO_FAILED
