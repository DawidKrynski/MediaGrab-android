package io.github.dawidkrynski.mediagrab

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.notifications_button).setOnClickListener { requestNotifications() }
        findViewById<Button>(R.id.go).setOnClickListener {
            val field = findViewById<EditText>(R.id.url)
            Toast.makeText(this, Links.startDownload(this, field.text.toString()), Toast.LENGTH_SHORT).show()
            field.text.clear()
        }
        if (savedInstanceState == null) requestNotifications()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun refresh() {
        val enabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val visibility = if (enabled) View.GONE else View.VISIBLE
        findViewById<View>(R.id.notifications_text).visibility = visibility
        findViewById<View>(R.id.notifications_button).visibility = visibility

        val session = InstagramSession(this)
        findViewById<TextView>(R.id.instagram_state)
            .setText(if (session.isLoggedIn) R.string.instagram_in else R.string.instagram_out)
        findViewById<Button>(R.id.instagram_button).apply {
            setText(if (session.isLoggedIn) R.string.instagram_logout else R.string.instagram_login)
            setOnClickListener {
                if (session.isLoggedIn) {
                    session.clear()
                    CookieManager.getInstance().removeAllCookies(null)
                    refresh()
                } else {
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                }
            }
        }
    }
}
