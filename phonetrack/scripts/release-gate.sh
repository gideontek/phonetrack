#!/usr/bin/env bash
# One command, one verdict: can this commit be released?
#   scripts/release-gate.sh            full gate: lint, unit tests, F-Droid guard, then every scenario
#                                      (slow ones too) on API 35 and the fast ones on API 33, 29 and 26
#   scripts/release-gate.sh --quick    API 35 only, no slow scenarios (about 25 minutes): a pre-merge check
#   scripts/release-gate.sh --update-skips   also record the expected skips per API level (review the diff)
#   scripts/release-gate.sh --allow-dirty    downgrade the git preflight to warnings (for testing the gate itself)
# Writes build/release-gate/<version>-<sha>/report.md. Exit 0 only if everything passed.
# Run it from anywhere; it uses the dedicated test AVDs on port 5556 (never your review emulator).
set -uo pipefail
cd "$(dirname "$0")/.."

QUICK=0; UPDATE=0; DIRTY=0
for a in "$@"; do case "$a" in --quick) QUICK=1 ;; --update-skips) UPDATE=1 ;; --allow-dirty) DIRTY=1 ;; *) echo "unknown option $a"; exit 2 ;; esac; done

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)
SHA=$(git rev-parse --short HEAD)
OUT="$PWD/build/release-gate/$VERSION-$SHA"
rm -rf "$OUT"; mkdir -p "$OUT"
PORT=5556
PREFLIGHT_FAILED=0
note() { echo "$*"; }
step_row() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$OUT/steps.tsv"; }

echo "=== Preflight ($VERSION, $SHA)"
if [ "$QUICK" = 0 ]; then
  bad() { if [ $DIRTY = 1 ]; then note "  WARN  $1 (allowed by --allow-dirty)"; else note "  FAIL  $1"; PREFLIGHT_FAILED=1; fi; }
  [ "$(git rev-parse --abbrev-ref HEAD)" = master ] || bad "not on master"
  [ -z "$(git status --porcelain)" ] || bad "the working tree is not clean"
  if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
    bad "versionName $VERSION is already tagged: bump the version first (see RELEASE_CHECKLIST.md)"
  fi
fi
echo "Last tag: $(git describe --tags --abbrev=0 2>/dev/null || echo none), $(git rev-list --count "$(git describe --tags --abbrev=0 2>/dev/null)"..HEAD 2>/dev/null || echo ?) commits since."
[ $PREFLIGHT_FAILED = 0 ] || { step_row FAIL 0 "Preflight"; python3 scripts/gate_report.py "$OUT" "$VERSION" "$SHA" >/dev/null; exit 1; }

timed() { # timed "<name>" <command...>: run, record PASS/FAIL with the seconds it took
  local name="$1"; shift
  local t0=$SECONDS
  echo; echo "=== $name"
  if "$@"; then step_row PASS $((SECONDS - t0)) "$name"; else step_row FAIL $((SECONDS - t0)) "$name"; fi
}
timed "Lint (zero errors)"   ./gradlew lint -q
timed "JVM unit tests"       ./gradlew testDebugUnitTest -q
timed "F-Droid guard"        scripts/fdroid-guard.sh

if [ $QUICK = 1 ]; then APIS=(35); else APIS=(35 33 29 26); fi
for api in "${APIS[@]}"; do
  scripts/test-avd.sh stop $PORT >/dev/null 2>&1; sleep 3
  SERIAL="$(scripts/test-avd.sh start "$api" $PORT | tail -1)"
  FAST=""; { [ $QUICK = 1 ] || [ "$api" != 35 ]; } && FAST="--fast"
  echo; echo "=== API $api on $SERIAL ${FAST:+(fast scenarios only)}"
  mkdir -p "$OUT/api-$api"
  scripts/run-e2e.sh --serial "$SERIAL" --no-boot --skip-unit $FAST --out "$OUT/api-$api" 2>&1 | tee "$OUT/api-$api/log.txt" | tail -n 40
done
scripts/test-avd.sh stop $PORT >/dev/null 2>&1

echo; python3 scripts/gate_report.py "$OUT" "$VERSION" "$SHA" $([ $UPDATE = 1 ] && echo --update-skips)
STATUS=$?
echo; echo "Report: $OUT/report.md"
exit $STATUS
