package com.example.smarteq.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.smarteq.data.AppConfig
import com.example.smarteq.manager.EQManager
import com.example.smarteq.manager.PresetManager
import kotlinx.coroutines.*

/**
 * Accessibility Service สำหรับ detect active app
 * เมื่อ detect app change → auto-switch EQ preset
 */
class AppDetectorService : AccessibilityService() {

    companion object {
        private const val TAG = "AppDetectorService"

        // หน่วงเวลาเล็กน้อยเพื่อให้แน่ใจว่า app switch เสร็จ
        private const val SWITCH_DELAY_MS = 100L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var eqManager: EQManager
    private lateinit var presetManager: PresetManager

    private var currentPackage: String? = null
    private var isServiceEnabled = true

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppDetectorService created")

        eqManager = EQManager(this)
        presetManager = PresetManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AppDetectorService connected")

        // เริ่มต้น EQ manager
        if (presetManager.isGlobalEnabled()) {
            eqManager.setEnabled(true)
            Log.d(TAG, "EQ enabled")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceEnabled) return

        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handleWindowStateChanged(event)
            }
        }
    }

    /**
     * จัดการเมื่อมีการเปลี่ยน window/app
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()

        if (packageName.isNullOrBlank()) return

        // กรองเฉพาะ activity จริง (ไม่เอา dialog, menu, etc.)
        if (className != null && (
            className.contains("Dialog") ||
            className.contains("Menu") ||
            className.contains("Popup") ||
            className.contains("Drawer")
        )) {
            return
        }

        // ถ้า package เปลี่ยน ให้ switch EQ
        if (packageName != currentPackage) {
            currentPackage = packageName
            Log.d(TAG, "App changed to: $packageName")

            // หน่วงเล็กน้อยแล้วค่อย switch
            serviceScope.launch {
                delay(SWITCH_DELAY_MS)
                switchEQForApp(packageName)
            }
        }
    }

    /**
     * Switch EQ preset ตาม app
     */
    private fun switchEQForApp(packageName: String) {
        // รับ config ของ app
        var config = presetManager.getAppConfig(packageName)

        // ถ้าไม่มี config ให้สร้าง default
        if (config == null) {
            val popularApp = com.example.smarteq.data.PopularApps.findByPackageName(packageName)
            config = popularApp ?: com.example.smarteq.data.PopularApps.createUnknown(packageName)
        }

        // ถ้าปิดใช้งานสำหรับ app นี้ ให้ใช้ Flat preset
        if (!config.enabled) {
            eqManager.applyPreset(com.example.smarteq.data.DefaultPresets.FLAT)
            Log.d(TAG, "EQ disabled for $packageName, using Flat preset")
            return
        }

        // ใช้ custom bands ถ้ามี
        if (config.useCustomBands()) {
            val customBands = config.customBands ?: return
            customBands.forEachIndexed { index, level ->
                eqManager.setBandLevel(index, level)
            }
            Log.d(TAG, "Applied custom EQ for $packageName")
            return
        }

        // ใช้ preset
        val preset = presetManager.getPresetById(config.presetId)
        if (preset != null) {
            eqManager.applyPreset(preset)
            Log.d(TAG, "Applied preset '${preset.name}' for $packageName")
        } else {
            // ถ้าไม่เจอ preset ให้ใช้ Flat
            eqManager.applyPreset(com.example.smarteq.data.DefaultPresets.FLAT)
            Log.d(TAG, "Preset not found, using Flat for $packageName")
        }
    }

    /**
     * รับ config ของ app ปัจจุบัน
     */
    fun getCurrentAppConfig(): AppConfig? {
        val packageName = currentPackage ?: return null
        return presetManager.getAppConfig(packageName)
    }

    /**
     * อัปเดต config ของ app ปัจจุบัน
     */
    fun updateCurrentAppConfig(config: AppConfig) {
        val packageName = config.packageName
        presetManager.updateAppConfig(config)

        // ถ้าเป็น app ปัจจุบัน ให้ apply ทันที
        if (packageName == currentPackage) {
            switchEQForApp(packageName)
        }
    }

    /**
     * เปิด/ปิด การใช้งาน service
     */
    fun setServiceEnabled(enabled: Boolean) {
        isServiceEnabled = enabled
        Log.d(TAG, "Service ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * รับสถานะ EQ manager
     * @throws UninitializedPropertyAccessException ถ้าเรียกก่อน onCreate()
     */
    fun getEQManager(): EQManager {
        if (!::eqManager.isInitialized) {
            throw UninitializedPropertyAccessException("EQManager not initialized. Call onCreate() first.")
        }
        return eqManager
    }

    /**
     * รับสถานะ preset manager
     * @throws UninitializedPropertyAccessException ถ้าเรียกก่อน onCreate()
     */
    fun getPresetManager(): PresetManager {
        if (!::presetManager.isInitialized) {
            throw UninitializedPropertyAccessException("PresetManager not initialized. Call onCreate() first.")
        }
        return presetManager
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppDetectorService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        eqManager.release()
        Log.d(TAG, "AppDetectorService destroyed")
    }
}
