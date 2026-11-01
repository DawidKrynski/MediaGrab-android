package io.github.dawidkrynski.mediagrab

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/** The user signs in on Instagram's own page. The app only keeps the resulting session cookies. */
class LoginActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.fitsSystemWindows = true
        setContentView(web)
        CookieManager.getInstance().setAcceptCookie(true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = saveIfLoggedIn()

            // Instagram often changes pages without a full load.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = saveIfLoggedIn()
        }
        web.loadUrl(LOGIN_URL)
    }

    private fun saveIfLoggedIn() {
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_URL) ?: return
        if (InstagramSession(this).save(cookies)) {
            CookieManager.getInstance().flush()
            Toast.makeText(this, R.string.login_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val INSTAGRAM_URL = "https://www.instagram.com/"
        const val LOGIN_URL = "https://www.instagram.com/accounts/login/"
    }
}
