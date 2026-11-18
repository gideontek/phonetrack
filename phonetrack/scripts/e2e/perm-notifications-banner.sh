#!/usr/bin/env bash
# Only notifications revoked (API 33+): the Main banner nudges, Skip hides it for good, and Restore default settings brings it back.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "perm-notifications-banner"
if [ "$(API_LEVEL)" -lt 33 ]; then echo "  SKIP  the notification permission only exists on API 33+"; exit 0; fi
install_apks || { fail "install"; exit 1; }
clean_start
revoke POST_NOTIFICATIONS
phase notifications-banner NotificationsBannerPhaseTest
exit $SCENARIO_FAILED
