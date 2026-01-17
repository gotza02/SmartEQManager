package com.example.smarteq.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.smarteq.manager.EQManager
import com.example.smarteq.manager.PresetManager
import com.example.smarteq.ui.theme.SmartEQTheme

/**
 * MainActivity หลักของแอป
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmartEQTheme {
                MainScreen()
            }
        }
    }
}

/**
 * Main Screen หลัก
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val eqManager = remember { EQManager(context) }
    val presetManager = remember { PresetManager(context) }

    // ตรวจสอบ Accessibility Service status
    val isAccessibilityEnabled = remember { isAccessibilityServiceEnabled(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart EQ Manager") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Equalizer, null) },
                    label = { Text("EQ Control") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Smartphone, null) },
                    label = { Text("App Presets") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.GraphicEq, null) },
                    label = { Text("Volume Norm") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> EQControlScreen(
                eqManager = eqManager,
                presetManager = presetManager,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            1 -> AppPresetListScreen(
                presetManager = presetManager,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            2 -> VolumeNormalizationScreen(
                eqManager = eqManager,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            3 -> SettingsScreen(
                isAccessibilityEnabled = isAccessibilityEnabled,
                onOpenAccessibilitySettings = {
                    openAccessibilitySettings(context)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

/**
 * ตรวจสอบว่า Accessibility Service เปิดอยู่หรือไม่
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, AppDetectorService::class.java)

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServices.split(":").any { it == expectedComponentName.flattenToString() }
}

/**
 * เปิด Accessibility Settings
 */
fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    context.startActivity(intent)
}
