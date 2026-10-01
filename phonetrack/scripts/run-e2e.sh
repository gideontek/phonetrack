#!/usr/bin/env bash
# One command, one verdict. Runs the unit tests, the instrumented suite and the host scenarios
# on the test emulator, then prints a summary table. Exit code 0 only if everything passed.
#   scripts/run-e2e.sh [--serial emulator-5556] [--api 35] [--fast] [--no-boot]
# Run it from the phonetrack/ directory (the Gradle root).
set -uo pipefail
cd "$(dirname "$0")/.."

SERIAL=""; API=35; FAST=0; BOOT=1
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --api) API="$2"; shift 2 ;;
    --fast) FAST=1; shift ;;
    --no-boot) BOOT=0; shift ;;
    *) echo "unknown option $1"; exit 2 ;;
  esac
done

if [ -z "$SERIAL" ]; then
  if [ "$BOOT" = 1 ]; then SERIAL="$(scripts/test-avd.sh start "$API" | tail -1)"; else echo "--no-boot needs --serial"; exit 2; fi
fi
export ANDROID_SERIAL="$SERIAL" SERIAL
echo "Test device: $SERIAL (API $(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r'))"

declare -a NAMES RESULTS
record() { NAMES+=("$1"); RESULTS+=("$2"); }
run() { # run "<name>" <command...>
  local name="$1"; shift
  echo; echo "=== $name"
  if "$@"; then record "$name" PASS; else record "$name" FAIL; fi
}

run "JVM unit tests"            ./gradlew testDebugUnitTest -q
run "Instrumented (all granted)" ./gradlew connectedDebugAndroidTest -q
# The UI suite's screenshot tour leaves its pictures on the device for a person to look through.
mkdir -p build/e2e-report/screens
adb -s "$SERIAL" pull /sdcard/phonetrack-screens/. build/e2e-report/screens >/dev/null 2>&1 || true
for s in scripts/e2e/*.sh; do
  [ "$(basename "$s")" = lib.sh ] && continue
  case "$(basename "$s")" in slow-*) [ "$FAST" = 1 ] && continue ;; esac
  run "Scenario: $(basename "$s" .sh)" bash "$s"
done

echo; echo "================ SUMMARY (device $SERIAL) ================"
status=0
for i in "${!NAMES[@]}"; do
  printf '%-6s %s\n' "${RESULTS[$i]}" "${NAMES[$i]}"
  [ "${RESULTS[$i]}" = PASS ] || status=1
done
mkdir -p build/e2e-report
for i in "${!NAMES[@]}"; do printf '%s\t%s\n' "${RESULTS[$i]}" "${NAMES[$i]}"; done > build/e2e-report/summary.txt
exit $status
