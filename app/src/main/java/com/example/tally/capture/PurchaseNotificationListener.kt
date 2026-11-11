package com.example.tally.capture

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.example.tally.TallyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hands every new notification to [PurchaseRecorder]. Android binds this service once the user turns
 * on Tally under Settings > Notification access, and keeps it running from then on.
 *
 * Only notifications posted while access is on are seen. Notifications already in the shade when
 * access is granted are left alone.
 */
class PurchaseNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        // Summaries repeat their children; ongoing ones are timers, music and downloads.
        val skip = Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE
        if ((notification.flags and skip) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val captured = CapturedNotification(
            packageName = sbn.packageName,
            appLabel = appLabel(sbn),
            key = sbn.key,
            title = title,
            text = text,
            isMessage = notification.category == Notification.CATEGORY_MESSAGE ||
                extras.containsKey(Notification.EXTRA_MESSAGES),
            postedAt = sbn.postTime,
        )
        val recorder = (application as TallyApp).recorder
        scope.launch {
            try {
                recorder.record(captured)
            } catch (e: Exception) {
                // The notification's text stays out of the log.
                Log.w(TAG, "Couldn't record a notification from ${captured.packageName}", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun appLabel(sbn: StatusBarNotification): String {
        // Android attaches the posting app's info to every notification.
        val info = BundleCompat.getParcelable(sbn.notification.extras, "android.appInfo", ApplicationInfo::class.java)
            ?: runCatching { packageManager.getApplicationInfo(sbn.packageName, 0) }.getOrNull()
        return info?.let { runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull() }
            ?: sbn.packageName
    }

    private companion object {
        const val TAG = "TallyCapture"
    }
}
