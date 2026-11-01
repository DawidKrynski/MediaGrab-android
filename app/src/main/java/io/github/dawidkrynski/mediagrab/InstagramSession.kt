package io.github.dawidkrynski.mediagrab

import android.content.Context
import java.io.File

/** Netscape cookies for Instagram, kept private. Used only for Instagram URLs and never logged. */
class InstagramSession(context: Context) {
    private val file = File(context.noBackupFilesDir, "instagram_cookies.txt")

    val isLoggedIn: Boolean get() = file.exists()

    fun save(cookieHeader: String): Boolean {
        val text = netscape(cookieHeader, System.currentTimeMillis() / 1000 + YEAR_SECONDS) ?: return false
        val temp = File(file.path + ".tmp")
        temp.setReadable(false, false)
        temp.writeText(text)
        return temp.renameTo(file)
    }

    fun clear() {
        file.delete()
    }

    /** Engines may rewrite a cookies file, so every job gets its own copy. */
    fun copyTo(target: File): File? = if (file.exists()) file.copyTo(target, overwrite = true) else null

    companion object {
        private const val YEAR_SECONDS = 365L * 24 * 3600
        private const val DOMAIN = ".instagram.com"

        /** Returns null without a session cookie: anything else is not a completed login. */
        fun netscape(cookieHeader: String, expiry: Long): String? {
            val pairs = cookieHeader.split(';').map { it.trim() }.filter { '=' in it }
            if (pairs.none { it.startsWith("sessionid=") && it.length > "sessionid=".length }) return null
            return pairs.joinToString("\n", "# Netscape HTTP Cookie File\n", "\n") { pair ->
                val (name, value) = pair.split('=', limit = 2)
                "$DOMAIN\tTRUE\t/\tTRUE\t$expiry\t$name\t$value"
            }
        }
    }
}
