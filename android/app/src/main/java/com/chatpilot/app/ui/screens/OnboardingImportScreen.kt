package com.chatpilot.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatpilot.app.data.ApiClient
import com.chatpilot.app.data.ImportResponse
import com.chatpilot.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingImportScreen(onNavigateToProfile: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("chatpilot_prefs", Context.MODE_PRIVATE) }
    val apiClient = remember {
        ApiClient {
            val ip = prefs.getString("server_ip", "192.168.1.2") ?: "192.168.1.2"
            val port = prefs.getString("server_port", "8000") ?: "8000"
            "http://$ip:$port"
        }
    }

    var selectedPlatform by remember { mutableStateOf("WhatsApp") }
    var contactNameOverride by remember { mutableStateOf("") }
    var userNameOverride by remember { mutableStateOf("") }

    var isUploading by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<ImportResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleFileSelected(uri: Uri) {
        coroutineScope.launch {
            try {
                isUploading = true
                errorMessage = null
                importResult = null
                uploadStatusText = "Reading export file..."

                val fileName = getFileName(context, uri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    errorMessage = "Could not read selected file or file is empty."
                    isUploading = false
                    return@launch
                }

                uploadStatusText = "Uploading to laptop & computing Style DNA..."
                val contactParam = contactNameOverride.trim().ifEmpty { null }
                val userParam = userNameOverride.trim().ifEmpty { null }

                val res = if (selectedPlatform == "WhatsApp") {
                    apiClient.uploadWhatsAppExport(
                        fileBytes = bytes,
                        fileName = fileName.ifEmpty { "whatsapp_chat.txt" },
                        contactName = contactParam,
                        userName = userParam
                    )
                } else {
                    apiClient.uploadInstagramExport(
                        fileBytes = bytes,
                        fileName = fileName.ifEmpty { "messages.json" },
                        contactName = contactParam,
                        userName = userParam
                    )
                }

                if (res.isSuccess) {
                    importResult = res.getOrNull()
                    uploadStatusText = "Import complete!"
                } else {
                    errorMessage = res.exceptionOrNull()?.message ?: "Upload failed. Check laptop connection in Settings."
                }
            } catch (e: Exception) {
                errorMessage = "Error importing chat: ${e.localizedMessage}"
            } finally {
                isUploading = false
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleFileSelected(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Import Chat History",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Upload real chat exports to compute a personalized Style DNA and indexing for each contact on your laptop.",
            fontSize = 14.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        // Platform Segmented Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("WhatsApp", "Instagram").forEach { platform ->
                val isSelected = selectedPlatform == platform
                Button(
                    onClick = {
                        selectedPlatform = platform
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) PrimaryIndigo else SurfaceContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = platform,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Optional metadata card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, OutlineBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Optional Identity Tags", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                OutlinedTextField(
                    value = contactNameOverride,
                    onValueChange = { contactNameOverride = it },
                    label = { Text("Contact Name (leave blank to auto-detect)") },
                    modifier = Modifier.fillMaxWidth(),
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
                    value = userNameOverride,
                    onValueChange = { userNameOverride = it },
                    label = { Text("Your Name / Persona (leave blank for 'You')") },
                    modifier = Modifier.fillMaxWidth(),
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
        }

        // Upload Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = if (selectedPlatform == "WhatsApp") "Select WhatsApp Chat Export (.txt)" else "Select Instagram messages.json",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = if (selectedPlatform == "WhatsApp")
                        "Export without media: WhatsApp -> Contact info -> Export Chat -> Without Media."
                    else
                        "Export messages from Meta Accounts Center -> Your information and permissions -> Download your information (JSON format). Non-text posts/reels are automatically filtered.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = {
                        if (selectedPlatform == "WhatsApp") {
                            filePickerLauncher.launch("text/*")
                        } else {
                            filePickerLauncher.launch("*/*")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(uploadStatusText ?: "Processing...")
                    } else {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Export File")
                    }
                }
            }
        }

        // Error message card
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, AccentCoral.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = errorMessage ?: "", color = AccentCoral, fontSize = 13.sp)
                }
            }
        }

        // Success result details
        if (importResult != null) {
            val result = importResult!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import Successful!",
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "Contact Profile: ${result.contactName} (${result.platform})",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• Messages parsed: ${result.totalMessages}\n• Conversation turns: ${result.totalTurns}\n• RAG exchange pairs indexed: ${result.indexedExchangePairs}\n• Style DNA computed & stored in laptop SQLite.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    if (onNavigateToProfile != null) {
                        Button(
                            onClick = { onNavigateToProfile.invoke(result.contactName) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View ${result.contactName}'s Profile →", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Privacy Guarantee Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, OutlineBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AccentEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "100% Private & Local. Chat exports are processed on your laptop SQLite database. No data is sent to cloud servers unless cloud fallback is toggled.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result = ""
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index) ?: ""
                }
            }
        }
    }
    if (result.isEmpty()) {
        result = uri.path?.substringAfterLast('/') ?: "chat_export"
    }
    return result
}
