package com.chatpilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
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
fun ContactProfileScreen() {
    var contactName by remember { mutableStateOf("Alex") }
    var formalityScore by remember { mutableFloatStateOf(0.25f) }
    var customNotes by remember { mutableStateOf("Keep replies ultra short, rarely capitalize first letter, banter allowed.") }
    var isSaved by remember { mutableStateOf(false) }

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
                    text = "Contact Style Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Speaking to $contactName",
                    fontSize = 14.sp,
                    color = PrimaryLight
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryIndigo),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Metrics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "Computed Style DNA", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                // Formality Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Formality", fontSize = 13.sp, color = TextSecondary)
                        Text(
                            text = if (formalityScore < 0.4f) "Casual / Slang" else if (formalityScore > 0.7f) "Formal" else "Conversational",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryLight
                        )
                    }
                    Slider(
                        value = formalityScore,
                        onValueChange = { formalityScore = it },
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryIndigo,
                            activeTrackColor = PrimaryIndigo,
                            inactiveTrackColor = OutlineBorder
                        )
                    )
                }

                Divider(color = OutlineBorder.copy(alpha = 0.5f))

                // Stats Grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "Emoji Density", fontSize = 12.sp, color = TextSecondary)
                        Text(text = "High (1.8/msg)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Top: 💀 😭 ☕", fontSize = 13.sp, color = AccentEmerald)
                    }
                    Column {
                        Text(text = "Avg Reply Length", fontSize = 12.sp, color = TextSecondary)
                        Text(text = "7.2 words", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "~36 characters", fontSize = 12.sp, color = TextSecondary)
                    }
                    Column {
                        Text(text = "Capitalization", fontSize = 12.sp, color = TextSecondary)
                        Text(text = "Rare (18%)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Lowercase bias", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }

        // Common Catchphrases / Slang Chips
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Top Catchphrases Used With $contactName", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("lmao", "nah totally", "bet", "sounds good", "yo").forEach { phrase ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceContainerHigh)
                                .border(1.dp, OutlineBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = phrase, fontSize = 12.sp, color = TextPrimary)
                        }
                    }
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
                    Text(text = "Custom Prompt Guidance (Editable)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                OutlinedTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
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
                    onClick = { isSaved = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSaved) "Saved to Laptop SQLite!" else "Save Profile Adjustments")
                }
            }
        }
    }
}
