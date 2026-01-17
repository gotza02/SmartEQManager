package com.example.smarteq.manager

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.util.Log
import com.example.smarteq.data.EQPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager หลักสำหรับจัดการ Equalizer และ Volume Normalization
 * รองรับ 3 modes:
 * 1. Legacy Mode (sessionId=0) - Global EQ
 * 2. Session Mode - EQ สำหรับ session เฉพาะ
 * 3. Disabled - ปิดใช้งาน
 */
class EQManager(private val context: Context) {

    companion object {
        private const val TAG = "EQManager"

        // Priority สำหรับ Equalizer
        private const val PRIORITY = 0

        // Global session ID (Legacy mode)
        private const val SESSION_ID_GLOBAL = 0
    }

    private val audioManager = requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager) {
        "AudioManager not available"
    }
    private var equalizer: Equalizer? = null
    // private var currentSessionId: Int = -1  // Reserved for future session mode implementation

    // Volume Normalization Manager (Android 9+)
    val volumeNormalizationManager = VolumeNormalizationManager(context)

    // State สำหรับ UI
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentMode = MutableStateFlow(EQMode.DISABLED)
    val currentMode: StateFlow<EQMode> = _currentMode.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    /**
     * Modes ที่รองรับ
     */
    enum class EQMode {
        DISABLED,      // ปิดใช้งาน
        LEGACY,        // Global EQ (sessionId=0)
        SESSION        // Per-session EQ
    }

    init {
        initializeEqualizer()
    }

    /**
     * สร้างและเริ่มต้น Equalizer
     */
    private fun initializeEqualizer() {
        try {
            // ลองใช้ Legacy mode (sessionId=0) ก่อน
            equalizer = Equalizer(PRIORITY, SESSION_ID_GLOBAL)
            equalizer?.enabled = false

            val bandCount = equalizer?.numberOfBands ?: 0
            Log.d(TAG, "Equalizer initialized with $bandCount bands")

            if (bandCount > 0) {
                _currentMode.value = EQMode.LEGACY
                _bandLevels.value = (0 until bandCount).map { 0 }

                // แสดงรายละเอียด bands
                for (i in 0 until bandCount) {
                    val freq = equalizer?.getCenterFreq(i.toShort()) ?: 0
                    val range = equalizer?.getBandLevelRange() ?: intArrayOf(0, 0)
                    Log.d(TAG, "Band $i: ${freq / 1000}Hz, range: ${range[0] / 100}dB to ${range[1] / 100}dB")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Equalizer", e)
            _currentMode.value = EQMode.DISABLED
        }
    }

    /**
     * เปิด/ปิด ใช้งาน Equalizer
     */
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            _isEnabled.value = enabled
            Log.d(TAG, "Equalizer ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set enabled state", e)
        }
    }

    /**
     * ใช้ preset กับ Equalizer
     */
    fun applyPreset(preset: EQPreset) {
        try {
            if (!isEnabled.value) {
                setEnabled(true)
            }

            preset.bands.forEachIndexed { index, level ->
                if (index < (_bandLevels.value.size)) {
                    setBandLevel(index, level)
                }
            }

            Log.d(TAG, "Applied preset: ${preset.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply preset", e)
        }
    }

    /**
     * ตั้งค่าระดับ EQ ของ band ที่ระบุ
     * @param bandIndex ดัชนีของ band (0-based)
     * @param level ระดับเสียง (millibel, -1500 ถึง 1500)
     */
    fun setBandLevel(bandIndex: Int, level: Int) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), level.toShort())

            // อัปเดต state
            val newLevels = _bandLevels.value.toMutableList()
            if (bandIndex < newLevels.size) {
                newLevels[bandIndex] = level
                _bandLevels.value = newLevels
            }

            Log.d(TAG, "Band $bandIndex set to ${level / 100}dB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set band level", e)
        }
    }

    /**
     * รับระดับ EQ ของ band ที่ระบุ
     */
    fun getBandLevel(bandIndex: Int): Int {
        return try {
            equalizer?.getBandLevel(bandIndex.toShort())?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get band level", e)
            0
        }
    }

    /**
     * รับจำนวน bands ที่รองรับ
     */
    fun getBandCount(): Int {
        return equalizer?.numberOfBands ?: 0
    }

    /**
     * รับความถี่กลางของแต่ละ band
     * @return รายการความถี่ (Hz)
     */
    fun getCenterFreqs(): List<Int> {
        val bandCount = getBandCount()
        return (0 until bandCount).map { index ->
            (equalizer?.getCenterFreq(index.toShort()) ?: 0) / 1000
        }
    }

    /**
     * รับช่วงระดับที่สามารถตั้งได้
     * @return Pair<min, max> ในหน่วย millibel
     */
    fun getBandLevelRange(): Pair<Int, Int> {
        val range = equalizer?.getBandLevelRange() ?: intArrayOf(-1500, 1500)
        return Pair(range[0], range[1])
    }

    /**
     * รับชื่อ presets ที่มีในระบบ
     */
    fun getSystemPresetNames(): List<String> {
        val presets = mutableListOf<String>()
        try {
            val count = equalizer?.numberOfPresets ?: 0
            for (i in 0 until count) {
                val name = equalizer?.getPresetName(i.toShort()) ?: "Unknown"
                presets.add(name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get preset names", e)
        }
        return presets
    }

    /**
     * ใช้ system preset
     */
    fun useSystemPreset(presetName: String) {
        try {
            val names = getSystemPresetNames()
            val index = names.indexOf(presetName)
            if (index >= 0) {
                equalizer?.usePreset(index.toShort())
                Log.d(TAG, "Used system preset: $presetName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to use system preset", e)
        }
    }

    /**
     * ตรวจสอบว่า Equalizer ทำงานได้หรือไม่
     */
    fun isWorking(): Boolean {
        return equalizer != null && getBandCount() > 0
    }

    /**
     * รับข้อมูลสถานะของ Equalizer
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("EQ Status:")
            appendLine("  Mode: ${_currentMode.value}")
            appendLine("  Enabled: ${_isEnabled.value}")
            appendLine("  Bands: ${getBandCount()}")
            appendLine("  Working: ${isWorking()}")

            if (isWorking()) {
                appendLine("\nFrequencies:")
                getCenterFreqs().forEachIndexed { index, freq ->
                    appendLine("  Band $index: ${freq}Hz")
                }

                val (min, max) = getBandLevelRange()
                appendLine("\nRange: ${min / 100}dB to ${max / 100}dB")
            }
        }
    }

    /**
     * ลบทรัพยากรเมื่อไม่ใช้งานแล้ว
     */
    fun release() {
        try {
            // Release Volume Normalization Manager
            volumeNormalizationManager.release()

            // Release Equalizer
            equalizer?.release()
            equalizer = null
            _isEnabled.value = false
            _currentMode.value = EQMode.DISABLED
            Log.d(TAG, "EQManager released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release EQManager", e)
        }
    }
}
