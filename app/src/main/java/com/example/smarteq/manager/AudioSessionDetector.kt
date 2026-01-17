package com.example.smarteq.manager

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Detector สำหรับตรวจจับ Audio Sessions ของ apps ต่างๆ
 * ใช้ AudioPlaybackConfiguration API (Android 8.1+)
 */
class AudioSessionDetector(private val context: Context) {

    companion object {
        private const val TAG = "AudioSessionDetector"
        private const val POLL_INTERVAL_MS = 1000L

        // Player type constants (using reflection since they may not be publicly accessible)
        private const val PLAYER_TYPE_AAUDIO = 1
        private const val PLAYER_TYPE_SOUNDFOUNTAIN = 2 // Deprecated
        private const val PLAYER_TYPE_MEDIAPLAYER = 3
        private const val PLAYER_TYPE_JAM_SOUNDPOOL = 4 // Legacy
        private const val PLAYER_TYPE_AUDIOTRACK = 5
    }

    private val audioManager = requireNotNull(context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager) {
        "AudioManager not available"
    }

    // Coroutine scope สำหรับ background work
    private val detectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    // State สำหรับ active sessions
    private val _activeSessions = MutableStateFlow<Set<ActiveSession>>(emptySet())
    val activeSessions: StateFlow<Set<ActiveSession>> = _activeSessions.asStateFlow()

    // State สำหรับ primary session (session ของ active app)
    private val _primarySession = MutableStateFlow<ActiveSession?>(null)
    val primarySession: StateFlow<ActiveSession?> = _primarySession.asStateFlow()

    @Volatile
    private var isMonitoring = false

    /**
     * ข้อมูล session ที่ detect ได้
     */
    data class ActiveSession(
        val sessionId: Int,
        val packageName: String?,
        val uid: Int,
        val type: SessionType
    )

    enum class SessionType {
        MEDIA,
        GAME,
        UNKNOWN
    }

    /**
     * เริ่ม monitoring sessions
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring")
            return
        }

        isMonitoring = true
        Log.d(TAG, "Started monitoring audio sessions")

        // Start polling ด้วย coroutine
        monitorJob = detectorScope.launch {
            while (isMonitoring) {
                try {
                    detectSessions()
                    delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error detecting sessions", e)
                }
            }
        }
    }

    /**
     * หยุด monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.d(TAG, "Stopped monitoring")
    }

    /**
     * Detect active audio sessions
     */
    private fun detectSessions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            detectSessionsApi26()
        } else {
            detectSessionsLegacy()
        }
    }

    /**
     * Detect sessions สำหรับ Android 8.0+ (API 26+)
     * ใช้ AudioPlaybackConfiguration API
     */
    private fun detectSessionsApi26() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        try {
            val sessions = mutableSetOf<ActiveSession>()

            // Get active audio sessions (Public API)
            val configs = audioManager.activePlaybackConfigurations

            for (config in configs) {
                // Use reflection to access properties that may not be publicly accessible
                val sessionId = try {
                    config.javaClass.getMethod("getAudioSessionId").invoke(config) as? Int ?: 0
                } catch (e: Exception) {
                    // Fallback: try to access as property
                    try {
                        config.javaClass.getField("audioSessionId").getInt(config)
                    } catch (e2: Exception) {
                        0
                    }
                }

                val playerType = try {
                    config.javaClass.getMethod("getPlayerType").invoke(config) as? Int ?: 0
                } catch (e: Exception) {
                    // Fallback: try to access as property
                    try {
                        config.javaClass.getField("playerType").getInt(config)
                    } catch (e2: Exception) {
                        0
                    }
                }

                val session = ActiveSession(
                    sessionId = sessionId,
                    packageName = null, // Not available in API 26-28 easily
                    uid = 0, // Not available in API 26-28 easily
                    type = determineSessionType(playerType)
                )
                sessions.add(session)
            }

            // Update state
            _activeSessions.value = sessions

            // Set primary session (ใช้ session แรกที่พบ)
            if (sessions.isNotEmpty()) {
                _primarySession.value = sessions.first()
            } else {
                _primarySession.value = null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting sessions (API 26+)", e)
        }
    }

    /**
     * Detect sessions แบบ legacy (Android < 8.0)
     * ใช้การ enumerate audio effects
     */
    private fun detectSessionsLegacy() {
        try {
            val sessions = mutableSetOf<ActiveSession>()

            // Enumerate all equalizer effects
            val effects = AudioEffect.queryEffects()

            for (descriptor in effects) {
                if (descriptor.type == AudioEffect.EFFECT_TYPE_EQUALIZER) {
                    // Try to create effect to get session info
                    try {
                        // Note: เราไม่สามารถ detect session แบบ legacy ได้ละเอียด
                        // ให้ return empty set และใช้ sessionId=0 แทน
                        Log.d(TAG, "Found equalizer effect: ${descriptor.name}")
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            _activeSessions.value = sessions
            _primarySession.value = null

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting sessions (legacy)", e)
        }
    }

    /**
     * Determine session type จาก player type name (Legacy)
     */
    private fun determineSessionType(playerTypeName: String?): SessionType {
        return when {
            playerTypeName?.contains("MediaPlayer", ignoreCase = true) == true -> SessionType.MEDIA
            playerTypeName?.contains("SoundPool", ignoreCase = true) == true -> SessionType.GAME
            playerTypeName?.contains("AudioTrack", ignoreCase = true) == true -> SessionType.MEDIA
            else -> SessionType.UNKNOWN
        }
    }

    /**
     * Determine session type จาก player type int (API 26+)
     */
    private fun determineSessionType(playerType: Int): SessionType {
        return when (playerType) {
            PLAYER_TYPE_AAUDIO,
            PLAYER_TYPE_AUDIOTRACK,
            PLAYER_TYPE_MEDIAPLAYER -> SessionType.MEDIA
            PLAYER_TYPE_JAM_SOUNDPOOL -> SessionType.GAME
            else -> SessionType.UNKNOWN
        }
    }

    /**
     * Get session สำหรับ package ที่ระบุ
     */
    fun getSessionForPackage(packageName: String): ActiveSession? {
        return _activeSessions.value.find { it.packageName == packageName }
    }

    /**
     * Release resources
     */
    fun release() {
        stopMonitoring()

        // Cancel monitor job
        monitorJob?.cancel()
        monitorJob = null

        // Cancel coroutine scope
        detectorScope.cancel()

        _activeSessions.value = emptySet()
        _primarySession.value = null
    }
}