package com.example.smarteq.data

/**
 * Data class สำหรับ EQ Preset
 * @param id รหัส preset ที่ไม่ซ้ำกัน
 * @param name ชื่อ preset ที่แสดงใน UI
 * @param bands ค่าระดับ EQ แต่ละ band (หน่วย: millibel, -1500 ถึง 1500)
 * @param isDefault เป็น preset ที่มากับระบบหรือไม่
 */
data class EQPreset(
    val id: String,
    val name: String,
    val bands: List<Int>,  // 5 bands: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz
    val isDefault: Boolean = false
) {
    companion object {
        // ค่า min/max สำหรับ EQ bands (millibel)
        const val MIN_LEVEL = -1500  // -15dB
        const val MAX_LEVEL = 1500   // +15dB

        /**
         * แปลงค่า dB เป็น millibel
         */
        fun dBToMillibel(dB: Float): Int = (dB * 100).toInt()

        /**
         * แปลงค่า millibel เป็น dB
         */
        fun millibelToDb(millibel: Int): Float = millibel / 100f
    }

    /**
     * ตรวจสอบว่า preset นี้ใช้ค่า default (flat) หรือไม่
     */
    fun isFlat(): Boolean = bands.all { it == 0 }

    /**
     * คัดลอก preset และแก้ไข band ที่ระบุ
     */
    fun withBand(index: Int, level: Int): EQPreset {
        if (index !in bands.indices) return this
        val newBands = bands.toMutableList()
        newBands[index] = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return copy(bands = newBands)
    }
}

/**
 * Default Presets ที่มากับแอป
 */
object DefaultPresets {
    val FLAT = EQPreset(
        id = "flat",
        name = "Flat",
        bands = listOf(0, 0, 0, 0, 0),
        isDefault = true
    )

    val BASS_BOOST = EQPreset(
        id = "bass_boost",
        name = "Bass Boost",
        bands = listOf(1000, 200, 0, 0, 0),  // +10dB @ 60Hz
        isDefault = true
    )

    val TREBLE_BOOST = EQPreset(
        id = "treble_boost",
        name = "Treble Boost",
        bands = listOf(0, 0, 0, 200, 600),  // +6dB @ 14kHz
        isDefault = true
    )

    val VOCAL = EQPreset(
        id = "vocal",
        name = "Vocal",
        bands = listOf(200, 400, 600, 200, 0),
        isDefault = true
    )

    val ROCK = EQPreset(
        id = "rock",
        name = "Rock",
        bands = listOf(800, 400, 0, 400, 600),
        isDefault = true
    )

    val POP = EQPreset(
        id = "pop",
        name = "Pop",
        bands = listOf(400, 200, 0, 200, 400),
        isDefault = true
    )

    val JAZZ = EQPreset(
        id = "jazz",
        name = "Jazz",
        bands = listOf(200, 300, 400, 400, 200),
        isDefault = true
    )

    val CLASSICAL = EQPreset(
        id = "classical",
        name = "Classical",
        bands = listOf(600, 400, 200, 200, 0),
        isDefault = true
    )

    val ELECTRONIC = EQPreset(
        id = "electronic",
        name = "Electronic",
        bands = listOf(800, 600, 0, 200, 400),
        isDefault = true
    )

    /**
     * รายการ preset ทั้งหมด
     */
    val ALL = listOf(
        FLAT, BASS_BOOST, TREBLE_BOOST, VOCAL,
        ROCK, POP, JAZZ, CLASSICAL, ELECTRONIC
    )
}
