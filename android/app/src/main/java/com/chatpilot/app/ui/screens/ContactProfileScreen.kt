package com.chatpilot.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import com.chatpilot.app.data.ContactProfile
import com.chatpilot.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ContactProfileScreen(initialContactName: String? = null) {
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

    var profiles by remember { mutableStateOf<List<ContactProfile>>(emptyList()) }
    var selectedContactName by remember { mutableStateOf<String?>(initialContactName) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var saveStatus by remember { mutableStateOf<String?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(initialContactName) {
        if (!initialContactName.isNullOrBlank()) {
            selectedContactName = initialContactName
        }
    }

    // Editable states for selected profile
    var formalityScore by remember { mutableFloatStateOf(0.3f) }
    var customNotes by remember { mutableStateOf("") }

    val selectedProfile = profiles.find { it.contactName.equals(selectedContactName, ignoreCase = true) }
        ?: profiles.firstOrNull()

    // Sync editable fields when selected profile changes
    LaunchedEffect(selectedProfile?.contactName) {
        selectedProfile?.let {
            formalityScore = it.formalityScore
            customNotes = it.customToneNotes
            saveStatus = null
        }
    }

    fun loadProfiles() {
        coroutineScope.launch {
            isLoading = true
            fetchError = null
            saveStatus = null
            val res = apiClient.fetchProfiles()
            if (res.isSuccess) {
                val list = res.getOrNull() ?: emptyList()
                profiles = list
                if (selectedContactName == null || list.none { it.contactName == selectedContactName }) {
                    selectedContactName = list.firstOrNull()?.contactName
                }
            } else {
                fetchError = res.exceptionOrNull()?.message ?: "Failed to load profiles from laptop."
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadProfiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Contact Style Profiles",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (selectedProfile != null) "Speaking to ${selectedProfile.contactName}" else "Per-user style DNA & custom instructions",
                    fontSize = 14.sp,
                    color = PrimaryLight
                )
            }
            IconButton(
                onClick = { loadProfiles() },
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Profiles",
                    tint = PrimaryIndigo
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        }

        // Error message card
        if (fetchError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, AccentCoral.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Connection Error", color = AccentCoral, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = fetchError ?: "", color = TextSecondary, fontSize = 13.sp)
                    Button(
                        onClick = { loadProfiles() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry Connection")
                    }
                }
            }
        }

        // Empty state when no profiles exist
        if (!isLoading && fetchError == null && profiles.isEmpty()) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "No Contact Profiles Yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Import a real WhatsApp (.txt) or Instagram (.json) export in the Import tab. ChatPilot will automatically analyze your chat history, compute Style DNA, and add a contact profile here.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Dropdown & Contact Selector
        if (profiles.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, OutlineBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select Contact Profile",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    // Dropdown selector button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainerHigh)
                                .border(1.dp, PrimaryIndigo.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { isDropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryIndigo),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (selectedProfile?.contactName?.take(1) ?: "?").uppercase(),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = selectedProfile?.contactName ?: "Choose Contact",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${selectedProfile?.totalMessagesAnalyzed ?: 0} messages analyzed",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Expand dropdown",
                                tint = TextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.background(SurfaceContainerHigh)
                        ) {
                            profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = p.contactName,
                                                fontWeight = if (p.contactName == selectedContactName) FontWeight.Bold else FontWeight.Normal,
                                                color = if (p.contactName == selectedContactName) PrimaryLight else TextPrimary
                                            )
                                            Text(
                                                text = "${p.totalMessagesAnalyzed} messages",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedContactName = p.contactName
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Horizontal chips row for quick switching
                    if (profiles.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            profiles.forEach { p ->
                                val isSelected = p.contactName.equals(selectedProfile?.contactName, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedContactName = p.contactName },
                                    label = { Text(p.contactName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryIndigo,
                                        selectedLabelColor = TextPrimary,
                                        containerColor = SurfaceContainerHigh,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Profile Details Card
        if (selectedProfile != null) {
            val p = selectedProfile

            // Style DNA Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Computed Style DNA (${p.contactName})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    // Formality Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Formality", fontSize = 13.sp, color = TextSecondary)
                            Text(
                                text = if (formalityScore < 0.4f) "Casual / Slang" else if (formalityScore > 0.7f) "Formal & Direct" else "Conversational",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLight
                            )
                        }
                        Slider(
                            value = formalityScore,
                            onValueChange = {
                                formalityScore = it
                                saveStatus = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryIndigo,
                                activeTrackColor = PrimaryIndigo,
                                inactiveTrackColor = OutlineBorder
                            )
                        )
                    }

                    HorizontalDivider(color = OutlineBorder.copy(alpha = 0.5f))

                    // Stats Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "Emoji Density", fontSize = 12.sp, color = TextSecondary)
                            val emojiRating = if (p.emojiDensity > 0.8f) "High" else if (p.emojiDensity > 0.2f) "Moderate" else "Low / Rare"
                            Text(
                                text = "$emojiRating (${String.format("%.1f", p.emojiDensity)}/msg)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val topEmojiText = if (p.topEmojis.isNotEmpty()) "Top: ${p.topEmojis.take(4).joinToString(" ")}" else "None"
                            Text(text = topEmojiText, fontSize = 12.sp, color = AccentEmerald)
                        }
                        Column {
                            Text(text = "Avg Reply Length", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = "${String.format("%.1f", p.avgWordsPerReply)} words",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(text = "~${p.avgCharsPerReply.toInt()} characters", fontSize = 12.sp, color = TextSecondary)
                        }
                        Column {
                            Text(text = "Capitalization", fontSize = 12.sp, color = TextSecondary)
                            val capPercent = (p.capitalizationRate * 100).toInt()
                            val capLabel = if (capPercent < 40) "Lowercase bias" else "Standard"
                            Text(text = "$capPercent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = capLabel, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // Top Catchphrases / Slang Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Top Phrases Used With ${p.contactName}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    val phrases = p.commonPhrases
                    if (phrases.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            phrases.forEach { phrase ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(SurfaceContainerHigh)
                                        .border(1.dp, OutlineBorder, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(text = phrase, fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No distinctive repetitive phrases found in chat export.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Editable Tone Override
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Custom Prompt Guidance for ${p.contactName}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = {
                            customNotes = it
                            saveStatus = null
                        },
                        placeholder = { Text("e.g. Keep replies ultra short, banter allowed, never use exclamation marks.") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSaving = true
                                saveStatus = null
                                p.formalityScore = formalityScore
                                p.customToneNotes = customNotes.trim()
                                val res = apiClient.updateProfile(p.contactName, p)
                                if (res.isSuccess) {
                                    saveStatus = "✓ Saved to Laptop SQLite!"
                                } else {
                                    saveStatus = "Failed: ${res.exceptionOrNull()?.message}"
                                }
                                isSaving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...")
                        } else {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Profile Adjustments")
                        }
                    }

                    if (saveStatus != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (saveStatus?.startsWith("✓") == true) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(saveStatus ?: "", color = AccentEmerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text(saveStatus ?: "", color = AccentCoral, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
