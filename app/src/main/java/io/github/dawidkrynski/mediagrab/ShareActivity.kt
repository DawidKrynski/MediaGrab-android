package io.github.dawidkrynski.mediagrab

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible share target: a shared link starts downloading straight away. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = Links.startDownload(this, intent.getStringExtra(Intent.EXTRA_TEXT))
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
