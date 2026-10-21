/*
 * Callectr: call recording for Android.
 * Local recordings use MediaStore by default so setup does not depend on a document-tree picker.
 */
package app.callectr.recorder.system.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.callectr.recorder.utils.AppLogger

/**
 * Default local storage for Callectr.
 *
 * Android 10+ lets an app create its own media files in shared storage without asking for broad
 * file-system permission. Recordings are inserted into Recordings/Callectr as pending media,
 * written through a seekable file descriptor, then published only after the container is finalised.
 */
object MediaStoreHelper {
    private const val TAG = "CV:MediaStore"
    const val RELATIVE_PATH = "Recordings/Callectr"

    /**
     * Android 12+ has a first-class Recordings collection. Android 11 (our minSdk 30) does not,
     * so use Music/Callectr there rather than relying on an OEM accepting an unsupported top-level
     * folder. The user's current Android 16 device always resolves to Recordings/Callectr.
     */
    internal fun relativePathForSdk(sdkInt: Int): String =
        if (sdkInt >= 31) RELATIVE_PATH else "Music/Callectr"

    data class CreatedAudio(
        val uri: Uri,
        val descriptor: ParcelFileDescriptor,
        val displayName: String,
    )

    data class UriInfo(
        val displayName: String?,
        val sizeBytes: Long?,
        val lastModifiedMillis: Long?,
    )

    data class StoredAudio(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val lastModifiedMillis: Long,
    )

    fun createAudioFile(
        context: Context,
        fileName: String,
        mimeType: String,
    ): CreatedAudio? {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = relativePathForSdk(Build.VERSION.SDK_INT)
        val mediaMime = if (fileName.endsWith(".m4a", ignoreCase = true)) "audio/mp4" else mimeType
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mediaMime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }
            .onFailure { AppLogger.e(TAG, "Failed to create MediaStore entry for $fileName", it) }
            .getOrNull() ?: return null

        val pfd = runCatching { resolver.openFileDescriptor(uri, "rw") }
            .onFailure { AppLogger.e(TAG, "Failed to open MediaStore output $uri", it) }
            .getOrNull()

        if (pfd == null) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        AppLogger.i(TAG, "Created pending recording $uri → $relativePath/$fileName")
        return CreatedAudio(uri, pfd, "$relativePath/$fileName")
    }

    /** Make a finished recording visible to Files/audio-player apps. */
    fun publish(context: Context, uri: Uri): Boolean = runCatching {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null) > 0
    }.onFailure {
        AppLogger.e(TAG, "Failed to publish MediaStore recording $uri", it)
    }.getOrDefault(false)

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    fun rename(context: Context, uri: Uri, newName: String): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            if (newName.endsWith(".m4a", ignoreCase = true)) {
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            }
        }
        if (context.contentResolver.update(uri, values, null, null) > 0) uri else null
    }.onFailure {
        AppLogger.w(TAG, "Failed to rename MediaStore recording $uri: ${it.message}")
    }.getOrNull()

    fun info(context: Context, uri: Uri): UriInfo {
        var name: String? = null
        var size: Long? = null
        var modified: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                        name = cursor.getString(it)
                    }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                        if (!cursor.isNull(it)) size = cursor.getLong(it)
                    }
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 }?.let {
                        if (!cursor.isNull(it)) modified = cursor.getLong(it) * 1000L
                    }
                }
            }
        }.onFailure {
            AppLogger.w(TAG, "Could not query metadata for $uri: ${it.message}")
        }
        return UriInfo(name, size, modified)
    }

    /**
     * Enumerates Callectr's automatic local folder. This is used to rebuild the in-app catalog after
     * an app-data reset/reinstall without asking the user to pick a folder again.
     */
    fun listLocalRecordings(context: Context): List<StoredAudio> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val path = relativePathForSdk(Build.VERSION.SDK_INT)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val args = arrayOf(path, "$path/")
        val items = mutableListOf<StoredAudio>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    val modified = if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol) * 1000L
                    items += StoredAudio(uri, name, size, modified)
                }
            }
        }.onFailure {
            AppLogger.w(TAG, "Could not enumerate automatic local recordings: ${it.message}")
        }
        return items
    }

    /** Whether an app-owned MediaStore URI still resolves. */
    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    fun isMediaStoreUri(uri: Uri?): Boolean = uri?.authority == MediaStore.AUTHORITY
}
