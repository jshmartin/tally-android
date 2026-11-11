package com.example.tally.capture

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether the user has let Tally read notifications, and how to send them to the switch. */
object NotificationAccess {
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens Tally's own Notification access switch, or the list of apps where that page isn't available. */
    fun openSettings(context: Context) {
        val component = ComponentName(context, PurchaseNotificationListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (detail == null || !start(context, detail)) start(context, list)
    }

    /** App info is where Android 13+ hides "Allow restricted settings" for apps installed outside a store. */
    fun openAppInfo(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Whether notification access is on, checked again each time the screen comes back from Settings. */
@Composable
fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = NotificationAccess.isEnabled(context)
        onPauseOrDispose { }
    }
    return enabled
}
