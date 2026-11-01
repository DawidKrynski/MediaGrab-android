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

    fun publish(staging: File, source: String): List<Saved> =
        staging.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() !in PARTIAL_EXTENSIONS }
            .sortedBy { it.name }
            .map { publishFile(it, source) }
            .toList()

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

    private companion object {
        val PARTIAL_EXTENSIONS = setOf("part", "ytdl", "temp", "tmp")
    }
}
