package io.github.dawidkrynski.mediagrab

import android.content.Context

object Links {
    /** Starts a download when the text holds a single-post link; returns the message to show. */
    fun startDownload(context: Context, text: String?): Int {
        val url = UrlRules.extractUrl(text) ?: return R.string.no_link
        if (UrlRules.isCollection(url)) return R.string.collection
        DownloadService.start(context, url)
        return R.string.starting
    }
}
