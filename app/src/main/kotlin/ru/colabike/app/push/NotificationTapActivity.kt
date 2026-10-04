package ru.colabike.app.push

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ru.colabike.app.ColaBikeApplication
import ru.colabike.app.MainActivity

/**
 * Where a tap on a notification lands, and nothing more: it has no screen, is not exported (only
 * the app's own notification can start it), reads the tap back with every field checked, hands the
 * destination to the app and goes. The app's exported activity reads no part of the notification,
 * so another app can forge nothing through it.
 */
class NotificationTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PushTap.from(intent)?.let {
            (application as ColaBikeApplication).graph.pushOpener.opened(it)
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
