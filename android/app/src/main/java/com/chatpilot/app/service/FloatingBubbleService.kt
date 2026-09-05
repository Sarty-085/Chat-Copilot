package com.chatpilot.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.chatpilot.app.data.ApiClient
import com.chatpilot.app.data.MessageTurn
import com.chatpilot.app.data.RemoteInputHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingBubbleService : Service() {

    companion object {
        const val ACTION_NEW_MESSAGE = "com.chatpilot.app.ACTION_NEW_MESSAGE"
        const val ACTION_DISMISS = "com.chatpilot.app.ACTION_DISMISS"
        const val EXTRA_CONTACT = "extra_contact"
        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_MESSAGE = "extra_message"
        private const val CHANNEL_ID = "chatpilot_bubble_channel"
        private const val NOTIFICATION_ID = 9001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isExpanded = false

    private var currentContact = ""
    private var currentPlatform = ""
    private var currentMessage = ""

    private val apiClient by lazy {
        val prefs = getSharedPreferences("chatpilot_prefs", Context.MODE_PRIVATE)
        val serverIp = prefs.getString("server_ip", "192.168.1.2") ?: "192.168.1.2"
        val serverPort = prefs.getString("server_port", "8000") ?: "8000"
        ApiClient { "http://$serverIp:$serverPort" }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification("ChatPilot reply bubble active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NEW_MESSAGE -> {
                currentContact = intent.getStringExtra(EXTRA_CONTACT) ?: "Contact"
                currentPlatform = intent.getStringExtra(EXTRA_PLATFORM) ?: "whatsapp"
                currentMessage = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                showBubbleOverlay()
            }
            ACTION_DISMISS -> {
                removeOverlay()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showBubbleOverlay() {
        if (overlayView != null) {
            removeOverlay()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }

        // Programmatically build modern tactile dark-mode overlay container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF0F172A.toInt()) // Slate-900
            elevation = 16f
        }

        // Header with Contact Name & Platform badge
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(this).apply {
            text = "$currentContact ($currentPlatform)"
            setTextColor(0xFFF8FAFC.toInt())
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 14f
            setBackgroundColor(0x00000000)
            setOnClickListener { removeOverlay() }
        }
        headerLayout.addView(titleView)
        headerLayout.addView(closeBtn)
        container.addView(headerLayout)

        // Incoming message quote
        val quoteView = TextView(this).apply {
            text = "\"$currentMessage\""
            setTextColor(0xFFCBD5E1.toInt())
            textSize = 13f
            setPadding(0, 10, 0, 16)
        }
        container.addView(quoteView)

        // Suggestions container
        val suggestionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val loadingView = TextView(this).apply {
            text = "⚡ Generating replies via laptop LLM..."
            setTextColor(0xFF6366F1.toInt()) // Electric indigo
            textSize = 12f
        }
        suggestionsLayout.addView(loadingView)
        container.addView(suggestionsLayout)

        overlayView = container
        windowManager.addView(container, params)

        // Fetch AI suggestions in background
        serviceScope.launch {
            val turns = listOf(MessageTurn(speaker = currentContact, text = currentMessage, isUser = false))
            val res = apiClient.fetchSuggestions(currentContact, currentPlatform, turns)

            withContext(Dispatchers.Main) {
                suggestionsLayout.removeAllViews()
                if (res.isSuccess) {
                    val suggestions = res.getOrNull()?.suggestions ?: emptyList()
                    for (suggestion in suggestions) {
                        val chip = Button(this@FloatingBubbleService).apply {
                            text = "💬 $suggestion"
                            setTextColor(0xFFFFFFFF.toInt())
                            setBackgroundColor(0xFF1E293B.toInt()) // Slate-800
                            textSize = 13f
                            isAllCaps = false
                            setPadding(20, 14, 20, 14)
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 8, 0, 8) }
                            layoutParams = lp

                            setOnClickListener {
                                onSuggestionTapped(suggestion)
                            }
                        }
                        suggestionsLayout.addView(chip)
                    }
                } else {
                    val errorView = TextView(this@FloatingBubbleService).apply {
                        text = "Unable to connect to laptop server. Check IP in Settings."
                        setTextColor(0xFFEF4444.toInt())
                        textSize = 12f
                    }
                    suggestionsLayout.addView(errorView)
                }
            }
        }
    }

    private fun onSuggestionTapped(chosenText: String) {
        val replyAction = ReplyNotificationListenerService.activeReplyActions[currentContact.lowercase()]
        if (replyAction != null) {
            val filled = RemoteInputHelper.populateReply(this, replyAction, chosenText)
            if (filled) {
                // Remove overlay after filling
                removeOverlay()
            }
        } else {
            removeOverlay()
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might already be removed
            }
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChatPilot Bubble Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatPilot Reply Assistant")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
