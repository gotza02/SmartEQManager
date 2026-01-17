package com.example.smarteq.data

/**
 * การตั้งค่า EQ สำหรับแต่ละแอป
 * @param packageName ชื่อ package ของแอป (เช่น com.spotify.music)
 * @param appName ชื่อแอปที่แสดงใน UI
 * @param presetId รหัส preset ที่เลือกใช้
 * @param customBands ค่า EQ ที่กำหนดเอง (ถ้ามี) จะ override preset
 * @param enabled เปิดใช้งาน auto-EQ สำหรับแอปนี้หรือไม่
 */
data class AppConfig(
    val packageName: String,
    val appName: String,
    val presetId: String = DefaultPresets.FLAT.id,
    val customBands: List<Int>? = null,
    val enabled: Boolean = true
) {
    /**
     * ตรวจสอบว่าใช้ custom bands หรือ preset
     */
    fun useCustomBands(): Boolean = customBands != null

    /**
     * คัดลอกและอัปเดต preset
     */
    fun withPreset(presetId: String): AppConfig {
        return copy(presetId = presetId, customBands = null)
    }

    /**
     * คัดลอกและตั้งค่า custom bands
     */
    fun withCustomBands(bands: List<Int>): AppConfig {
        return copy(presetId = "custom", customBands = bands)
    }

    /**
     * เปิด/ปิด การใช้งาน
     */
    fun withEnabled(enabled: Boolean): AppConfig {
        return copy(enabled = enabled)
    }
}

/**
 * รายการแอปยอดนิยมที่รองรับ EQ preset
 */
object PopularApps {
    val SPOTIFY = AppConfig(
        packageName = "com.spotify.music",
        appName = "Spotify",
        presetId = DefaultPresets.ROCK.id
    )

    val YOUTUBE_MUSIC = AppConfig(
        packageName = "com.google.android.apps.youtube.music",
        appName = "YouTube Music",
        presetId = DefaultPresets.POP.id
    )

    val YOUTUBE = AppConfig(
        packageName = "com.google.android.youtube",
        appName = "YouTube",
        presetId = DefaultPresets.VOCAL.id
    )

    val APPLE_MUSIC = AppConfig(
        packageName = "com.apple.android.music",
        appName = "Apple Music",
        presetId = DefaultPresets.POP.id
    )

    val SOUNDCLOUD = AppConfig(
        packageName = "com.soundcloud.android",
        appName = "SoundCloud",
        presetId = DefaultPresets.ELECTRONIC.id
    )

    val DEEZER = AppConfig(
        packageName = "com.deezer.android.app",
        appName = "Deezer",
        presetId = DefaultPresets.POP.id
    )

    val TIDAL = AppConfig(
        packageName = "com.aspiro.tidal",
        appName = "Tidal",
        presetId = DefaultPresets.FLAT.id
    )

    val AMAZON_MUSIC = AppConfig(
        packageName = "com.amazon.mp3",
        appName = "Amazon Music",
        presetId = DefaultPresets.POP.id
    )

    val POWERAMP = AppConfig(
        packageName = "com.maxmpz.audioplayer",
        appName = "Poweramp",
        presetId = DefaultPresets.ROCK.id
    )

    /**
     * รายการทั้งหมด
     */
    val ALL = listOf(
        SPOTIFY, YOUTUBE_MUSIC, YOUTUBE, APPLE_MUSIC,
        SOUNDCLOUD, DEEZER, TIDAL, AMAZON_MUSIC, POWERAMP
    )

    /**
     * ค้นหาแอปจาก package name
     */
    fun findByPackageName(packageName: String): AppConfig? {
        return ALL.find { it.packageName == packageName }
    }

    /**
     * สร้าง AppConfig สำหรับแอปที่ไม่รู้จัก
     */
    fun createUnknown(packageName: String): AppConfig {
        // แปลง package name เป็นชื่อแอปที่อ่านง่าย
        val appName = packageName.substringAfterLast('.')
            .split('.')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return AppConfig(
            packageName = packageName,
            appName = appName,
            presetId = DefaultPresets.FLAT.id,
            enabled = false  // default disabled
        )
    }
}
