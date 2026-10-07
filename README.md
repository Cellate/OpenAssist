# OpenAssist

<p align="center">
  <img src="OpenAssist.png" width="96" height="96" alt="OpenAssist Logo" />
</p>

<p align="center">
  <strong>Open-Source Digital Assistant & Circle to Search for Android</strong><br>
  <em>Built for de-Googled ROMs (LineageOS, GrapheneOS, CalyxOS) & OEM Android (ColorOS, One UI, Pixel)</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_10+-3DDC84?style=flat&logo=android&logoColor=white" alt="Android 10+" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License" />
  <img src="https://img.shields.io/badge/Google_Play_Services-Zero_Dependency-success" alt="No GApps Needed" />
  <img src="https://img.shields.io/badge/Whisper_Engine-Offline_&_Local-purple" alt="Offline Whisper" />
</p>

---

## 🌟 Overview

**OpenAssist** is a 100% open-source, privacy-first alternative to proprietary voice assistants and proprietary "Circle to Search" implementations. 

It runs as your default **Android Digital Assistant** (`android.service.voice.VoiceInteractionService`). Whenever you trigger the assistant gesture (long-press Home or swipe from bottom corner), OpenAssist captures the screen, gives you an interactive canvas to circle or crop any area, transcribes your spoken prompt (via offline Whisper or Google Speech), and automatically pastes the image crop and your prompt directly into your favorite AI web chat (**Gemini, ChatGPT, Claude, Perplexity, DeepSeek, Grok**, or your own custom endpoint).

---

## ✨ Key Features

### 🔍 1. Circle to Search Interactive Canvas
* **Google Neon Glow Lasso:** Draw a freehand glowing lasso around any image, equation, text snippet, or UI element.
* **Precision 8-Handle Box & Smooth Handle Pulling:** Once circled (or in box mode), 8 precision resize handles appear at all 4 corners and 4 edge centers with generous 52dp touch targets. You can freely pull and drag any of the 8 handles to fine-tune your exact crop area without accidentally triggering a new circle search line. Drag from inside the box to reposition, or touch outside to draw a new circle.
* **Canva-Style Free Pan & Zoom:** 2-finger pan and pinch-to-zoom (0.6x up to 12x) to comfortably reach the extreme top or bottom edges of any app.
* **Instant Clipboard Sync:** Selection crops are immediately saved to internal cache and synced to the Android clipboard.
* **Uncongested Prompt Bar:** Professional, distraction-free prompt pill (`"Type or speak your question here..."`) housing only the text input, microphone button, and send button.

### 🎙️ 2. Offline Whisper & Dual-Engine Speech Dictation
* **Dual-Device Architecture:**
  * **Custom ROMs / De-Googled (e.g. LineageOS, GrapheneOS, AOSP):** Eliminates `Voice input failed (5)` and `Speech engine busy` errors. Routes seamlessly to **FUTO Voice Input** or on-device local Whisper models.
  * **OEM Android (e.g. Oppo ColorOS 14, Samsung One UI):** Connects directly to Google Speech Recognition & Synthesis (`com.google.android.tts`) without `EXTRA_PREFER_OFFLINE` lockouts.
* **Local Quantized Model Store (`.bin` / `.onnx`):**
  * Import any quantized GGML / ONNX Whisper model (e.g. `whisper-base-hinglish.bin`, `whisper-small-hindi.bin`, `whisper-tiny-en.onnx`).
  * One-tap instant active model selection with persistent highlighting.
  * Per-language model binding (separate models for Hinglish, Hindi, and English).
* **Processing Modes:**
  * **Post-Processing (Default):** Records audio cleanly first, then passes the buffer to Whisper for maximum accuracy without dropped words.
  * **Real-Time Streaming:** Live word-by-word streaming dictation.
* **Hinglish Transliteration:** Full phonetic Devanagari-to-Latin conversion in `Translit.kt` so spoken Hindi instructions are cleanly transliterated into Hinglish.
* **Hands-Free "Auto-Listen on Assist":** Toggleable setting that automatically arms the microphone 250ms after entering the overlay, letting you speak instructions immediately without touching any button.
* **Persistent Privacy Card:** Real-time audio waveform visualizer with active status beacon (`● LIVE MIC ACTIVE`), engine badge, partial transcription preview, and Done/Cancel controls. **Audio is never recorded or sent to any telemetry server.**

