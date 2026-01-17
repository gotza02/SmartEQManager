package com.example.smarteq.manager

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.DynamicsProcessing.Limiter
import android.media.audiofx.DynamicsProcessing.Mbc
import android.media.audiofx.DynamicsProcessing.MbcBand
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manager สำหรับ Volume Normalization
 * ใช้ DynamicsProcessing (Android 9+) และ LoudnessEnhancer
 *
 * การทำงาน:
 * 1. Detect audio session ของ active app
 * 2. Apply DynamicsProcessing ตาม session:
 *    - Limiter: ป้องกัน clipping (เพลงดัง)
 *    - Compressor: Reduce dynamic range
 *    - Output Gain: Boost เพลงเบา
 */
class VolumeNormalizationManager(private val context: Context) {

    companion object {
        private const val TAG = "VolumeNormalizationManager"

        // Default settings
        private const val DEFAULT_LIMITER_THRESHOLD = -2.0f  // dB
        private const val DEFAULT_LIMITER_ATTACK = 10        // ms
        private const val DEFAULT_LIMITER_RELEASE = 100      // ms

        private const val DEFAULT_COMPRESSOR_RATIO = 2.0f    // 2:1 ratio
        private const val DEFAULT_COMPRESSOR_THRESHOLD = -20.0f  // dB
        private const val DEFAULT_COMPRESSOR_ATTACK = 50     // ms
        private const val DEFAULT_COMPRESSOR_RELEASE = 200   // ms

        private const val DEFAULT_OUTPUT_GAIN = 3.0f         // dB
    }

    private val sessionDetector = AudioSessionDetector(context)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null  // Job สำหรับ monitoring session changes

    // DynamicsProcessing instances per session
    private val sessionProcessors = mutableMapOf<Int, DynamicsProcessing>()

    // LoudnessEnhancer for global boost (sessionId=0)
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // State สำหรับ UI
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentSession = MutableStateFlow<AudioSessionDetector.ActiveSession?>(null)
    val currentSession: StateFlow<AudioSessionDetector.ActiveSession?> = _currentSession.asStateFlow()

    private val _settings = MutableStateFlow(
        NormalizationSettings(
            limiterEnabled = true,
            limiterThreshold = DEFAULT_LIMITER_THRESHOLD,
            compressorEnabled = true,
            compressorRatio = DEFAULT_COMPRESSOR_RATIO,
            outputGain = DEFAULT_OUTPUT_GAIN
        )
    )
    val settings: StateFlow<NormalizationSettings> = _settings.asStateFlow()

    init {
        initialize()
    }

    /**
     * ข้อมูลการตั้งค่า normalization
     */
    data class NormalizationSettings(
        val limiterEnabled: Boolean,
        val limiterThreshold: Float,      // dB (negative, e.g., -2.0)
        val compressorEnabled: Boolean,
        val compressorRatio: Float,       // e.g., 2.0 = 2:1 ratio
        val compressorThreshold: Float = DEFAULT_COMPRESSOR_THRESHOLD,  // dB
        val outputGain: Float             // dB (positive for boost)
    )

    /**
     * เริ่มต้นระบบ
     */
    private fun initialize() {
        try {
            // Initialize LoudnessEnhancer (global)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                loudnessEnhancer = LoudnessEnhancer(0, 0)
                Log.d(TAG, "LoudnessEnhancer initialized")
            }

            // Start monitoring sessions
            sessionDetector.startMonitoring()

            // Observe session changes
            monitorJob = serviceScope.launch {
                sessionDetector.primarySession.collect { session ->
                    handleSessionChange(session)
                }
            }

            Log.d(TAG, "VolumeNormalizationManager initialized")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    /**
     * เปิด/ปิด ใช้งาน normalization
     */
    fun setEnabled(enabled: Boolean) {
        try {
            _isEnabled.value = enabled

            if (enabled) {
                // Enable LoudnessEnhancer
                loudnessEnhancer?.setEnabled(true)
                loudnessEnhancer?.setTargetGain((_settings.value.outputGain * 1000).toInt())

                // Enable all session processors
                sessionProcessors.values.forEach { dp ->
                    dp.enabled = true
                }

                Log.d(TAG, "Normalization enabled")
            } else {
                // Disable LoudnessEnhancer
                loudnessEnhancer?.setEnabled(false)

                // Disable all session processors
                sessionProcessors.values.forEach { dp ->
                    dp.enabled = false
                }

                Log.d(TAG, "Normalization disabled")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set enabled state", e)
        }
    }

    /**
     * อัปเดตการตั้งค่า
     */
    fun updateSettings(newSettings: NormalizationSettings) {
        try {
            _settings.value = newSettings

            // Update LoudnessEnhancer
            loudnessEnhancer?.setTargetGain((newSettings.outputGain * 1000).toInt())

            // Update all session processors
            sessionProcessors.values.forEach { dp ->
                applySettingsToProcessor(dp, newSettings)
            }

            Log.d(TAG, "Settings updated: $newSettings")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update settings", e)
        }
    }

    /**
     * จัดการการเปลี่ยนแปลงของ session
     */
    private fun handleSessionChange(session: AudioSessionDetector.ActiveSession?) {
        try {
            _currentSession.value = session

            if (session != null && _isEnabled.value) {
                // Create or update processor for this session
                createOrUpdateSessionProcessor(session)
            }

            // Clean up old sessions
            cleanupInactiveSessions()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling session change", e)
        }
    }

    /**
     * สร้างหรืออัปเดต processor สำหรับ session
     */
    private fun createOrUpdateSessionProcessor(session: AudioSessionDetector.ActiveSession) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Check if processor already exists
                if (!sessionProcessors.containsKey(session.sessionId)) {
                    // Create new DynamicsProcessing
                    val dp = DynamicsProcessing(
                        0,  // priority
                        session.sessionId
                    )

                    // Apply current settings
                    applySettingsToProcessor(dp, _settings.value)

                    // Enable if normalization is enabled
                    dp.enabled = _isEnabled.value

                    // Store
                    sessionProcessors[session.sessionId] = dp

                    Log.d(TAG, "Created processor for session ${session.sessionId} (${session.packageName})")
                }
            } else {
                Log.d(TAG, "DynamicsProcessing not supported (Android < 9)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create processor for session ${session.sessionId}", e)
        }
    }

