package com.livingroomhq.core.data.repo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified media library across movies, shows and music.
 */
interface MediaRepository {
    val library: StateFlow<List<MediaItem>>
    fun continueWatching(): List<MediaItem>
    fun recentlyAdded(limit: Int = 10): List<MediaItem>
    fun byType(type: MediaType): List<MediaItem>
}

class LocalMediaRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) : MediaRepository {

    private val _library = MutableStateFlow<List<MediaItem>>(emptyList())
    override val library: StateFlow<List<MediaItem>> = _library.asStateFlow()

    init {
        refresh()
        scope.launch {
            while (true) {
                delay(60_000)
                refresh()
            }
        }
    }

    fun refresh() {
        scope.launch {
            _library.value = loadLocalMedia()
        }
    }

    override fun continueWatching(): List<MediaItem> =
        _library.value.filter { it.watchProgress in 0.01f..0.95f }
            .sortedByDescending { it.watchProgress }

    override fun recentlyAdded(limit: Int): List<MediaItem> =
        _library.value.sortedByDescending { it.addedAtMillis }.take(limit)

    override fun byType(type: MediaType): List<MediaItem> =
        _library.value.filter { it.type == type }

    private suspend fun loadLocalMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val video = if (hasVideoPermission()) loadVideo() else emptyList()
        val audio = if (hasAudioPermission()) loadAudio() else emptyList()
        (video + audio).sortedByDescending { it.addedAtMillis }
    }

    private fun hasVideoPermission(): Boolean =
        hasPermission(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasAudioPermission(): Boolean =
        hasPermission(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun loadVideo(): List<MediaItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
        )
        return context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString()).toString()
                    add(
                        MediaItem(
                            id = uri,
                            title = cursor.getString(titleCol).substringBeforeLast('.'),
                            type = MediaType.MOVIE,
                            description = uri,
                            posterUrl = uri,
                            runtimeMinutes = (cursor.getLong(durationCol) / 60_000L).toInt(),
                            addedAtMillis = cursor.getLong(addedCol) * 1_000L,
                        )
                    )
                }
            }
        }.orEmpty()
    }

    private fun loadAudio(): List<MediaItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        return context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString()).toString()
                    add(
                        MediaItem(
                            id = uri,
                            title = cursor.getString(titleCol),
                            type = MediaType.MUSIC,
                            description = cursor.getString(artistCol).orEmpty(),
                            runtimeMinutes = (cursor.getLong(durationCol) / 60_000L).toInt(),
                            addedAtMillis = cursor.getLong(addedCol) * 1_000L,
                        )
                    )
                }
            }
        }.orEmpty()
    }
}
