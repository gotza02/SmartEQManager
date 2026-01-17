package com.example.smarteq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smarteq.data.DefaultPresets
import com.example.smarteq.data.EQPreset
import com.example.smarteq.manager.EQManager
import com.example.smarteq.manager.PresetManager

/**
 * Screen สำหรับควบคุม EQ
 */
@Composable
fun EQControlScreen(
    eqManager: EQManager,
    presetManager: PresetManager,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(DefaultPresets.FLAT) }
    var isEnabled by remember { mutableStateOf(false) }

    // รับข้อมูลจาก EQManager
    val bandLevels by eqManager.bandLevels.collectAsState()
    val bandCount = eqManager.getBandCount()
    val centerFreqs = eqManager.getCenterFreqs()
    val (minLevel, maxLevel) = eqManager.getBandLevelRange()
    val isWorking = eqManager.isWorking()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isWorking)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = if (isWorking) "EQ Status: Working" else "EQ Status: Not Supported",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enable/Disable Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable EQ",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    isEnabled = enabled
                    eqManager.setEnabled(enabled)
                    if (enabled) {
                        eqManager.applyPreset(selectedPreset)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Preset Selector
        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DefaultPresets.ALL) { preset ->
                FilterChip(
                    selected = selectedPreset.id == preset.id,
                    onClick = {
                        selectedPreset = preset
                        if (isEnabled) {
                            eqManager.applyPreset(preset)
                            presetManager.saveLastPreset(preset.id)
                        }
                    },
                    label = { Text(preset.name) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // EQ Sliders
        if (isWorking && bandCount > 0) {
            Text(
                text = "Equalizer Bands",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0 until bandCount) {
                    EQBandSlider(
                        bandName = formatFrequency(centerFreqs.getOrNull(i) ?: 0),
                        level = bandLevels.getOrNull(i) ?: 0,
                        minLevel = minLevel,
                        maxLevel = maxLevel,
                        onLevelChange = { newLevel ->
                            eqManager.setBandLevel(i, newLevel)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else if (!isWorking) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Equalizer Not Supported",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your device may not support global EQ. This feature depends on your device manufacturer's audio implementation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Debug Info
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Debug Info",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = eqManager.getStatusInfo(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Slider สำหรับแต่ละ EQ Band
 */
@Composable
fun EQBandSlider(
    bandName: String,
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Band Name
        Text(
            text = bandName,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Slider
        Box(
            modifier = Modifier
                .height(200.dp)
                .width(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Max label
                Text(
                    text = "+${maxLevel / 100}",
                    style = MaterialTheme.typography.labelSmall
                )

                // Slider
                Slider(
                    value = level.toFloat(),
                    onValueChange = { onLevelChange(it.toInt()) },
                    valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(8.dp)
                )

                // Min label
                Text(
                    text = "${minLevel / 100}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Current Level
        Text(
            text = "${level / 100}dB",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * แปลงความถี่ให้เป็นรูปแบบที่อ่านง่าย
 */
private fun formatFrequency(hz: Int): String {
    return when {
        hz >= 1000 -> "${hz / 1000}kHz"
        else -> "${hz}Hz"
    }
}
