package com.livingroomhq.core.data.fault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ring-buffer fault recorder and last-sync status for playlist / guide operations. */
object FaultLog {

    private const val RING_CAPACITY = 32

    data class FaultEntry(
        val source: String,
        val message: String,
        val timestampMillis: Long,
    )

    sealed interface SyncState {
        data object Unknown : SyncState
        data class Ok(val timestampMillis: Long) : SyncState
        data class Failed(val message: String, val timestampMillis: Long) : SyncState
    }

    data class SyncStatus(
        val playlist: SyncState = SyncState.Unknown,
        val guide: SyncState = SyncState.Unknown,
    )

    private val ring = ArrayDeque<FaultEntry>(RING_CAPACITY)
    private val _faults = MutableStateFlow<List<FaultEntry>>(emptyList())
    val faults: StateFlow<List<FaultEntry>> = _faults.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun record(source: String, error: Throwable) {
        val entry = FaultEntry(
            source = source,
            message = error.localizedMessage ?: error.javaClass.simpleName,
            timestampMillis = System.currentTimeMillis(),
        )
        synchronized(ring) {
            if (ring.size >= RING_CAPACITY) ring.removeFirst()
            ring.addLast(entry)
            _faults.value = ring.toList()
        }
    }

    fun recordPlaylistSuccess() {
        _syncStatus.value = _syncStatus.value.copy(
            playlist = SyncState.Ok(System.currentTimeMillis()),
        )
    }

    fun recordPlaylistFailure(error: Throwable) {
        record("playlist", error)
        _syncStatus.value = _syncStatus.value.copy(
            playlist = SyncState.Failed(
                error.localizedMessage ?: "failed",
                System.currentTimeMillis(),
            ),
        )
    }

    fun recordGuideSuccess() {
        _syncStatus.value = _syncStatus.value.copy(
            guide = SyncState.Ok(System.currentTimeMillis()),
        )
    }

    fun recordGuideFailure(error: Throwable) {
        record("guide", error)
        _syncStatus.value = _syncStatus.value.copy(
            guide = SyncState.Failed(
                error.localizedMessage ?: "failed",
                System.currentTimeMillis(),
            ),
        )
    }

    fun formatSyncStatus(status: SyncStatus): String {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val playlist = when (val s = status.playlist) {
            SyncState.Unknown -> "Playlist: —"
            is SyncState.Ok -> "Playlist: synced ${timeFmt.format(Date(s.timestampMillis))}"
            is SyncState.Failed -> "Playlist: failed (${s.message})"
        }
        val guide = when (val s = status.guide) {
            SyncState.Unknown -> "Guide: —"
            is SyncState.Ok -> "Guide: synced ${timeFmt.format(Date(s.timestampMillis))}"
            is SyncState.Failed -> "Guide: failed (${s.message})"
        }
        return "$playlist · $guide"
    }
}

inline fun <T> runLoggedCatching(source: String, block: () -> T): Result<T> =
    runCatching(block).onFailure { FaultLog.record(source, it) }
