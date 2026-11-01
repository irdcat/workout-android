package com.github.irdcat.workout.infrastructure.google

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Drive may report a write's time a moment late; how often and how long to wait for it. */
private const val WRITE_VISIBLE_ATTEMPTS = 3
private const val WRITE_VISIBLE_DELAY_MILLIS = 1_000L

/**
 * Tells whether the spreadsheet was edited after the last sync, from Drive's
 * modified time of the file.
 *
 * Drive's clock is compared only with itself: the version seen when a sync
 * starts is kept, and a later check finds the sheet changed when Drive reports
 * a newer one. The app's own writes change that time too, so [writing] moves
 * the kept version past them, unless someone else had already edited the sheet.
 */
@Singleton
class SheetChangeTracker @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val store: SheetVersionStore,
) {

    /** Held across a read or write and its version update, so a check never sees one half done. */
    private val lock = Mutex()

    /**
     * Runs a sync's [read]. When it succeeds, the version from before it is kept:
     * an edit made while reading then shows as a change rather than being missed.
     */
    suspend fun <T> reading(read: suspend () -> T): T = lock.withLock {
        val before = versionOrNull()
        val result = read()
        store.set(before)
        result
    }

    /**
     * Runs the app's own [write]. If the sheet matched the synced version before
     * it, the version after it is kept, so the write itself isn't seen as a change.
     */
    suspend fun <T> writing(write: suspend () -> T): T = lock.withLock {
        val before = versionOrNull()
        val result = write()
        val synced = store.get()
        if (before != null && before == synced) {
            versionAfter(before)?.let { store.set(it) }
        }
        result
    }

    /** The first version newer than [before], or null when Drive doesn't show one in time. */
    private suspend fun versionAfter(before: SheetVersion): SheetVersion? {
        repeat(WRITE_VISIBLE_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(WRITE_VISIBLE_DELAY_MILLIS)
            val current = versionOrNull() ?: return null
            if (current.modifiedAt.isAfter(before.modifiedAt)) return current
        }
        return null
    }

    /**
     * true when the sheet changed after the last sync, false when it didn't; null
     * when that can't be told: never synced, another sheet linked since, or Drive
     * couldn't be asked (offline, or the Drive API isn't available).
     */
    suspend fun changedSinceSync(): Boolean? = lock.withLock {
        val synced = store.get() ?: return null
        val current = versionOrNull() ?: return null
        if (current.spreadsheetId != synced.spreadsheetId) return null
        current.modifiedAt.isAfter(synced.modifiedAt)
    }

    /** Forgets the synced version, e.g. when the cache is cleared. */
    suspend fun forget() = lock.withLock { store.set(null) }

    private suspend fun versionOrNull(): SheetVersion? =
        try {
            sheetsRepository.currentVersion()
        } catch (_: IOException) {
            // Offline, an HTTP error, or the Drive API turned off for the project.
            null
        } catch (_: IllegalArgumentException) {
            // No spreadsheet saved.
            null
        }
}
