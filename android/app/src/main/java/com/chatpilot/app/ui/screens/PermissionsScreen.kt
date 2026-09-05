package com.chatpilot.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatpilot.app.data.ApiClient
import com.chatpilot.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var networkStatus by remember { mutableStateOf("Ready to test") }
    var isCheckingNetwork by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("chatpilot_prefs", Context.MODE_PRIVATE)
    val serverIp = prefs.getString("server_ip", "10.0.2.2") ?: "10.0.2.2"
    val serverPort = prefs.getString("server_port", "8000") ?: "8000"
    val apiClient = remember { ApiClient { "http://$serverIp:$serverPort" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Setup & Permissions",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "ChatPilot needs 2 core permissions to intercept incoming messages and display quick-reply suggestion chips.",
            fontSize = 14.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        // 1. Notification Access
        PermissionCard(
            title = "1. Notification Access",
            description = "Allows ChatPilot to read incoming message context from WhatsApp and Instagram and extract the reply input hook.",
            icon = Icons.Default.Notifications,
            isGranted = hasNotificationAccess,
            buttonLabel = if (hasNotificationAccess) "Granted" else "Enable Access",
            onAction = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )

        // 2. Display Over Other Apps
        PermissionCard(
            title = "2. Display Over Other Apps",
            description = "Enables the floating quick-reply overlay bubble to appear above WhatsApp and Instagram when a message arrives.",
            icon = Icons.Default.Layers,
            isGranted = hasOverlayPermission,
            buttonLabel = if (hasOverlayPermission) "Granted" else "Allow Overlay",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        )

        // 3. Local Laptop Connection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "3. Laptop Server Connectivity",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Ensure your phone is on the same Wi-Fi network as your laptop running the ChatPilot FastAPI server ($serverIp:$serverPort).",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Status: $networkStatus",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (networkStatus.contains("Healthy", ignoreCase = true)) AccentEmerald else PrimaryLight
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isCheckingNetwork = true
                            networkStatus = "Pinging $serverIp:$serverPort..."
                            val res = apiClient.checkHealth()
                            networkStatus = if (res.isSuccess) {
                                "Healthy! Model: ${res.getOrNull()?.localModel}"
                            } else {
                                "Failed to connect: ${res.exceptionOrNull()?.message}"
                            }
                            isCheckingNetwork = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isCheckingNetwork
                ) {
                    Text(if (isCheckingNetwork) "Testing..." else "Test Connection")
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (isGranted) AccentEmerald.copy(alpha = 0.5f) else OutlineBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) AccentEmerald else PrimaryIndigo,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Text(
                text = description,
                fontSize = 13.sp,
                color = TextSecondary
            )
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) AccentEmerald else PrimaryIndigo
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isGranted) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(buttonLabel)
            }
        }
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}
