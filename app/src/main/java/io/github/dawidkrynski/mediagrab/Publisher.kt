package io.github.dawidkrynski.mediagrab

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/** Copies finished files from staging into Download/Media/<source>. MediaStore never overwrites. */
class Publisher(private val context: Context) {
    data class Saved(val uri: Uri, val mimeType: String)

    data class Published(val files: List<Saved>, val anyFailed: Boolean)

    /** One unreadable or unwritable file must not hide the ones already saved. */
    fun publish(staging: File, source: String, checkCanceled: () -> Unit): Published {
        val files = staging.walkTopDown().filter { it.isFile && isFinished(it.name) }.sortedBy { it.name }.toList()
        val saved = files.mapNotNull { file ->
            checkCanceled()
            runCatching { publishFile(file, source) }.getOrNull()
        }
        return Published(saved, saved.size < files.size)
    }

    private fun publishFile(file: File, source: String): Saved {
        val resolver = context.contentResolver
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Media/$source")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused the file")
        try {
            resolver.openOutputStream(uri).use { output ->
                file.inputStream().use { it.copyTo(requireNotNull(output)) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(uri, mimeType)
    }

    companion object {
        private val PARTIAL_EXTENSIONS = setOf("part", "ytdl", "temp", "tmp")
        private val UNMERGED_STREAM = Regex("""\.f[0-9a-z-]+\.\w+$""", RegexOption.IGNORE_CASE)

        /** After a failed merge yt-dlp leaves separate streams such as name.f137.mp4. */
        fun isFinished(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() !in PARTIAL_EXTENSIONS && !UNMERGED_STREAM.containsMatchIn(name)
    }
}
