package com.gideontek.phonetrack

/** What the Fix button on the status banner should do. */
enum class StatusFix { RequestSms, RequestLocation, RequestBgLocation, OpenLocationSettings }

/**
 * What the Main status card says. [problem] is the single most important thing stopping
 * location replies from working, with the [fix] that resolves it; null when all is well.
 */
data class StatusSummary(
    val headline: String,
    val problem: String?,
    val fix: StatusFix?
) {
    companion object {
        /**
         * Problems are only reported while listening is on: someone who turned it off
         * deliberately is not nagged about permissions. Priority follows the grant order
         * (SMS, location, then background location), then location services.
         */
        fun from(
            enabled: Boolean,
            smsGranted: Boolean,
            locationGranted: Boolean,
            bgLocationGranted: Boolean,
            locationServicesEnabled: Boolean
        ): StatusSummary {
            if (!enabled) return StatusSummary("Listening is off", null, null)
            return when {
                !smsGranted -> StatusSummary(
                    "Listening",
                    "SMS permission is off, so requests can't be received.",
                    StatusFix.RequestSms
                )
                !locationGranted -> StatusSummary(
                    "Listening",
                    "Location permission is off, so replies can't find you.",
                    StatusFix.RequestLocation
                )
                !bgLocationGranted -> StatusSummary(
                    "Listening",
                    "Background location is off, so replies can't start.",
                    StatusFix.RequestBgLocation
                )
                !locationServicesEnabled -> StatusSummary(
                    "Listening",
                    "Location services are off on this phone.",
                    StatusFix.OpenLocationSettings
                )
                else -> StatusSummary("Listening", null, null)
            }
        }
    }
}

object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** "just now", "12 min ago", "2 h ago", "3 d ago". */
    fun ago(now: Long, then: Long): String {
        val diff = (now - then).coerceAtLeast(0L)
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> "${diff / MINUTE} min ago"
            diff < DAY -> "${diff / HOUR} h ago"
            else -> "${diff / DAY} d ago"
        }
    }

    /** "2 h 10 min left", "14 min left", "less than a minute left". */
    fun left(remainingMs: Long): String {
        val ms = remainingMs.coerceAtLeast(0L)
        return when {
            ms < MINUTE -> "less than a minute left"
            ms < HOUR -> "${ms / MINUTE} min left"
            else -> {
                val hours = ms / HOUR
                val minutes = (ms % HOUR) / MINUTE
                if (minutes == 0L) "$hours h left" else "$hours h $minutes min left"
            }
        }
    }
}

/** One live subscription, ready to show. */
data class SubscriptionView(
    val number: String,
    val cadence: String,
    val fraction: Float,
    val leftText: String,
    val totalText: String
) {
    companion object {
        /** Live (not yet expired) subscriptions, soonest to end first. */
        fun active(subs: List<Subscription>, now: Long): List<SubscriptionView> =
            subs.filter { it.expiresAt > now }
                .sortedBy { it.expiresAt }
                .map { of(it, now) }

        fun of(sub: Subscription, now: Long): SubscriptionView {
            val totalMs = sub.durationHours * 3_600_000L
            val remaining = (sub.expiresAt - now).coerceAtLeast(0L)
            val fraction = if (totalMs <= 0L) 0f
                else (remaining.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val movement = if (sub.distMeters <= 0) "any movement" else "moves of ${sub.distMeters}m+"
            return SubscriptionView(
                number = sub.number,
                cadence = "every ${sub.freqMinutes}min · $movement",
                fraction = fraction,
                leftText = RelativeTime.left(remaining),
                totalText = "of ${sub.durationHours} h"
            )
        }
    }
}

/** The approvals list split for the Main screen. */
data class KnownNumbers(
    val pending: List<ApprovalEntry>,
    val approved: List<ApprovalEntry>,
    val blocked: List<ApprovalEntry>
) {
    companion object {
        /** Pending: most recently asked first (entries with no timestamp last). */
        fun split(entries: List<ApprovalEntry>): KnownNumbers = KnownNumbers(
            pending = entries.filter { it.state == ApprovalState.PENDING }
                .sortedByDescending { it.lastSeen },
            approved = entries.filter { it.state == ApprovalState.APPROVED },
            blocked = entries.filter { it.state == ApprovalState.BLOCKED }
        )
    }
}
