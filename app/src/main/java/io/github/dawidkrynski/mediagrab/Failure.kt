package io.github.dawidkrynski.mediagrab

enum class Failure(val message: Int) {
    RATE_LIMIT(R.string.err_rate),
    ACCESS(R.string.err_access),
    LOGIN(R.string.err_login),
    UNSUPPORTED(R.string.err_unsupported),
    NO_MEDIA(R.string.err_no_media),
    NETWORK(R.string.err_network),
    UNAVAILABLE(R.string.err_unavailable),
    UNKNOWN(R.string.err_unknown);

    /** yt-dlp handles video only, so these may still be images gallery-dl can fetch. */
    val triesOtherEngine: Boolean get() = this == UNSUPPORTED || this == NO_MEDIA

    companion object {
        private val RULES = listOf(
            RATE_LIMIT to "429|rate.?limit|too many requests",
            // Bot checks and Instagram's empty response mention cookies or signing in, but are access errors.
            ACCESS to "not a bot|empty media response|403|forbidden|blocked|challenge",
            LOGIN to "login|log in|logged.in|sign in|authentication|authorization|authrequired|credentials|private",
            UNSUPPORTED to "unsupported url|no suitable extractor|noextractorerror",
            NO_MEDIA to "no video could be found|there is no video|no video formats|no media found|nothing to download",
            NETWORK to "timed out|temporary failure|network is unreachable|connectionerror|getaddrinfo|unable to download webpage|connection reset",
            UNAVAILABLE to "unavailable|has been removed|deleted|404|not found",
        ).map { (failure, pattern) -> failure to Regex(pattern, RegexOption.IGNORE_CASE) }

        private val ERROR_LINE = Regex("""^ERROR:|\[error\]""", RegexOption.IGNORE_CASE)

        /** Terminal errors take precedence over warnings, which often mention unrelated problems. */
        fun classify(stderr: String): Failure {
            val errors = stderr.lines().filter { ERROR_LINE.containsMatchIn(it) }
            val text = if (errors.isEmpty()) stderr else errors.joinToString("\n")
            return RULES.firstOrNull { (_, pattern) -> pattern.containsMatchIn(text) }?.first ?: UNKNOWN
        }
    }
}