### 🤖 3. Direct AI Web Assistant Injection & Custom AI (Zero Vendor Lock-In)
* **Multi-Service AI Switcher:** Switch with 1 tap between **Gemini, ChatGPT, Claude, Perplexity, DeepSeek, Grok, Le Chat (Mistral), Copilot, Qwen**, or any custom endpoint.
* **Add Any Custom Web Assistant on the Fly:**
  * When a new frontier model or self-hosted LLM interface is released, add it immediately without code edits or app updates.
  * **In Settings:** Dedicated *AI Web Assistant & Custom AI Services* section lets you manage custom assistants, set defaults, delete old ones, or use 1-tap quick presets (DeepSeek, Grok, Ollama WebUI, LM Studio).
  * **Directly in Overlay:** Tap `+ Add AI` in the assistant chips row to enter an AI Name and Web URL on the fly.
  * **Full Local AI & Self-Hosted Support:** Connect directly to local LAN or home server endpoints (e.g. `http://localhost:3000` for Ollama / Open WebUI, `http://192.168.1.x:1234` for LM Studio, or custom reverse proxies).
* **Smart Multi-Strategy Auto-Pasting:**
  * Dispatches synthetic browser `ClipboardEvent('paste')` and `DragEvent('drop')` with `DataTransfer` payloads directly into the AI's `rich-textarea` / `[contenteditable]`.
  * Dynamically queries hidden `input[type="file"]` elements and fires synthetic `change` events.
  * Native hardware paste keyevent fallback (`Ctrl+V`).
* **UI Caching for Ultra-Fast Loads:**
  * Configured with `WebSettings.LOAD_DEFAULT`, DOM storage, and database cache persistence so Gemini and other AI web UIs load from disk cache instead of re-downloading heavy bundles on every assist.
  * `singleTask` with `FLAG_ACTIVITY_SINGLE_TOP` keeps the WebView warmed up in memory for instant responses.
* **Crash Shielding:**
  * **Binder IPC Protection:** Downscales high-resolution screenshots to safe web resolutions (max 1280px, RGB_565, ~150-250KB) before Base64 injection, completely preventing Android's 1MB `TransactionTooLargeException` and Chromium IPC buffer overflows.
  * **`onRenderProcessGone` Recovery:** Automatically catches Chromium renderer process restarts under memory pressure, keeping the host application stable.

---

## 📱 Hardware & OS Compatibility

| Device / Operating System | Speech Engine Used | Status | Notes |
| :--- | :--- | :--- | :--- |
| **LineageOS 20 / 21 (De-Googled)** | FUTO Voice Input / Local Whisper | ✅ Verified | No GApps or Google Play Services required |
| **GrapheneOS / CalyxOS** | FUTO Voice Input / Local Whisper | ✅ Verified | Full sandboxed privacy operation |
| **Oppo F21s Pro 4G (ColorOS 14)** | Google TTS / Google Speech Services | ✅ Verified | Fixed modal popup "Voice search isn't available" |
| **Samsung Galaxy (One UI)** | Samsung Voice / Google TTS / FUTO | ✅ Verified | Supports Bixby and Honeyboard speech intents |
| **Google Pixel (Stock Android 10-15)** | Google Speech / Local Whisper | ✅ Verified | Seamless integration with assist gestures |

---

## 🚀 Getting Started & Setup

### Step 1: Install the APK
Download or build `OpenAssist.apk` and install it on your device.

### Step 2: Set as Default Digital Assistant
1. Go to **Settings → Apps → Default apps → Digital assistant app** (or **Voice input & assist**).
2. Tap **Default digital assistant app** and select **OpenAssist**.
3. **Important:** Enable **Use screenshot** (and **Use text from screen**). Without this toggle, Android's framework will not supply the screen bitmap to any digital assistant.

### Step 3: Grant Permissions
1. Launch **OpenAssist Settings** from your app drawer.
2. Grant **Microphone** permission for voice dictation.
3. (Optional) Under **Behaviour**, enable **"Auto-listen on Assist"** if you want hands-free voice input as soon as you trigger the overlay.

