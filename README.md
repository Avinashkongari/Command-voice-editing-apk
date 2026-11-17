# VoxCut Studio — AI Voice-Controlled Android Video Editor

- **Application Name**: VoxCut Studio
- **Package Name (`applicationId`)**: `com.aistudio.voxcut.vkxrqm`
- **Version**: `1.0` (`versionCode: 1`)
- **Min / Target SDK**: Android 8.0 (API 26) / Android 16 (API 36)
- **Debug APK Artifact**: `releases/VoxCut_Studio_v1.0.0-debug.apk` (`25,818,360` bytes / ~24.6 MB, APK Signature Scheme v2 verified)

## Core Capabilities

- **Multi-Provider AI & Dynamic Model Discovery**:
  - Dedicated adapters for **Google Gemini API**, **OpenAI-Compatible APIs**, and **Custom REST Endpoints**.
  - Dynamic model discovery (`/v1beta/models` and `/v1/models`), manual Model ID entry, model capability tags, and live connection testing.
  - Hardware-backed **Android Keystore AES-256-GCM** encrypted credential storage (keys are never hardcoded or logged).
- **Voice-Directed Editing (Hindi, English, Hinglish)**:
  - Live `SpeechRecognizer` voice capture + typed AI command panel.
  - Strict structured JSON command validation (`CommandValidator`) before execution in `TimelineEngine`.
  - Missing-asset detection with one-tap Photo Picker / SAF continuation.
- **Multi-Track Visual Timeline & Media3 Export**:
  - Video, Image Overlay, Text & Subtitles, FX & Keyframes, Audio/Story Narration (with streaming `MediaExtractor`/`MediaCodec` waveform peaks), and Voiceover recording (`MediaRecorder`).
  - Manual tools: Split, Trim, Crop, Rotate, Scale/Position, Duplicate, Freeze Frame, Color Filters, Brightness/Contrast/Saturation/Exposure/Blur, Ken Burns motion, Transitions, Speed (`0.25x–4x`), Volume/Fades, and Keyframes.
  - Real **Media3 Transformer** MP4 export (`480p`–`4K`, `24/30/60 FPS`, configurable bitrate) with device `MediaCodecList` capability checks and `StatFs` storage checks.

## APK Download & Installation Instructions (Android Phone)

1. Open `releases/VoxCut_Studio_v1.0.0-debug.apk` in the repository and tap **Download** / **View raw**.
2. On your Android 8.0+ phone, open the downloaded `.apk` file from your Downloads / Files app.
3. If prompted, enable **Allow from this source** for your browser or file manager, then tap **Install**.
4. Open **VoxCut Studio**, go to **AI API Settings**, configure your provider (Gemini / OpenAI-compatible / Custom), fetch models, and tap **Test Connection** to enable voice editing.

## Build Instructions (Local / CI)

1. Open in Android Studio (JDK 11+, Android SDK 36, `minSdk 26`).
2. No API keys or signing secrets are bundled in the repository.
3. Build Debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
4. Build Release APK (requires upload keystore + `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` env vars):
   ```bash
   gradle :app:assembleRelease
   ```
5. Run local JVM unit & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
