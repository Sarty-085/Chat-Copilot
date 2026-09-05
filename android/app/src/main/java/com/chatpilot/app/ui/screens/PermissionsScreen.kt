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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chatpilot.app.data.ApiClient
import com.chatpilot.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(onSetupCompleted: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("chatpilot_prefs", Context.MODE_PRIVATE) }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    var serverIp by remember { mutableStateOf(prefs.getString("server_ip", "192.168.1.2") ?: "192.168.1.2") }
    var serverPort by remember { mutableStateOf(prefs.getString("server_port", "8000") ?: "8000") }

    var networkStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingNetwork by remember { mutableStateOf(false) }
    var isServerReachable by remember { mutableStateOf(false) }

    // Re-check permissions when returning to app from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasNotificationAccess = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAllDone = hasNotificationAccess && hasOverlayPermission

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Setup & Connectivity",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Complete these 2 permissions and verify laptop connection. Once setup is done, this tab will automatically hide.",
            fontSize = 14.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        // All Completed Banner
        if (isAllDone) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AccentEmerald, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "All Essential Permissions Granted!", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(
                        text = "ChatPilot is ready to intercept messages and suggest on-brand replies. You can now start importing chat exports.",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    if (onSetupCompleted != null) {
                        Button(
                            onClick = onSetupCompleted,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done - Go to Import", color = SurfaceDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 1. Notification Access
        PermissionCard(
            title = "1. Notification Access",
            description = "Allows ChatPilot to read incoming message context from WhatsApp & Instagram and trigger quick replies.",
            icon = Icons.Default.Notifications,
            isGranted = hasNotificationAccess,
            buttonLabel = if (hasNotificationAccess) "Granted" else "Enable Notification Access",
            onAction = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )

        // 2. Display Over Other Apps
        PermissionCard(
            title = "2. Display Over Other Apps",
            description = "Enables the floating quick-reply overlay bubble to appear above WhatsApp & Instagram when a message arrives.",
            icon = Icons.Default.Layers,
            isGranted = hasOverlayPermission,
            buttonLabel = if (hasOverlayPermission) "Granted" else "Allow Overlay Permission",
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
                .border(1.dp, if (isServerReachable) AccentEmerald.copy(alpha = 0.5f) else OutlineBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isServerReachable) AccentEmerald else PrimaryIndigo,
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
                    text = "Ensure your phone is on the same Wi-Fi network as your laptop running the ChatPilot server.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                // Inline IP and Port configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("Laptop Wi-Fi IP") },
                        modifier = Modifier.weight(2f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isCheckingNetwork = true
                            networkStatus = "Pinging http://$serverIp:$serverPort/v1/health..."
                            // Save IP
                            prefs.edit().putString("server_ip", serverIp.trim()).putString("server_port", serverPort.trim()).apply()

                            val client = ApiClient { "http://${serverIp.trim()}:${serverPort.trim()}" }
                            val res = client.checkHealth()
                            if (res.isSuccess) {
                                isServerReachable = true
                                val health = res.getOrNull()
                                networkStatus = "✓ Connected! Model: ${health?.localModel} (Status: ${health?.status})"
                            } else {
                                isServerReachable = false
                                networkStatus = "✗ Connection failed: ${res.exceptionOrNull()?.message ?: "Check if server is running on laptop and phone is on same Wi-Fi."}"
                            }
                            isCheckingNetwork = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isCheckingNetwork
                ) {
                    if (isCheckingNetwork) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pinging Server...")
                    } else {
                        Text("Test & Save Connection")
                    }
                }

                if (networkStatus != null) {
                    Text(
                        text = networkStatus ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isServerReachable) AccentEmerald else AccentCoral
                    )
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

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}
