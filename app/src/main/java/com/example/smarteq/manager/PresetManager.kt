package com.example.smarteq.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.smarteq.data.AppConfig
import com.example.smarteq.data.EQPreset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager สำหรับจัดการ Preset storage
 * ใช้ SharedPreferences สำหรับเก็บข้อมูล
 */
class PresetManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "eq_presets"
        private const val KEY_APP_PRESETS = "app_presets"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_GLOBAL_ENABLED = "global_enabled"
        private const val KEY_LAST_PRESET = "last_preset"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // State สำหรับ observe app configs
    private val _appConfigs = MutableStateFlow<List<AppConfig>>(emptyList())
    val appConfigs: StateFlow<List<AppConfig>> = _appConfigs.asStateFlow()

    // State สำหรับ custom presets
    private val _customPresets = MutableStateFlow<List<EQPreset>>(emptyList())
    val customPresets: StateFlow<List<EQPreset>> = _customPresets.asStateFlow()

    init {
        loadAppConfigs()
        loadCustomPresets()
    }

    /**
     * บันทึก preset สำหรับแอปที่ระบุ
     */
    fun saveAppPreset(packageName: String, presetId: String) {
        val configs = getAppConfigsMap()
        val existing = configs[packageName]
        val updated = (existing ?: AppConfig(
            packageName = packageName,
            appName = packageName.substringAfterLast('.')
        )).copy(presetId = presetId)

        configs[packageName] = updated
        saveAppConfigsMap(configs)
        loadAppConfigs()
    }

    /**
     * บันทึก custom bands สำหรับแอปที่ระบุ
     */
    fun saveAppCustomBands(packageName: String, bands: List<Int>) {
        val configs = getAppConfigsMap()
        val existing = configs[packageName]
        val updated = (existing ?: AppConfig(
            packageName = packageName,
            appName = packageName.substringAfterLast('.')
        )).copy(customBands = bands, presetId = "custom")

        configs[packageName] = updated
        saveAppConfigsMap(configs)
        loadAppConfigs()
    }

    /**
     * รับ preset ที่บันทึกไว้สำหรับแอป
     */
    fun getAppPreset(packageName: String): String? {
        val config = getAppConfigsMap()[packageName]
        return config?.presetId
    }

    /**
     * รับ custom bands ที่บันทึกไว้สำหรับแอป
     */
    fun getAppCustomBands(packageName: String): List<Int>? {
        val config = getAppConfigsMap()[packageName]
        return config?.customBands
    }

    /**
     * รับ AppConfig ทั้งหมด
     */
    fun getAllAppConfigs(): List<AppConfig> {
        return _appConfigs.value
    }

    /**
     * รับ AppConfig สำหรับแอปที่ระบุ
     */
    fun getAppConfig(packageName: String): AppConfig? {
        return _appConfigs.value.find { it.packageName == packageName }
    }

    /**
     * อัปเดต AppConfig
     */
    fun updateAppConfig(config: AppConfig) {
        val configs = getAppConfigsMap()
        configs[config.packageName] = config
        saveAppConfigsMap(configs)
        loadAppConfigs()
    }

    /**
     * ลบ AppConfig ของแอป
     */
    fun removeAppConfig(packageName: String) {
        val configs = getAppConfigsMap()
        configs.remove(packageName)
        saveAppConfigsMap(configs)
        loadAppConfigs()
    }

    /**
     * บันทึก custom preset ใหม่
     */
    fun saveCustomPreset(preset: EQPreset) {
        val presets = getCustomPresetsList().toMutableList()

        // ถ้ามี ID เดิมอยู่แล้ว ให้แทนที่
        val index = presets.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            presets[index] = preset
        } else {
            presets.add(preset)
        }

        saveCustomPresetsList(presets)
        loadCustomPresets()
    }

    /**
     * ลบ custom preset
     */
    fun deleteCustomPreset(presetId: String) {
        val presets = getCustomPresetsList().toMutableList()
        presets.removeAll { it.id == presetId }
        saveCustomPresetsList(presets)
        loadCustomPresets()
    }

    /**
     * รับ EQ preset จาก ID (รวมทั้ง default และ custom)
     */
    fun getPresetById(presetId: String): EQPreset? {
        // ค้นหาจาก default presets
        val default = com.example.smarteq.data.DefaultPresets.ALL.find { it.id == presetId }
        if (default != null) return default

        // ค้นหาจาก custom presets
        return _customPresets.value.find { it.id == presetId }
    }

    /**
     * ตั้งค่า global enabled state
     */
    fun setGlobalEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
    }

    /**
     * รับ global enabled state
     */
    fun isGlobalEnabled(): Boolean {
        return prefs.getBoolean(KEY_GLOBAL_ENABLED, true)
    }

    /**
     * บันทึก last used preset
     */
    fun saveLastPreset(presetId: String) {
        prefs.edit().putString(KEY_LAST_PRESET, presetId).apply()
    }

    /**
     * รับ last used preset
     */
    fun getLastPreset(): String? {
        return prefs.getString(KEY_LAST_PRESET, null)
    }

    // Private helper methods

    private fun getAppConfigsMap(): MutableMap<String, AppConfig> {
        val json = prefs.getString(KEY_APP_PRESETS, null)
        if (json == null) {
            // Return default popular apps
            val defaults = mutableMapOf<String, AppConfig>()
            com.example.smarteq.data.PopularApps.ALL.forEach {
                defaults[it.packageName] = it
            }
            return defaults
        }

        val type = object : TypeToken<Map<String, AppConfig>>() {}.type
        return gson.fromJson(json, type) ?: mutableMapOf()
    }

    private fun saveAppConfigsMap(map: Map<String, AppConfig>) {
        val json = gson.toJson(map)
        prefs.edit().putString(KEY_APP_PRESETS, json).apply()
    }

    private fun loadAppConfigs() {
        val map = getAppConfigsMap()
        _appConfigs.value = map.values.toList()
    }

    private fun getCustomPresetsList(): List<EQPreset> {
        val json = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        val type = object : TypeToken<List<EQPreset>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveCustomPresetsList(list: List<EQPreset>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_CUSTOM_PRESETS, json).apply()
    }

    private fun loadCustomPresets() {
        _customPresets.value = getCustomPresetsList()
    }

    /**
     * ล้างข้อมูลทั้งหมด (สำหรับ debug)
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        loadAppConfigs()
        loadCustomPresets()
    }
}
