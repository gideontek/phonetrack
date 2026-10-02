#!/usr/bin/env bash
# The F-Droid rules, checked on the real release artifact. F-Droid builds the release variant from
# Google/Maven Central only, so a new dependency, test code in the APK or an INTERNET permission must
# be a deliberate change, not an accident.
#   scripts/fdroid-guard.sh            compare against the committed baselines (exit 1 on any difference)
#   scripts/fdroid-guard.sh --update   rewrite the baselines (review the diff before committing it)
set -uo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
APKANALYZER="$SDK/cmdline-tools/latest/bin/apkanalyzer"
BASE=scripts/fdroid-baseline
APK=app/build/outputs/apk/release/app-release-unsigned.apk
UPDATE=0; [ "${1:-}" = "--update" ] && UPDATE=1
FAILED=0
pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; FAILED=1; }

mkdir -p "$BASE" build/fdroid-guard
echo "fdroid-guard"
./gradlew -q :app:assembleRelease >/dev/null 2>&1 || { fail "assembleRelease"; exit 1; }
[ -f "$APK" ] || { fail "release APK not found at $APK"; exit 1; }

# What is checked, each as a text file that can be diffed.
for cfg in releaseRuntimeClasspath releaseCompileClasspath; do
  ./gradlew -q :app:dependencies --configuration "$cfg" 2>/dev/null > "build/fdroid-guard/$cfg.txt"
done
"$TOOLS/aapt2" dump permissions "$APK" | sort > build/fdroid-guard/permissions.txt
unzip -Z1 "$APK" | sort > build/fdroid-guard/apk-files.txt
# Class lines ("C d ... <name>") of every class the release dex defines.
"$APKANALYZER" dex packages --defined-only "$APK" 2>/dev/null | awk '$1=="C"{print $NF}' | sort > build/fdroid-guard/dex-classes.txt
[ "$(grep -c '^com.gideontek.phonetrack' build/fdroid-guard/dex-classes.txt)" -gt 0 ] || { fail "could not read the app's classes from the release dex"; exit 1; }

for f in releaseRuntimeClasspath releaseCompileClasspath permissions apk-files; do
  if [ $UPDATE = 1 ]; then cp "build/fdroid-guard/$f.txt" "$BASE/$f.txt"; echo "  updated $BASE/$f.txt"; continue; fi
  if diff -u "$BASE/$f.txt" "build/fdroid-guard/$f.txt" > "build/fdroid-guard/$f.diff"; then
    pass "$f matches the baseline"
  else
    fail "$f differs from the baseline (build/fdroid-guard/$f.diff):"; head -20 "build/fdroid-guard/$f.diff" | sed 's/^/        /'
  fi
done
[ $UPDATE = 1 ] && exit 0

# Absolute rules, independent of the baselines.
if grep -q "android.permission.INTERNET" build/fdroid-guard/permissions.txt; then fail "the release APK requests INTERNET"; else pass "no INTERNET permission"; fi
TESTY=$(grep -E "^(androidx\.test|androidx\.compose\.ui\.test|org\.junit|junit|com\.gideontek\.phonetrack\.(support|protocol|phase|smoke))" build/fdroid-guard/dex-classes.txt || true)
if [ -n "$TESTY" ]; then fail "test code in the release dex: $(echo "$TESTY" | head -5 | tr '\n' ' ')"; else pass "no test classes in the release dex"; fi
exit $FAILED
