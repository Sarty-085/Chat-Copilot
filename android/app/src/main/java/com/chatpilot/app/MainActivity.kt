package com.chatpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.chatpilot.app.ui.screens.ContactProfileScreen
import com.chatpilot.app.ui.screens.OnboardingImportScreen
import com.chatpilot.app.ui.screens.PermissionsScreen
import com.chatpilot.app.ui.screens.SettingsScreen
import com.chatpilot.app.ui.theme.ChatPilotTheme
import com.chatpilot.app.ui.theme.PrimaryIndigo
import com.chatpilot.app.ui.theme.SurfaceContainer
import com.chatpilot.app.ui.theme.TextPrimary
import com.chatpilot.app.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatPilotTheme {
                MainAppScaffold()
            }
        }
    }
}

@Composable
fun MainAppScaffold() {
    var currentTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        Triple("Import", Icons.Default.FileDownload, 0),
        Triple("Profile", Icons.Default.Person, 1),
        Triple("Settings", Icons.Default.Settings, 2),
        Triple("Setup", Icons.Default.Security, 3)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceContainer) {
                navItems.forEach { (label, icon, index) ->
                    val isSelected = currentTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = index },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryIndigo,
                            selectedTextColor = PrimaryIndigo,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                0 -> OnboardingImportScreen()
                1 -> ContactProfileScreen()
                2 -> SettingsScreen()
                3 -> PermissionsScreen()
            }
        }
    }
}
