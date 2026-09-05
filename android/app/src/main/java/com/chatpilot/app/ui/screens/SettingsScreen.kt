package com.chatpilot.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatpilot.app.data.ApiClient
import com.chatpilot.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("chatpilot_prefs", Context.MODE_PRIVATE)

    var backendMode by remember { mutableStateOf(prefs.getString("backend_mode", "local") ?: "local") }
    var serverIp by remember { mutableStateOf(prefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100") }
    var serverPort by remember { mutableStateOf(prefs.getString("server_port", "8000") ?: "8000") }
    var localModel by remember { mutableStateOf(prefs.getString("local_model", "llama3.2:3b") ?: "llama3.2:3b") }

    var cloudProvider by remember { mutableStateOf(prefs.getString("cloud_provider", "gemini") ?: "gemini") }
    var cloudApiKey by remember { mutableStateOf(prefs.getString("cloud_api_key", "") ?: "") }

    var pingResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Inference & Connectivity",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Segmented Switcher: Local vs Cloud
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("local" to "Local (Laptop)", "cloud" to "Cloud Fallback").forEach { (mode, label) ->
                val isSelected = backendMode == mode
                Button(
                    onClick = { backendMode = mode },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) PrimaryIndigo else SurfaceContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if (backendMode == "local") {
            // Local Laptop Settings Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Computer, contentDescription = null, tint = PrimaryIndigo)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Laptop Inference Server", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("Laptop IP on Wi-Fi (e.g. 192.168.x.x)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Server Port") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = localModel,
                        onValueChange = { localModel = it },
                        label = { Text("Ollama Model Tag") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                pingResult = "Pinging http://$serverIp:$serverPort/v1/health..."
                                val client = ApiClient { "http://$serverIp:$serverPort" }
                                val res = client.checkHealth()
                                pingResult = if (res.isSuccess) {
                                    val health = res.getOrNull()
                                    "✓ Connected! Model: ${health?.localModel} (Ollama status: ${health?.status})"
                                } else {
                                    "✗ Failed: ${res.exceptionOrNull()?.message}"
                                }
                                isTesting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isTesting
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTesting) "Testing Ping..." else "Test Connection (Ping)")
                    }

                    if (pingResult != null) {
                        Text(
                            text = pingResult ?: "",
                            fontSize = 12.sp,
                            color = if (pingResult?.startsWith("✓") == true) AccentEmerald else AccentCoral
                        )
                    }
                }
            }
        } else {
            // Cloud Fallback Settings Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = AccentEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Cloud Fallback Provider", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    // Provider dropdown or radios
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gemini", "openai", "anthropic").forEach { prov ->
                            val isSel = cloudProvider == prov
                            FilterChip(
                                selected = isSel,
                                onClick = { cloudProvider = prov },
                                label = { Text(prov.capitalize()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryIndigo,
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = cloudApiKey,
                        onValueChange = { cloudApiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Save Settings Action
        Button(
            onClick = {
                prefs.edit().apply {
                    putString("backend_mode", backendMode)
                    putString("server_ip", serverIp)
                    putString("server_port", serverPort)
                    putString("local_model", localModel)
                    putString("cloud_provider", cloudProvider)
                    putString("cloud_api_key", cloudApiKey)
                    apply()
                }
                saveMessage = "Settings saved successfully!"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Preferences")
        }

        if (saveMessage != null) {
            Text(text = saveMessage ?: "", color = AccentEmerald, fontSize = 13.sp)
        }
    }
}
