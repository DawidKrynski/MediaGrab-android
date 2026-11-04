package io.github.dawidkrynski.mediagrab

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Downloads shared links one at a time. Anything shared meanwhile joins the queue. */
class DownloadService : Service() {
    private sealed interface Outcome {
        data class Saved(val files: List<Publisher.Saved>, val partial: Failure?) : Outcome
        data class Failed(val failure: Failure) : Outcome
        data object Canceled : Outcome
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val queue = ConcurrentLinkedQueue<String>()
    private val pending = AtomicInteger(0)
    @Volatile private var current: String? = null
    private lateinit var engines: Engines
    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastProgressAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // No job runs yet, so anything here was left by a killed process.
        File(cacheDir, "jobs").deleteRecursively()
        engines = Engines(applicationContext)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mediagrab:download")
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, getString(R.string.channel_result), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            PROGRESS_ID,
            progressNotification(getString(R.string.preparing), null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (intent?.action == ACTION_CANCEL) {
            cancelAll()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (url != current && url !in queue) {
            queue.add(url)
            pending.incrementAndGet()
            executor.execute(::runNext)
        }
        return START_NOT_STICKY
    }

    /** Android kills an app that outlives this callback, so stopping cannot wait for the worker. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engines.cancel()
        executor.shutdown()
        super.onDestroy()
    }

    private fun cancelAll() {
        val dropped = generateSequence { queue.poll() }.count()
        pending.addAndGet(-dropped)
        engines.cancel()
        stopIfIdle()
    }

    private fun runNext() {
        // Reset before taking the link: a cancel after this point either empties the queue or stays set.
        engines.resetCancel()
        val url = queue.poll() ?: return
        wakeLock.acquire(WAKE_LOCK_MS)
        current = url
        try {
            val outcome = download(url)
            report(url, outcome)
            if (engines.updateIsOverdue() && pending.get() == 1) engines.updateYtDlp()
        } finally {
            wakeLock.release()
            current = null
            pending.decrementAndGet()
            main.post(::stopIfIdle)
        }
    }

    private fun stopIfIdle() {
        if (pending.get() <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun download(url: String): Outcome {
        val source = UrlRules.source(url)
        val staging = File(cacheDir, "jobs/${System.nanoTime()}")
        val media = File(staging, "media").apply { mkdirs() }
        var failure: Failure? = null
        try {
            val cookies = if (source == "instagram") InstagramSession(this).copyTo(File(staging, "cookies.txt")) else null
            engines.init()
            if (engines.neverUpdated) engines.updateYtDlp()
            showProgress(getString(R.string.downloading, source), null)
            failure = runEngines(url, media, cookies) { percent ->
                showProgress(getString(R.string.downloading, source), percent)
            }
            val published = Publisher(this).publish(media, source, engines::throwIfCanceled)
            if (published.anyFailed && failure == null) failure = Failure.UNKNOWN
            return if (published.files.isEmpty()) Outcome.Failed(failure ?: Failure.NO_MEDIA)
            else Outcome.Saved(published.files, failure)
        } catch (_: Canceled) {
            return Outcome.Canceled
        } catch (e: Exception) {
            debugSave(e.toString())
            return Outcome.Failed(failure ?: Failure.UNKNOWN)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Returns the failure of the last engine tried, or null. Files may exist despite a failure. */
    private fun runEngines(url: String, media: File, cookies: File?, onProgress: (Int) -> Unit): Failure? {
        if (UrlRules.engine(url) == Engine.GALLERY_DL) {
            val failure = attempt { engines.galleryDl(url, media, cookies) }
            val empty = media.walkTopDown().none { it.isFile }
            // gallery-dl exits cleanly with no files when it finds nothing, so yt-dlp still gets a try.
            val retry = empty && (failure == null || failure.triesOtherEngine) && UrlRules.source(url) != "instagram"
            return if (retry) attempt { engines.ytDlp(url, media, cookies, onProgress) } else failure
        }
        val failure = attempt { engines.ytDlp(url, media, cookies, onProgress) } ?: return null
        if (!failure.triesOtherEngine) return failure
        return attempt { engines.galleryDl(url, media, cookies) }
    }

    private fun attempt(run: () -> Unit): Failure? = try {
        run()
        null
    } catch (e: EngineError) {
        debugSave(e.stderr)
        Failure.classify(e.stderr)
    }

    /** Raw engine output stays out of logs and out of release builds. */
    private fun debugSave(text: String) {
        if (BuildConfig.DEBUG) File(filesDir, "last_error.txt").writeText(text.take(16_000))
    }

    private fun report(url: String, outcome: Outcome) {
        // The service restarts between links, so result ids must not depend on its lifetime.
        val id = (System.currentTimeMillis() and 0x3FFFFFFF).toInt() + RESULT_ID_BASE
        val notification = when (outcome) {
            Outcome.Canceled -> return
            is Outcome.Saved -> resultNotification(
                if (outcome.files.size == 1) getString(R.string.saved_one)
                else getString(R.string.saved_many, outcome.files.size),
                outcome.partial?.let { getString(R.string.saved_partial) } ?: url,
                savedIntent(outcome.files),
                id,
            )
            is Outcome.Failed -> resultNotification(
                getString(R.string.failed),
                failureText(url, outcome.failure),
                if (canLogIn(url, outcome.failure)) Intent(this, LoginActivity::class.java) else Intent(this, MainActivity::class.java),
                id,
            )
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) {
            manager.notify(id, notification)
        } else {
            val text = when (outcome) {
                is Outcome.Failed -> failureText(url, outcome.failure)
                else -> getString(R.string.saved_one)
            }
            main.post { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }
        }
    }

    private fun canLogIn(url: String, failure: Failure) = failure == Failure.LOGIN && UrlRules.source(url) == "instagram"

    private fun failureText(url: String, failure: Failure): String = when {
        failure == Failure.LOGIN && !canLogIn(url, failure) -> getString(R.string.err_login_other)
        else -> getString(failure.message)
    }

    private fun savedIntent(files: List<Publisher.Saved>): Intent {
        val single = files.singleOrNull() ?: return Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(single.uri, single.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun showProgress(text: String, percent: Int?) {
        val now = SystemClock.elapsedRealtime()
        if (percent != null && now - lastProgressAt < PROGRESS_INTERVAL_MS) return
        lastProgressAt = now
        getSystemService(NotificationManager::class.java).notify(PROGRESS_ID, progressNotification(text, percent))
    }

    private fun progressNotification(text: String, percent: Int?): Notification {
        val cancel = PendingIntent.getService(
            this, 0, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        val known = percent != null && percent in 0..100
        return Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setContentText(if (known) "$percent%" else getString(R.string.preparing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (known) percent!! else 0, !known)
            .addAction(Notification.Action.Builder(null, getString(R.string.cancel), cancel).build())
            .build()
    }

    private fun resultNotification(title: String, text: String, intent: Intent, id: Int): Notification {
        val open = PendingIntent.getActivity(
            this, id, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        private const val ACTION_CANCEL = "cancel"
        private const val EXTRA_URL = "url"
        private const val CHANNEL_PROGRESS = "progress"
        private const val CHANNEL_RESULT = "result"
        private const val PROGRESS_ID = 1
        private const val RESULT_ID_BASE = 100
        private const val PROGRESS_INTERVAL_MS = 1000L
        private const val WAKE_LOCK_MS = 2 * 3600 * 1000L

        fun start(context: Context, url: String) {
            context.startForegroundService(Intent(context, DownloadService::class.java).putExtra(EXTRA_URL, url))
        }
    }
}
