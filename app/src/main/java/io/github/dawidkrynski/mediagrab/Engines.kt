package io.github.dawidkrynski.mediagrab

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Canceled : Exception()

class EngineError(val stderr: String) : Exception()

/** Runs yt-dlp and gallery-dl on the Python interpreter bundled with youtubedl-android. */
class Engines(private val context: Context) {
    @Volatile private var galleryProcess: Process? = null
    @Volatile private var canceled = false
    private val prefs = context.getSharedPreferences("engines", Context.MODE_PRIVATE)

    fun init() {
        YoutubeDL.init(context)
        FFmpeg.init(context)
    }

    fun updateIsOverdue(): Boolean =
        System.currentTimeMillis() - prefs.getLong(LAST_UPDATE, 0) > UPDATE_INTERVAL_MS

    val neverUpdated: Boolean get() = prefs.getLong(LAST_UPDATE, 0) == 0L

    /** Sites change often, so yt-dlp stays current. A failed update keeps the existing version. */
    fun updateYtDlp() {
        val worker = thread(isDaemon = true) {
            runCatching { YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE) }
                .onSuccess { prefs.edit().putLong(LAST_UPDATE, System.currentTimeMillis()).apply() }
        }
        worker.join(UPDATE_WAIT_MS)
    }

    fun ytDlp(url: String, dir: File, cookies: File?, onProgress: (Int) -> Unit) {
        throwIfCanceled()
        val request = YoutubeDLRequest(url).apply {
            addOption("--ignore-config")
            addOption("--no-colors")
            addOption("--no-playlist")
            addOption("--playlist-items", "1:$MAX_ITEMS")
            addOption("--socket-timeout", "20")
            addOption("--retries", "3")
            addOption("--extractor-retries", "2")
            addOption("--no-overwrites")
            addOption("--no-continue")
            addOption("-S", "res:1080,vcodec:h264,acodec:aac,ext:mp4:m4a")
            addOption("--merge-output-format", "mp4")
            addOption("--windows-filenames")
            addOption("-P", dir.path)
            addOption("-o", "%(title).70B [%(id)s].%(ext)s")
            cookies?.let { addOption("--cookies", it.path) }
        }
        try {
            YoutubeDL.execute(request, PROCESS_ID) { progress, _, _ ->
                // A cancel can arrive before the process is registered and find nothing to stop.
                if (canceled) YoutubeDL.destroyProcessById(PROCESS_ID)
                onProgress(progress.toInt())
            }
        } catch (_: YoutubeDL.CanceledException) {
            throw Canceled()
        } catch (e: YoutubeDLException) {
            if (canceled) throw Canceled()
            throw EngineError(e.message.orEmpty())
        }
        throwIfCanceled()
    }

    fun galleryDl(url: String, dir: File, cookies: File?) {
        throwIfCanceled()
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val python = File(packages, "python/usr")
        val command = mutableListOf(
            File(context.applicationInfo.nativeLibraryDir, "libpython.so").path,
            "-m", "gallery_dl",
            "--config-ignore", "--no-input", "--no-colors",
            "--retries", "2",
            "--http-timeout", "20",
            "-o", "cache.file=null",
            "-o", "extractor.cookies-update=false",
            "--range", "1-$MAX_GALLERY_ITEMS",
            "--directory", dir.path,
            "--restrict-filenames", "auto",
        )
        cookies?.let { command += listOf("--cookies", it.path) }
        command += listOf("--", url)
        val builder = ProcessBuilder(command).redirectOutput(File("/dev/null"))
        builder.environment().apply {
            put("LD_LIBRARY_PATH", "${python.path}/lib")
            put("SSL_CERT_FILE", "${python.path}/etc/tls/cert.pem")
            put("REQUESTS_CA_BUNDLE", "${python.path}/etc/tls/cert.pem")
            put("PYTHONHOME", python.path)
            put("PYTHONPATH", galleryArchive().path)
            put("HOME", context.cacheDir.path)
            put("TMPDIR", context.cacheDir.path)
        }
        val process = builder.start()
        galleryProcess = process
        if (canceled) process.destroy()
        val stderr = StringBuilder()
        val reader = thread(isDaemon = true) {
            process.errorStream.bufferedReader().forEachLine { if (stderr.length < MAX_STDERR) stderr.appendLine(it) }
        }
        val finished = process.waitFor(GALLERY_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        if (!finished) process.destroyForcibly()
        reader.join(1000)
        galleryProcess = null
        if (canceled) throw Canceled()
        if (!finished || process.exitValue() != 0) throw EngineError(stderr.toString())
    }

    fun cancel() {
        canceled = true
        YoutubeDL.destroyProcessById(PROCESS_ID)
        galleryProcess?.destroy()
    }

    fun throwIfCanceled() {
        if (canceled) throw Canceled()
    }

    fun resetCancel() {
        canceled = false
    }

    private fun galleryArchive(): File {
        val target = File(context.noBackupFilesDir, "gallery_dl-${BuildConfig.VERSION_CODE}.zip")
        if (!target.exists()) {
            target.parentFile?.listFiles { f -> f.name.startsWith("gallery_dl-") }?.forEach { it.delete() }
            val temp = File(target.path + ".tmp")
            context.assets.open("gallery_dl.zip").use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.renameTo(target)
        }
        return target
    }

    private companion object {
        const val PROCESS_ID = "mediagrab"
        const val LAST_UPDATE = "last_update"
        const val UPDATE_INTERVAL_MS = 24 * 3600 * 1000L
        const val UPDATE_WAIT_MS = 30_000L
        const val MAX_ITEMS = 10
        const val MAX_GALLERY_ITEMS = 101
        const val MAX_STDERR = 16_000
        const val GALLERY_TIMEOUT_MINUTES = 30L
    }
}
