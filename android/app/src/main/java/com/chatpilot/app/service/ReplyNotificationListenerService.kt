package com.chatpilot.app.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chatpilot.app.data.RemoteInputHelper
import com.chatpilot.app.data.ReplyActionContext

class ReplyNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ReplyListenerService"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val INSTAGRAM_PKG = "com.instagram.android"

        // Active notification reply cache: contactName -> ReplyActionContext
        val activeReplyActions = mutableMapOf<String, ReplyActionContext>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg != WHATSAPP_PKG && pkg != INSTAGRAM_PKG) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract title (usually sender or group)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        // Extract text
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() || text.isBlank()) {
            return
        }

        // Avoid self-sent or group status notifications
        if (text.contains("new messages", ignoreCase = true) || text.contains("Checking for new messages", ignoreCase = true)) {
            return
        }

        val platform = if (pkg == WHATSAPP_PKG) "whatsapp" else "instagram"
        val contactName = title.trim()

        Log.d(TAG, "[$platform] Received message from $contactName: $text")

        // Find RemoteInput reply action
        val replyAction = RemoteInputHelper.findReplyAction(
            notification = notification,
            key = sbn.key,
            contactName = contactName,
            platform = platform,
            incomingText = text
        )

        if (replyAction != null) {
            activeReplyActions[contactName.lowercase()] = replyAction

            // Broadcast or start FloatingBubbleService
            val bubbleIntent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_NEW_MESSAGE
                putExtra(FloatingBubbleService.EXTRA_CONTACT, contactName)
                putExtra(FloatingBubbleService.EXTRA_PLATFORM, platform)
                putExtra(FloatingBubbleService.EXTRA_MESSAGE, text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(bubbleIntent)
            } else {
                startService(bubbleIntent)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) {
            activeReplyActions.values.removeAll { it.notificationKey == sbn.key }
        }
    }
}
