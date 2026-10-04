#!/usr/bin/env bash
# Create / start / stop the dedicated test emulator so tests never touch the one you review on.
#   scripts/test-avd.sh start [API] [PORT]   default API 35, port 5556; prints the adb serial
#   scripts/test-avd.sh stop  [PORT]
#   scripts/test-avd.sh list
# The AVD is "PhoneTrack_Test_API<N>" (Pixel 5, default x86_64 image, no Play Store).
# A boot that does not finish within BOOT_TIMEOUT seconds (default 300) is killed and tried once more,
# then fails: a hung emulator must not stall a long run (the release gate) for hours.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
ADB="$SDK/platform-tools/adb"

cmd="${1:-}"; shift || true

# True once sys.boot_completed is 1, false if that takes longer than BOOT_TIMEOUT seconds.
wait_for_boot() {
  local serial="$1" deadline=$((SECONDS + ${BOOT_TIMEOUT:-300}))
  while [ $SECONDS -lt $deadline ]; do
    [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 2
  done
  return 1
}

start() {
  local api="${1:-35}" port="${2:-5556}"
  local name="PhoneTrack_Test_API${api}" serial="emulator-${port}"
  local image="system-images;android-${api};default;x86_64"
  if ! "$EMULATOR" -list-avds | grep -qx "$name"; then
    echo "Creating AVD $name from $image" >&2
    echo no | "$AVDMANAGER" create avd -n "$name" -k "$image" -d pixel_5 >/dev/null
  fi
  if "$ADB" devices | grep -q "^${serial}"; then
    echo "$serial already running" >&2
    wait_for_boot "$serial" || { echo "$serial is listed but did not finish booting" >&2; return 1; }
  else
    local attempt
    for attempt in 1 2; do
      echo "Booting $name on port $port (headless, attempt $attempt)..." >&2
      nohup "$EMULATOR" -avd "$name" -port "$port" -no-window -no-audio -no-boot-anim \
        -no-snapshot-save -gpu swiftshader_indirect >/tmp/"$name".log 2>&1 &
      if wait_for_boot "$serial"; then break; fi
      echo "Boot of $name did not finish in ${BOOT_TIMEOUT:-300}s; see /tmp/$name.log" >&2
      "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
      pkill -f "qemu-system.*-avd $name" 2>/dev/null || true
      sleep 5
      [ "$attempt" = 2 ] && return 1
    done
  fi
  # Known, fast, deterministic UI: no animations, screen stays on, keyguard dismissed.
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" -s "$serial" shell settings put global "$s" 0
  done
  "$ADB" -s "$serial" shell settings put global hide_error_dialogs 1   # no "System UI isn't responding" over the app
  "$ADB" -s "$serial" shell svc power stayon true
  "$ADB" -s "$serial" shell input keyevent KEYCODE_WAKEUP
  "$ADB" -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  echo "$serial"
}

case "$cmd" in
  start) start "$@" ;;
  stop)  "$ADB" -s "emulator-${1:-5556}" emu kill ;;
  list)  "$EMULATOR" -list-avds ;;
  *) sed -n '2,7p' "$0"; exit 2 ;;
esac
