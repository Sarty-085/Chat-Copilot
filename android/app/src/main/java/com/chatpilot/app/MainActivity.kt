package com.chatpilot.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chatpilot.app.ui.screens.ContactProfileScreen
import com.chatpilot.app.ui.screens.OnboardingImportScreen
import com.chatpilot.app.ui.screens.PermissionsScreen
import com.chatpilot.app.ui.screens.SettingsScreen
import com.chatpilot.app.ui.screens.isNotificationServiceEnabled
import com.chatpilot.app.ui.theme.ChatPilotTheme
import com.chatpilot.app.ui.theme.PrimaryIndigo
import com.chatpilot.app.ui.theme.SurfaceContainer
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

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MainAppScaffold() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var forceShowPermissions by remember { mutableStateOf(false) }

    // Re-check permissions on resume
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

    val isSetupCompleted = hasOverlayPermission && hasNotificationAccess

    // Build tabs dynamically: hide "Setup" once permissions are completed
    val navItems = remember(isSetupCompleted, forceShowPermissions) {
        val items = mutableListOf(
            NavItem("import", "Import", Icons.Default.FileDownload),
            NavItem("profile", "Profiles", Icons.Default.Person),
            NavItem("settings", "Settings", Icons.Default.Settings)
        )
        if (!isSetupCompleted || forceShowPermissions) {
            items.add(NavItem("setup", "Setup", Icons.Default.Security))
        }
        items
    }

    // Default tab: if setup is not completed, start on Setup; otherwise start on Import
    var selectedTabId by remember {
        mutableStateOf(if (!isSetupCompleted) "setup" else "import")
    }
    var activeContactForProfile by remember { mutableStateOf<String?>(null) }

    // If setup completes while user is on setup tab, switch to import automatically
    LaunchedEffect(isSetupCompleted) {
        if (isSetupCompleted && !forceShowPermissions && selectedTabId == "setup") {
            selectedTabId = "import"
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceContainer) {
                navItems.forEach { item ->
                    val isSelected = selectedTabId == item.id
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTabId = item.id },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
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
            when (selectedTabId) {
                "import" -> OnboardingImportScreen(
                    onNavigateToProfile = { contactName ->
                        activeContactForProfile = contactName
                        selectedTabId = "profile"
                    }
                )
                "profile" -> ContactProfileScreen(initialContactName = activeContactForProfile)
                "settings" -> SettingsScreen(
                    onOpenPermissions = {
                        forceShowPermissions = true
                        selectedTabId = "setup"
                    }
                )
                "setup" -> PermissionsScreen(
                    onSetupCompleted = {
                        forceShowPermissions = false
                        selectedTabId = "import"
                    }
                )
                else -> OnboardingImportScreen()
            }
        }
    }
}
