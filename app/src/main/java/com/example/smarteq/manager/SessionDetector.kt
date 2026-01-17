package com.example.smarteq.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detector สำหรับตรวจจับ Audio Session จากแอปต่างๆ
 * รองรับ 2 methods:
 * 1. Broadcast Receiver - รับ session ID จาก media players
 * 2. DUMP permission - อ่านจาก AudioPlaybackConfiguration (ต้อง grant via ADB)
 */
class SessionDetector(private val context: Context) {

    companion object {
        private const val TAG = "SessionDetector"

        // Action สำหรับ broadcast session ID
        const val ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION =
            "android.media.AUDIO_EFFECT_CONTROL_SESSION"

        const val ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION =
            "android.media.AUDIO_EFFECT_CONTROL_SESSION_CLOSE"

        // Extra keys
        const val EXTRA_PACKAGE_NAME = "android.media.extra.PACKAGE_NAME"
        const val EXTRA_AUDIO_SESSION = "android.media.extra.AUDIO_SESSION"
    }

    // State สำหรับ active sessions
    private val _activeSessions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val activeSessions: StateFlow<Map<Int, String>> = _activeSessions.asStateFlow()

    // State สำหรับ current active package
    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    // Broadcast receiver สำหรับรับ session broadcasts
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    handleSessionOpen(intent)
                }
                ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    handleSessionClose(intent)
                }
            }
        }
    }

    private var isRegistered = false

    /**
     * เริ่มต้น detector
     */
    fun start() {
        if (!isRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    addAction(ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                }
                context.registerReceiver(sessionReceiver, filter)
                isRegistered = true
                Log.d(TAG, "Session detector started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session detector", e)
            }
        }
    }

    /**
     * หยุด detector
     */
    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(sessionReceiver)
                isRegistered = false
                Log.d(TAG, "Session detector stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop session detector", e)
            }
        }
    }

    /**
     * จัดการเมื่อมี session เปิด
     */
    private fun handleSessionOpen(intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION, -1)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)

        if (sessionId != -1 && !packageName.isNullOrBlank()) {
            val sessions = _activeSessions.value.toMutableMap()
            sessions[sessionId] = packageName
            _activeSessions.value = sessions
            _currentPackage.value = packageName

            Log.d(TAG, "Session opened: ID=$sessionId, Package=$packageName")
        }
    }

    /**
     * จัดการเมื่อมี session ปิด
     */
    private fun handleSessionClose(intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION, -1)

        if (sessionId != -1) {
            val sessions = _activeSessions.value.toMutableMap()
            val packageName = sessions.remove(sessionId)

            if (packageName != null) {
                _activeSessions.value = sessions

                // Update current package
                _currentPackage.value = if (sessions.isNotEmpty()) {
                    sessions.values.last()
                } else {
                    null
                }

                Log.d(TAG, "Session closed: ID=$sessionId, Package=$packageName")
            }
        }
    }

    /**
     * รับ session ID ปัจจุบันสำหรับ package ที่ระบุ
     */
    fun getSessionId(packageName: String): Int? {
        return _activeSessions.value.entries.find { it.value == packageName }?.key
    }

    /**
     * ตรวจสอบว่ามี session ที่ active หรือไม่
     */
    fun hasActiveSession(packageName: String): Boolean {
        return _activeSessions.value.values.contains(packageName)
    }

    /**
     * รับจำนวน sessions ที่ active ทั้งหมด
     */
    fun getActiveSessionCount(): Int {
        return _activeSessions.value.size
    }

    /**
     * ล้าง sessions ทั้งหมด
     */
    fun clearSessions() {
        _activeSessions.value = emptyMap()
        _currentPackage.value = null
        Log.d(TAG, "All sessions cleared")
    }

    /**
     * สร้าง Intent สำหรับ broadcast session ID
     * (สำหรับ media players ที่ต้องการส่ง session)
     */
    fun createSessionBroadcastIntent(
        sessionId: Int,
        packageName: String
    ): Intent {
        return Intent(ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(EXTRA_AUDIO_SESSION, sessionId)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        }
    }

    /**
     * Enhanced Session Detection (DUMP permission)
     * ต้อง grant DUMP permission via ADB:
     * adb shell pm grant <package.name> android.permission.DUMP
     *
     * วิธีนี้จะอ่าน AudioPlaybackConfiguration โดยตรงจาก system
     */
    fun detectSessionsViaDump(): Map<Int, String> {
        val sessions = mutableMapOf<Int, String>()

        try {
            // เรียก dumpsys ของ audio service
            val process = Runtime.getRuntime().exec(arrayOf(
                "dumpsys",
                "audio"
            ))

            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Parse output หา AudioPlaybackConfiguration
            val lines = output.lines()
            var currentSessionId: Int? = null
            var currentPackage: String? = null

            for (line in lines) {
                when {
                    line.contains("AudioPlaybackConfiguration") -> {
                        // เริ่ม configuration block ใหม่
                        currentSessionId = null
                        currentPackage = null
                    }
                    line.contains("sessionId:") -> {
                        val match = Regex("sessionId:\\s*(\\d+)").find(line)
                        currentSessionId = match?.groupValues?.get(1)?.toIntOrNull()
                    }
                    line.contains("package:") -> {
                        val match = Regex("package:\\s*([\\w.]+)").find(line)
                        currentPackage = match?.groupValues?.get(1)
                    }
                    // เมื่อเจอข้อมูลครบ ให้บันทึก
                    line.trim().isEmpty() || line.contains("---") -> {
                        if (currentSessionId != null && currentPackage != null) {
                            sessions[currentSessionId] = currentPackage
                        }
                    }
                }
            }

            _activeSessions.value = sessions
            Log.d(TAG, "Detected ${sessions.size} sessions via DUMP")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect sessions via DUMP", e)
        }

        return sessions
    }

    /**
     * ตรวจสอบว่ามี DUMP permission หรือไม่
     */
    fun hasDumpPermission(): Boolean {
        return try {
            context.checkPermission(
                android.Manifest.permission.DUMP,
                android.os.Process.myPid(),
                android.os.Process.myUid()
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}
