# 🎚️ Smart EQ Manager

Global Equalizer App สำหรับ Android ที่ auto-switch EQ preset เมื่อเปลี่ยนแอป

## ✨ Features

- ✅ **Global Equalizer** - ใช้งานได้กับทุก media app
- ✅ **Per-App Presets** - ตั้งค่า EQ แต่ละแอปได้ต่างกัน
- ✅ **Auto-Switch** - เปลี่ยน preset อัตโนมัติเมื่อ switch app
- ✅ **9-Band EQ** - ปรับความถี่ได้ละเอียด
- ✅ **Custom Presets** - สร้าง preset ของตัวเองได้

## 📋 Requirements

- Android 8.0 (API 26) ขึ้นไป
- Accessibility Service permission

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                  UI Layer                        │
│  • MainActivity (Compose)                       │
│  • EQControlScreen                              │
│  • AppPresetListScreen                          │
│  • SettingsScreen                               │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              Service Layer                      │
│  • AppDetectorService (Accessibility)           │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│             Manager Layer                       │
│  • EQManager (Equalizer control)                │
│  • SessionDetector (Audio sessions)             │
│  • PresetManager (Storage)                      │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│               Data Layer                        │
│  • EQPreset (EQ presets data)                   │
│  • AppConfig (Per-app config)                   │
└─────────────────────────────────────────────────┘
```

## 🚀 วิธีการ Build

### 1. เปิดโปรเจกต์ใน Android Studio
```bash
cd SmartEQManager
```

### 2. Sync Gradle
Android Studio จะ sync dependencies อัตโนมัติ

### 3. Build APK
```bash
./gradlew assembleDebug
```

### 4. ติดตั้งบนเครื่อง
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📱 วิธีใช้งาน

### Step 1: เปิดใช้งาน Accessibility Service

1. เปิดแอป
2. ไปที่ **Settings** tab
3. กด **Enable Service**
4. ค้นหา **Smart EQ Manager** ในรายการ
5. เปิด toggle ให้เป็น ON

### Step 2: ตั้งค่า EQ Presets แต่ละแอป

1. ไปที่ **App Presets** tab
2. เลือกแอปที่ต้องการ (เช่น Spotify, YouTube)
3. เลือก preset ที่ต้องการ (Rock, Pop, Vocal, etc.)
4. เปิด toggle เพื่อเปิดใช้งาน auto-EQ

### Step 3: ใช้งาน

เมื่อเปิดแอปที่ตั้งค่าไว้:
- EQ จะถูกปรับอัตโนมัติตาม preset
- ไม่ต้องปรับ EQ ทุกครั้งที่เปลี่ยนแอป!

## 🎵 Default Presets

| Preset | Description | Bands |
|--------|-------------|-------|
| **Flat** | ปกติ ไม่ปรับ | ทุก band เท่ากัน |
| **Bass Boost** | เสียงทุ้ง | เพิ่ม 60Hz |
| **Treble Boost** | เสียงแหลม | เพิ่ม 14kHz |
| **Vocal** | เน้นเสียงร้อง | เพิ่ม Mid bands |
| **Rock** | เสียงแน่น | Bass + Treble |
| **Pop** | สมดุล | Balanced |

## ⚠️ Known Limitations

### Global EQ Support

แอปใช้ **Legacy Mode (sessionId=0)** ซึ่ง:
- ✅ ใช้ได้กับ: Pixel, Samsung บางรุ่น, OnePlus บางรุ่น
- ❌ อาจไม่ได้กับ: Xiaomi ส่วนใหญ่, Huawei

ถ้าไม่ได้:
1. ตรวจสอบที่ **Debug Info** ใน EQ Control tab
2. ดูว่า "EQ Status: Working" หรือไม่
3. ถ้า "Not Supported" = เครื่องไม่รองรับ global EQ

## 🔧 Technical Details

### EQ Methods

**Method 1: Legacy Mode (sessionId=0)**
```kotlin
val equalizer = Equalizer(0, 0)  // Global output
```
- ง่ายที่สุด
- Deprecated แต่ยังใช้ได้บางเครื่อง
- ไม่ต้อง root

**Method 2: Broadcast Receiver**
- รับ session ID จาก media apps
- ต้องการ app ส่ง broadcast เอง
- ใช้ได้กับ apps ที่ support เท่านั้น

**Method 3: Enhanced Detection (DUMP)**
- ใช้ DUMP permission
- ต้อง grant via ADB:
  ```bash
  adb shell pm grant com.example.smarteq android.permission.DUMP
  ```
- อ่าน AudioPlaybackConfiguration โดยตรง

## 📂 Project Structure

```
SmartEQManager/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/smarteq/
│   │   │   ├── data/
│   │   │   │   ├── EQPreset.kt          # Preset data model
│   │   │   │   └── AppConfig.kt         # App config
│   │   │   ├── manager/
│   │   │   │   ├── EQManager.kt         # Equalizer control
│   │   │   │   ├── SessionDetector.kt   # Audio session detection
│   │   │   │   └── PresetManager.kt     # Storage
│   │   │   ├── service/
│   │   │   │   └── AppDetectorService.kt # Accessibility service
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── EQControlScreen.kt
│   │   │   │   ├── AppPresetListScreen.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── theme/
│   │   │   └── receiver/
│   │   ├── res/
│   │   │   ├── xml/
│   │   │   │   └── accessibility_service_config.xml
│   │   │   └── values/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── README.md
```

## 🤝 Contributing

Pull requests ยินดีต้อนรับ!

## 📄 License

MIT License

## 🙏 Credits

- Wavelet - Inspiration for global EQ implementation
- Android AudioFX Documentation
- Open-source Android community

---

Made with ❤️ for Android audio enthusiasts
