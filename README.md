# OpenAssist (v1.34.0)

> **The 100% Free & Open-Source Digital Assistant and Circle to Search for Android.**  
> Built for De-Googled ROMs (LineageOS, GrapheneOS, CalyxOS) & OEM Android (ColorOS, One UI, Pixel, HyperOS).

[![Android CI](https://github.com/openassist/openassist/actions/workflows/build.yml/badge.svg)](https://github.com/openassist/openassist/actions)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Zero Google Dependencies](https://img.shields.io/badge/Dependencies-Zero%20Google%20Play-emerald.svg)](#privacy--de-googled-support)

---

## 🌟 Overview

On proprietary Android distributions, **Circle to Search** and voice assistance are locked behind closed Google Play Services or OEM-specific cloud stacks. Custom de-Googled ROMs (such as LineageOS, GrapheneOS, and CalyxOS) have historically had zero options for a functional screen-search assistant.

**OpenAssist** is a drop-in replacement for the Android system digital assistant (`VoiceInteractionService`). Triggered via long-press Home, navigation pill long-press, or corner swipe gestures, it captures your current screen without transmitting private telemetry.

### Key Capabilities
- 🔍 **Circle to Search**: Google Neon Glow lasso with 8 precision handles (52dp touch targets), 2-finger pan, and 0.6x to 12.0x pinch-zoom.
- 🎙️ **Dual-Engine Speech Recognition**:
  - **In-process Native Engine**: Direct SpeechRecognizer binding with FUTO Voice Input and Vosk lock (completely avoids ColorOS/Oppo "Voice search isn't available" popup).
  - **Embedded Whisper.cpp (GGML v2)**: High-performance C++ JNI Whisper engine with multi-model store, fast per-language binding, and live Hinglish/Devanagari phonetics.
- 🌐 **Zero AI Lock-In**: Crop-and-send directly to Gemini, ChatGPT, Claude, DeepSeek, Grok, Copilot, Perplexity, Le Chat, Qwen, or any custom self-hosted LLM (Ollama, LM Studio, Open WebUI at `http://localhost:3000`).
- 🦊 **Interactive Companions**: 10 reactive mascot companions (Ozzy Sentinel, Pip Nexus, Echo Bat, Nova Otter, Pico Chameleon, Taro Tanuki, Ollie Sprite, Byte Kitten, Lumi Firefly, Kiko Axolotl) with synth audio chirps and live states.
- 🛡️ **Zero Tracking & Privacy First**: No analytics, no proprietary SDKs, no ad IDs. Fully open source under GPLv3.

---

## 📊 Feature Comparison

| Capability | Google Circle to Search | Samsung Galaxy AI | OpenAssist |
| :--- | :---: | :---: | :---: |
| **License & Source** | Proprietary | Proprietary | **100% Open Source (GPLv3)** |
| **De-Googled ROMs** | ❌ No | ❌ No | **✅ Full Native Support** |
| **Offline Speech** | Requires GApps | Samsung Account | **✅ Whisper.cpp / FUTO / Vosk** |
| **Choice of AI Backend** | Google Only | Google / Samsung | **✅ Gemini, ChatGPT, Claude, Ollama, DeepSeek** |
| **Screenshot Privacy** | Sent to Google Cloud | Sent to Cloud | **✅ Kept On-Device until you send** |
| **Hinglish Conversion** | Inconsistent | ❌ No | **✅ Phonetic Devanagari ➔ Latin Engine** |
| **Mascot Companion** | ❌ No | ❌ No | **✅ 10 Customizable Reactive Mascots** |

---

## 🏗️ Architecture

```
OpenAssist Architecture
├── app/src/main/java/com/openassist/app/
│   ├── AssistService.kt        # VoiceInteractionService implementation & session hooks
│   ├── OverlayActivity.kt      # Transparent system UI overlay, Circle to Search & Voice Bar
│   ├── SelectionView.kt        # Custom Canvas view: lasso, neon glow, 8-handle crop, pinch-zoom
│   ├── Speech.kt               # Direct Android SpeechRecognizer lifecycle & FUTO priority
│   ├── WhisperBridge.kt        # JNI wrapper for whisper.cpp C++ inference engine
│   ├── ModelStore.kt           # GGML quantized model picker & language profile bindings
│   ├── Translit.kt             # Devanagari to Latin transliteration engine
│   ├── WebActivity.kt          # AI Web container with Drag-and-Drop screenshot injection
│   ├── AiServices.kt           # AI engine definitions (Gemini, ChatGPT, Claude, Local URL)
│   ├── Prefs.kt                # Type-safe Android SharedPreferences manager
│   └── SettingsActivity.kt     # Preferences UI, Whisper tester, and Mascot selector
├── app/src/main/cpp/
│   ├── CMakeLists.txt          # NDK build script linking libwhisper
│   └── whisper_jni.cpp         # Native JNI binding for 16kHz audio stream transcription
└── .github/workflows/
    ├── build.yml               # Automated Debug APK build on pull requests
    └── main.yml                # Automated Release APK signed build on branch push
```

---

## 🚀 Getting Started

### Prerequisites
- Android 10.0+ (API level 29 or higher)
- Android Studio Iguana / Jellyfish (or command-line Gradle 8.7+)
- Android NDK 25.2.9519653 + CMake 3.22.1 (for local Whisper compilation)
- JDK 17

### Building from Source

```bash
# Clone the repository
git clone https://github.com/openassist/openassist.git
cd openassist

# Build Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Enabling OpenAssist as Default Assistant

1. Open your phone's **Settings**.
2. Navigate to **Apps** > **Default apps** > **Digital assistant app**.
3. Select **OpenAssist**.
4. Enable the toggle for **Use screenshot** / **Analyze screen**.
5. Long-press the **Home button** (or swipe up from the bottom corners) to invoke!

---

## 📄 License

OpenAssist is licensed under the **GNU General Public License v3.0 (GPLv3)**.  
See [LICENSE](LICENSE) for the full text.
