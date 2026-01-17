package com.example.smarteq.manager

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.AudioEffect.Descriptor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detector สำหรับตรวจจับ Audio Sessions ของ apps ต่างๆ
 * ใช้ AudioPlaybackConfiguration API (Android 8.1+)
 */
class AudioSessionDetector(private val context: Context) {

    companion object {
        private const val TAG = "AudioSessionDetector"
        private const val POLL_INTERVAL_MS = 1000L
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            detectSessionsApi28()
        } else {
            detectSessionsLegacy()
        }
    }

    /**
     * Detect sessions สำหรับ Android 8.1+ (API 27+)
     * ใช้ AudioPlaybackConfiguration API
     */
    @Suppress("DEPRECATION")
    private fun detectSessionsApi28() {
        try {
            val sessions = mutableSetOf<ActiveSession>()

            // Get active audio sessions
            val activeSessionsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                audioManager.getActiveAudioSessionsForPlayer()
            } else {
                emptyList()
            }

            for (config in activeSessionsList) {
                val session = ActiveSession(
                    sessionId = config.audioSessionId,
                    packageName = config.packageName,
                    uid = config.uid,
                    type = determineSessionType(config.playerTypeName)
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
            Log.e(TAG, "Error detecting sessions (API 28+)", e)
        }
    }

    /**
     * Detect sessions แบบ legacy (Android < 8.1)
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
     * Determine session type จาก player type name
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

/**
 * Extension function สำหรับ getting active audio sessions (Android 8.1+)
 */
@Suppress("DEPRECATION")
private fun AudioManager.getActiveAudioSessionsForPlayer(): List<Any> {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Use reflection to call getActiveAudioSessionsForPlayer()
            val method = AudioManager::class.java.getMethod(
                "getActiveAudioSessionsForPlayer"
            )
            @Suppress("UNCHECKED_CAST")
            method.invoke(this) as? List<Any> ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("AudioSessionDetector", "Error getting active sessions", e)
        emptyList()
    }
}