    /**
     * นำการตั้งค่าไปใช้กับ processor
     */
    private fun applySettingsToProcessor(dp: DynamicsProcessing, settings: NormalizationSettings) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val config = dp.config

                // Configure Limiter
                config.limiterEnabled = settings.limiterEnabled
                if (settings.limiterEnabled) {
                    config.limiter.setThreshold(settings.limiterThreshold)
                    config.limiter.setAttackTime(DEFAULT_LIMITER_ATTACK.toInt())
                    config.limiter.setReleaseTime(DEFAULT_LIMITER_RELEASE.toInt())
                }

                // Configure Compressor (MBC - Multi-Band Compressor)
                config.mbcEnabled = settings.compressorEnabled
                if (settings.compressorEnabled) {
                    // Create single full-band compressor
                    val mbcBand = MbcBand(
                        0,  // band index
                        settings.compressorThreshold,  // threshold
                        settings.compressorRatio,      // ratio
                        DEFAULT_COMPRESSOR_ATTACK.toInt(),   // attack
                        DEFAULT_COMPRESSOR_RELEASE.toInt(),  // release
                        0f,  // priority (0 = highest)
                        0f,  // min gain
                        0f   // max gain
                    )

                    // For simplicity, use single band covering full range
                    val mbc = Mbc(1)  // 1 band
                    mbc.setBand(0, mbcBand)

                    config.setMbcToStage(0, mbc)
                }

                // Apply config
                dp.setConfig(config)

                Log.d(TAG, "Applied settings to processor: limiter=${settings.limiterEnabled}, compressor=${settings.compressorEnabled}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply settings to processor", e)
        }
    }

    /**
     * ลบ processor ของ sessions ที่ไม่ active แล้ว
     */
    private fun cleanupInactiveSessions() {
        try {
            val activeSessionIds = sessionDetector.activeSessions.value.map { it.sessionId }.toSet()

            val toRemove = sessionProcessors.keys.filter { it !in activeSessionIds }
            toRemove.forEach { sessionId ->
                sessionProcessors[sessionId]?.release()
                sessionProcessors.remove(sessionId)
                Log.d(TAG, "Removed processor for inactive session $sessionId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up inactive sessions", e)
        }
    }

    /**
     * รับข้อมูลสถานะ
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("Volume Normalization Status:")
            appendLine("  Enabled: ${_isEnabled.value}")
            appendLine("  Current Session: ${_currentSession.value?.packageName ?: "None"}")
            appendLine("  Active Sessions: ${sessionProcessors.size}")
            appendLine("  Limiter: ${if (_settings.value.limiterEnabled) "ON (${_settings.value.limiterThreshold}dB)" else "OFF"}")
            appendLine("  Compressor: ${if (_settings.value.compressorEnabled) "ON (${_settings.value.compressorRatio}:1)" else "OFF"}")
            appendLine("  Output Gain: +${_settings.value.outputGain}dB")
        }
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            setEnabled(false)

            // Cancel monitoring job
            monitorJob?.cancel()
            monitorJob = null

            // Cancel coroutine scope
            serviceScope.cancel()

            // Release all processors
            sessionProcessors.values.forEach { it.release() }
            sessionProcessors.clear()

            // Release LoudnessEnhancer
            loudnessEnhancer?.release()
            loudnessEnhancer = null

            // Stop session detector
            sessionDetector.release()

            Log.d(TAG, "Released")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to release", e)
        }
    }
}
