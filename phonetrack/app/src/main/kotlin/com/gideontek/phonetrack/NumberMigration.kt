package com.gideontek.phonetrack

/**
 * Pure merge logic for the one-time prefs migration to normalized phone numbers (see
 * [PrefsMigration]). Kept free of org.json so it can be unit tested on the JVM.
 *
 * Only *exact* duplicates after normalization are merged. Numbers that merely [PhoneNumber.matches]
 * (e.g. "+1555…" and "555…") stay separate entries; lookups already treat them as one person.
 */
object NumberMigration {

    /**
     * Normalizes numbers, merges exact duplicates (most restrictive state wins, earliest
     * [ApprovalEntry.firstSeen], latest [ApprovalEntry.lastSeen]) and drops PENDING entries we
     * could never reply to (alphanumeric senders, short codes). Entries with no timestamps
     * (written by older versions) get [now], otherwise they would look ancient and be pruned.
     * Order of first appearance is kept. Idempotent.
     */
    fun mergeApprovals(entries: List<ApprovalEntry>, now: Long): List<ApprovalEntry> {
        val merged = LinkedHashMap<String, ApprovalEntry>()
        for (entry in entries) {
            val number = PhoneNumber.normalize(entry.number)
            if (number.isEmpty()) continue
            if (entry.state == ApprovalState.PENDING && !PhoneNumber.isReplyable(number)) continue
            val first = if (entry.firstSeen == 0L) now else entry.firstSeen
            val last = if (entry.lastSeen == 0L) now else entry.lastSeen
            val existing = merged[number]
            merged[number] = if (existing == null) {
                ApprovalEntry(number, entry.state, first, last)
            } else {
                ApprovalEntry(
                    number = number,
                    state = ApprovalLogic.mostRestrictive(existing.state, entry.state),
                    firstSeen = minOf(existing.firstSeen, first),
                    lastSeen = maxOf(existing.lastSeen, last)
                )
            }
        }
        return merged.values.toList()
    }

    /**
     * Normalizes subscription numbers and merges exact duplicates, keeping the one that
     * expires last. Order of first appearance is kept. Idempotent.
     */
    fun mergeSubscriptions(subs: List<Subscription>): List<Subscription> {
        val merged = LinkedHashMap<String, Subscription>()
        for (sub in subs) {
            val number = PhoneNumber.normalize(sub.number)
            if (number.isEmpty()) continue
            val candidate = sub.copy(number = number)
            val existing = merged[number]
            if (existing == null || candidate.expiresAt > existing.expiresAt) merged[number] = candidate
        }
        return merged.values.toList()
    }
}
