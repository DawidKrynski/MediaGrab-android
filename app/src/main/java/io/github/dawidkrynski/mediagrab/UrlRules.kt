package io.github.dawidkrynski.mediagrab

import java.net.URI

enum class Engine { YT_DLP, GALLERY_DL }

object UrlRules {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val INSTAGRAM_POST = Regex("""^/(?:[^/]+/)?p/[^/]+/?$""")
    private val INSTAGRAM_SINGLE = Regex("""^/(?:[^/]+/)?(?:p|reels?|tv)/[^/]+/?$|^/stories/[^/]+/[^/]+/?$""")
    private val YOUTUBE_COLLECTION = Regex("""^/(?:playlist|channel/|c/|user/|@)""")
    private val TIKTOK_SINGLE = Regex("""/(?:video|photo)/""")
    private val X_SINGLE = Regex("""/status(?:es)?/""")
    private val REDDIT_SINGLE = Regex("""/comments/|^/[^/]+$|^/r/[^/]+/s/""")
    private val YOUTUBE_LIST_PARAMS = setOf("list", "index", "start_radio")
    private val SOURCES = listOf(
        "youtube.com" to "youtube",
        "youtu.be" to "youtube",
        "instagram.com" to "instagram",
        "twitter.com" to "x",
        "x.com" to "x",
        "tiktok.com" to "tiktok",
        "reddit.com" to "reddit",
        "redd.it" to "reddit",
        "facebook.com" to "facebook",
        "fb.watch" to "facebook",
    )

    /** First complete HTTP(S) URL in shared text, or null. Apps often wrap the link in a sentence. */
    fun extractUrl(text: String?): String? {
        val raw = URL.find(text ?: return null)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']') ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.host.isNullOrEmpty() || uri.userInfo != null) return null
        return normalize(raw)
    }

    private fun host(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty().lowercase()

    private fun path(url: String): String = runCatching { URI(url).path }.getOrNull().orEmpty()

    fun source(url: String): String {
        val host = host(url)
        SOURCES.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.let { return it.second }
        return host.removePrefix("www.").replace(Regex("[^a-z0-9-]"), "-").take(64).ifEmpty { "unknown" }
    }

    /** A watch URL with a list parameter still means just the requested video. */
    private fun normalize(url: String): String {
        if (source(url) != "youtube" || !url.contains('?')) return url
        val (base, query) = url.split('?', limit = 2)
        val params = query.split('&')
        if (params.none { it.startsWith("v=") }) return url
        return base + "?" + params.filterNot { it.substringBefore('=') in YOUTUBE_LIST_PARAMS }.joinToString("&")
    }

    /** Whole profiles and playlists are never downloaded implicitly. */
    fun isCollection(url: String): Boolean {
        val path = path(url)
        return when (source(url)) {
            "youtube" -> YOUTUBE_COLLECTION.containsMatchIn(path)
            "instagram" -> !INSTAGRAM_SINGLE.containsMatchIn(path)
            "tiktok" -> path.startsWith("/@") && !TIKTOK_SINGLE.containsMatchIn(path)
            "x" -> !X_SINGLE.containsMatchIn(path)
            "reddit" -> host(url) != "redd.it" && !REDDIT_SINGLE.containsMatchIn(path)
            else -> false
        }
    }

    /** Keep /p/ with gallery-dl so carousels retain images. */
    fun engine(url: String): Engine =
        if (source(url) == "instagram" && INSTAGRAM_POST.containsMatchIn(path(url))) Engine.GALLERY_DL else Engine.YT_DLP
}