### Step 4: Choose or Add Your AI Assistant
* By default, OpenAssist uses **Gemini**.
* In Settings under **AI Web Assistant & Custom AI Services**, switch to **ChatGPT, Claude, Perplexity, DeepSeek, Grok, Mistral, Copilot, Qwen**, or tap **+ Add Custom Web Assistant** to hook up any local or newly released AI (e.g. `http://localhost:3000` for Ollama / Open WebUI).
* You can also switch assistants or tap `+ Add AI` at any time directly inside the Circle to Search overlay!

---

## 🛠️ Building the APK

### Option A: 1-Click Cloud Build via GitHub Actions (Recommended)
You do not need Android Studio or a local Android SDK installed. GitHub Actions provides free cloud builders:

1. Clone or push this repository to GitHub on `main` or `master`.
2. Push your changes:
   ```bash
   git add .
   git commit -m "Build OpenAssist APK"
   git push origin main
   ```
3. Navigate to the **Actions** tab on your GitHub repository.
4. The workflow in `.github/workflows/build.yml` automatically:
   * Sets up Java 17 (Temurin) and Gradle 8.7
   * Creates an Android debug signing keystore
   * Builds the release APK (`gradle assembleRelease --no-daemon`)
   * Uploads the build output to artifacts
5. Click on the completed run and download the **OpenAssist-apk** artifact!

### Option B: Local Command Line Build
If you have Java 17 and the Android SDK installed locally:

```bash
# Make gradlew executable
chmod +x ./gradlew

# Build release APK
./gradlew assembleRelease --no-daemon

# The APK will be located at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📂 Project Architecture

```
OpenAssist/
├── .github/workflows/build.yml      # Automated GitHub Actions release builder
├── app/
│   ├── build.gradle.kts             # App-level build config (compileSdk 34, NDK)
│   ├── proguard-rules.pro           # ProGuard keeps for OpenAssist & Whisper
│   └── src/main/
│       ├── AndroidManifest.xml      # VoiceInteractionService & queries declarations
│       ├── cpp/                     # C++ Whisper JNI Bridge
│       │   ├── CMakeLists.txt       # CMake fetch & build for whisper.cpp
│       │   └── whisper_jni.cpp      # JNI bridge for on-device transcription
│       ├── java/com/openassist/app/
│       │   ├── AssistService.kt     # VoiceInteractionService entrypoint
│       │   ├── OverlayActivity.kt   # Circle to Search canvas & prompt input
│       │   ├── SelectionView.kt     # Glowing lasso, box crop, pan & pinch-zoom
│       │   ├── WebActivity.kt       # WebView AI chat with auto-paste injection
│       │   ├── Speech.kt            # Multi-engine audio orchestrator
│       │   ├── Recorder.kt          # 16kHz PCM audio recorder & watchdog
│       │   ├── ModelStore.kt        # Multi-model offline Whisper manager
│       │   ├── WhisperBridge.kt     # Native JNI wrapper for .bin models
│       │   ├── Translit.kt          # Devanagari to Latin Hinglish transliteration
│       │   ├── Prefs.kt             # Persistent user settings & engine flags
│       │   ├── SettingsActivity.kt  # Unified voice engine & model settings UI
│       │   ├── StagedFiles.kt       # Safe cache manager & bitmap compression
│       │   ├── SelectionProvider.kt # File ContentProvider for temporary URIs
│       │   ├── ClipboardHelper.kt   # System clipboard synchronization
│       │   ├── ImeContent.kt        # Rich image & file commit input wrapper
│       │   ├── AiServices.kt        # Supported AI endpoints registry
│       │   └── Ui.kt                # Fluid UI styling, dp math, and theme tokens
│       └── res/                     # Vector drawables, colors, themes, locales
├── build.gradle.kts                 # Root Gradle configuration
├── settings.gradle.kts              # Project repository & module settings
└── gradle.properties                # JVM heap and AndroidX build flags
```

---

## 🔒 Privacy Guarantee

* **Zero Telemetry:** OpenAssist contains no Google Analytics, Firebase Telemetry, Sentry, or third-party trackers.
* **Local Audio:** In **Local Model Mode** and **FUTO Mode**, your microphone audio is processed directly on your device's CPU/NPU and is never sent over any network.
* **Direct Web Interaction:** Your questions and images are sent exclusively to the AI website you choose (e.g. `gemini.google.com`, `chatgpt.com`). The app acts as an open, transparent user agent.

---

## 📄 License

OpenAssist is distributed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for more information.
