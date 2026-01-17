package com.example.smarteq.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarteq.manager.EQManager
import com.example.smarteq.manager.VolumeNormalizationManager

/**
 * Screen สำหรับตั้งค่า Volume Normalization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeNormalizationScreen(
    eqManager: EQManager,
    modifier: Modifier = Modifier
) {
    val normalizationManager = eqManager.volumeNormalizationManager
    val isEnabled by normalizationManager.isEnabled.collectAsState()
    val settings by normalizationManager.settings.collectAsState()
    val currentSession by normalizationManager.currentSession.collectAsState()
    val scrollState = rememberScrollState()

    // Local state for sliders
    var limiterThreshold by remember { mutableStateOf(settings.limiterThreshold) }
    var compressorRatio by remember { mutableStateOf(settings.compressorRatio) }
    var outputGain by remember { mutableStateOf(settings.outputGain) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Volume Normalization",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "ทำให้เพลงดังและเบามีความดังใกล้เคียงกัน (Android 9+)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Android Version Warning
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ ไม่รองรับ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Volume Normalization ต้องการ Android 9 (Pie) ขึ้นไป\nเครื่องของคุณใช้ Android ${Build.VERSION.RELEASE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "เปิด Volume Normalization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnabled) "กำลังทำงาน" else "ปิดอยู่",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enabled ->
                        normalizationManager.setEnabled(enabled)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current Session Info
        if (isEnabled && currentSession != null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Session ปัจจุบัน",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "App: ${currentSession?.packageName ?: "Unknown"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Session ID: ${currentSession?.sessionId ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Limiter Settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SettingsSection(title = "Limiter (จำกัดเสียงดัง)") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Limiter Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "เปิด Limiter",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = settings.limiterEnabled,
                                onCheckedChange = { enabled ->
                                    normalizationManager.updateSettings(
                                        settings.copy(limiterEnabled = enabled)
                                    )
                                }
                            )
                        }

                        if (settings.limiterEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Threshold Slider
                            Text(
                                text = "Threshold: ${limiterThreshold.toInt()}dB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = limiterThreshold,
                                onValueChange = { value ->
                                    limiterThreshold = value
                                },
                                onValueChangeFinished = {
                                    normalizationManager.updateSettings(
                                        settings.copy(limiterThreshold = limiterThreshold)
                                    )
                                },
                                valueRange = -20.0f..0.0f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "ยิ่งต่ำ ยิ่ง compress เยอะ (-20dB = หนักมาก, 0dB = ปิด)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Compressor Settings
            SettingsSection(title = "Compressor (ลดช่วงไดนามิก)") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Compressor Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "เปิด Compressor",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = settings.compressorEnabled,
                                onCheckedChange = { enabled ->
                                    normalizationManager.updateSettings(
                                        settings.copy(compressorEnabled = enabled)
                                    )
                                }
                            )
                        }

                        if (settings.compressorEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Ratio Slider
                            Text(
                                text = "Ratio: ${compressorRatio.toInt()}:1",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = compressorRatio,
                                onValueChange = { value ->
                                    compressorRatio = value
                                },
                                onValueChangeFinished = {
                                    normalizationManager.updateSettings(
                                        settings.copy(compressorRatio = compressorRatio)
                                    )
                                },
                                valueRange = 1.0f..10.0f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "ยิ่งสูง ยิ่ง compress เยอะ (2:1 = ปานกลาง, 10:1 = หนักมาก)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Output Gain Settings
            SettingsSection(title = "Output Gain (เพิ่มเสียงเบา)") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Gain: +${outputGain.toInt()}dB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = outputGain,
                            onValueChange = { value ->
                                outputGain = value
                            },
                            onValueChangeFinished = {
                                normalizationManager.updateSettings(
                                    settings.copy(outputGain = outputGain)
                                )
                            },
                            valueRange = 0.0f..10.0f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "เพิ่ม volume ทั้งหมด เพื่อ compensate การ compress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Info
            SettingsSection(title = "สถานะ") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = normalizationManager.getStatusInfo(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Section wrapper
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        content()
    }
}
