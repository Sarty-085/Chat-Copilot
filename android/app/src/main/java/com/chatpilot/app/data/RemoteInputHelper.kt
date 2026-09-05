package com.chatpilot.app.data

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

data class ReplyActionContext(
    val notificationKey: String,
    val contactName: String,
    val platform: String,
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val incomingMessage: String
)

object RemoteInputHelper {
    private const val TAG = "RemoteInputHelper"

    /**
     * Injects the chosen reply suggestion into the intercepted notification's RemoteInput.
     * Crucially: This sends the reply input to the messaging app's pending intent handler.
     * The messaging app then accepts the text for the reply thread.
     * Tap-to-send guarantee: This is triggered ONLY by user explicit tap.
     */
    fun populateReply(context: Context, replyAction: ReplyActionContext, replyText: String): Boolean {
        return try {
            val fillIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(replyAction.remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(replyAction.remoteInput), fillIntent, bundle)

            replyAction.pendingIntent.send(context, 0, fillIntent)
            Log.d(TAG, "Successfully populated RemoteInput for ${replyAction.contactName} with text: '$replyText'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send RemoteInput reply intent", e)
            false
        }
    }

    /**
     * Extracts quick-reply Action from an incoming notification.
     */
    fun findReplyAction(notification: Notification, key: String, contactName: String, platform: String, incomingText: String): ReplyActionContext? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                // Check if this action accepts text replies
                if (remoteInput.resultKey != null) {
                    val pendingIntent = action.actionIntent ?: continue
                    return ReplyActionContext(
                        notificationKey = key,
                        contactName = contactName,
                        platform = platform,
                        pendingIntent = pendingIntent,
                        remoteInput = remoteInput,
                        incomingMessage = incomingText
                    )
                }
            }
        }
        return null
    }
}
