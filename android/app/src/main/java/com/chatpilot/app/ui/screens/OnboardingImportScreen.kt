package com.chatpilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatpilot.app.ui.theme.*

@Composable
fun OnboardingImportScreen() {
    var selectedPlatform by remember { mutableStateOf("WhatsApp") }
    var importStatus by remember { mutableStateOf("No export imported yet") }
    var parsedCount by remember { mutableStateOf<String?>(null) }
    var isSimulatingImport by remember { mutableStateOf(false) }

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
            text = "ChatPilot analyzes how you genuinely speak to specific people and builds per-contact style profiles without fine-tuning.",
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
                    onClick = { selectedPlatform = platform },
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
                        "Export without media from WhatsApp -> Chat -> More -> Export Chat"
                    else
                        "Export JSON thread from Meta Accounts Center -> Download Information",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                Button(
                    onClick = {
                        isSimulatingImport = true
                        importStatus = "Parsing messages & computing style profile..."
                        // Emulated local import demonstration
                        parsedCount = "Imported 1,420 messages (Alex: 712 replies) • Style Profile Generated!"
                        importStatus = "Completed"
                        isSimulatingImport = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Export File")
                }
            }
        }

        // Parsing status details
        if (parsedCount != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "✓ Import Successful", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = parsedCount ?: "", color = TextPrimary, fontSize = 13.sp)
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
                    text = "100% Private & Local. Chat exports are parsed and stored encrypted on your laptop. No data is sent to cloud servers unless cloud fallback is toggled.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
