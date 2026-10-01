#!/usr/bin/env bash
# Notifications revoked (API 33+): the owner cannot be alerted, and nothing crashes.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

echo "perm-no-notifications"
if [ "$(API_LEVEL)" -lt 33 ]; then echo "  SKIP  the notification permission only exists on API 33+"; exit 0; fi
install_apks || { fail "install"; exit 1; }
clean_start
revoke ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS
phase no-notifications NoNotificationsPhaseTest
exit $SCENARIO_FAILED
