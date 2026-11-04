package com.mushaf.reader.data.backup

import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity

/**
 * Folds a backup into what this device already holds, instead of replacing it.
 *
 * A reader who keeps the mushaf on a phone and a tablet restores one onto the other expecting to
 * carry on — not to lose whatever the second device had recorded since. So the history is
 * united, and only what can hold a single value moves over from the backup:
 *
 *  - sessions and completed khatmas: both sides, with the copies a restore brings back dropped;
 *  - reading position and markers: the backup's, since restoring it means "continue from there"
 *    (a marker the backup never set leaves this device's in place);
 *  - the current khatma's page map: see [mergeProgress];
 *  - display settings: the backup's only when asked, because a tablet and a phone rarely want
 *    the same layout.
 *
 * Pure on purpose — no Android types — so every rule here is pinned by plain JVM tests.
 */
object BackupMerge {

    fun merge(
        local: BackupSnapshot,
        incoming: BackupSnapshot,
        takeDisplaySettings: Boolean,
    ): BackupSnapshot {
        val khatmas = mergeKhatmas(local.khatmas, incoming.khatmas)
        val progress = mergeProgress(local.reading.settings, incoming.reading.settings, khatmas)
        val base = if (takeDisplaySettings) incoming.reading.settings else local.reading.settings
        val settings = base.copy(
            lastPage = incoming.reading.settings.lastPage,
            visitedPages = progress.visitedPages,
            readPages = progress.readPages,
            khatmaStartedAt = progress.startedAt,
        )
        return incoming.copy(
            reading = ReadingStore.BackupState(
                settings = settings,
                bookmarks = incoming.reading.bookmarks.ifEmpty { local.reading.bookmarks },
                bookmarks2 = incoming.reading.bookmarks2.ifEmpty { local.reading.bookmarks2 },
            ),
            sessions = mergeSessions(local.sessions, incoming.sessions),
            khatmas = khatmas,
        )
    }

    /**
     * A session is the same session when it started and ended at the same instant — ids are only
     * row numbers, so two devices hand out the same ones. Local rows keep their ids; rows that
     * arrive get id 0, which Room replaces with a fresh one on insert.
     */
    fun mergeSessions(local: List<SessionEntity>, incoming: List<SessionEntity>): List<SessionEntity> {
        val known = local.mapTo(HashSet()) { it.startedAt to it.endedAt }
        val added = incoming
            .filter { known.add(it.startedAt to it.endedAt) }
            .map { it.copy(id = 0) }
        return (local + added).sortedByDescending { it.startedAt }
    }

    fun mergeKhatmas(local: List<KhatmaEntity>, incoming: List<KhatmaEntity>): List<KhatmaEntity> {
        val known = local.mapTo(HashSet()) { it.completedAt to it.startedAt }
        val added = incoming
            .filter { known.add(it.completedAt to it.startedAt) }
            .map { it.copy(id = 0) }
        return (local + added).sortedByDescending { it.completedAt }
    }

    data class Progress(val startedAt: Long, val visitedPages: Set<Int>, val readPages: Set<Int>)

    /**
     * Which khatma cycle survives, and with which pages.
     *
     * Every install starts its own cycle on first run, so differing start times alone mean
     * nothing — a tablet set up last week is not "a newer khatma". What does end a cycle is a
     * completed khatma: completing one starts the next at that same instant, so a cycle that
     * began before the latest completion (from either device) is finished, and its pages belong
     * to the khatma already archived. Then:
     *
     *  - a side with no progress at all yields to the other;
     *  - a finished cycle yields to one still running;
     *  - otherwise both are the same khatma read in two places: the pages are united and the
     *    cycle is dated from the earlier start.
     */
    fun mergeProgress(
        local: ReadingStore.Settings,
        incoming: ReadingStore.Settings,
        khatmas: List<KhatmaEntity>,
    ): Progress {
        val mine = Progress(local.khatmaStartedAt, local.visitedPages, local.readPages)
        val theirs = Progress(incoming.khatmaStartedAt, incoming.visitedPages, incoming.readPages)
        if (theirs.isEmpty()) return mine
        if (mine.isEmpty()) return theirs

        val lastCompletion = khatmas.maxOfOrNull { it.completedAt } ?: 0L
        val mineFinished = mine.startedAt in 1 until lastCompletion
        val theirsFinished = theirs.startedAt in 1 until lastCompletion
        return when {
            mineFinished && !theirsFinished -> theirs
            theirsFinished && !mineFinished -> mine
            else -> {
                val read = mine.readPages + theirs.readPages
                Progress(
                    startedAt = listOf(mine.startedAt, theirs.startedAt).filter { it > 0L }.minOrNull() ?: 0L,
                    // A page counted as read was necessarily opened.
                    visitedPages = mine.visitedPages + theirs.visitedPages + read,
                    readPages = read,
                )
            }
        }
    }

    private fun Progress.isEmpty() = visitedPages.isEmpty() && readPages.isEmpty()
}
