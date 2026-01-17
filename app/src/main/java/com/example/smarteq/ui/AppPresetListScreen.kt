package com.example.smarteq.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Divider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarteq.data.AppConfig
import com.example.smarteq.data.DefaultPresets
import com.example.smarteq.data.PopularApps
import com.example.smarteq.manager.PresetManager

/**
 * Screen สำหรับจัดการ Preset แต่ละแอป
 */
@Composable
fun AppPresetListScreen(
    presetManager: PresetManager,
    modifier: Modifier = Modifier
) {
    val appConfigs by presetManager.appConfigs.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "App Presets",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure EQ presets for each app. Auto-switched when the app is active.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(appConfigs) { config ->
                AppPresetItem(
                    config = config,
                    onConfigUpdate = { updated ->
                        presetManager.updateAppConfig(updated)
                    }
                )
            }
        }
    }
}

/**
 * Item สำหรับแต่ละ App Config
 */
@Composable
fun AppPresetItem(
    config: AppConfig,
    onConfigUpdate: (AppConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: App Name + Enabled Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Enabled Toggle
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { enabled ->
                        onConfigUpdate(config.withEnabled(enabled))
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preset",
                    style = MaterialTheme.typography.bodyMedium
                )

                TextButton(
                    onClick = { expanded = true }
                ) {
                    Text(presetManager.getPresetById(config.presetId)?.name ?: "Unknown")
                    Icon(
                        imageVector = if (expanded) Icons.Default.Circle else Icons.Default.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Dropdown Menu for presets
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Default Presets
                Text(
                    text = "Default Presets",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DefaultPresets.ALL.forEach { preset ->
                    val isSelected = config.presetId == preset.id
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(preset.name)
                            }
                        },
                        onClick = {
                            onConfigUpdate(config.withPreset(preset.id))
                            expanded = false
                        }
                    )
                }
            }

            // Show custom bands if any
            if (config.customBands != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Divider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Custom Bands",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    config.customBands.forEachIndexed { index, level ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "${level / 100}dB",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onConfigUpdate(config.withPreset(DefaultPresets.FLAT.id))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Preset")
                }
            }
        }
    }
}
