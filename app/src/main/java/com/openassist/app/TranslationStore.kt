package com.openassist.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class LanguageInfo(
    val code: String,
    val name: String,
    val isBuiltIn: Boolean = false
)

class TranslationStore(private val context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("openassist_translations", Context.MODE_PRIVATE)

    companion object {
        const val KEY_APP_LANG = "app_lang"
        const val KEY_SETTINGS_LANG = "settings_lang"
        const val KEY_OVERLAY_LANG = "overlay_lang"
        const val KEY_WEB_LANG = "web_lang"
        const val KEY_MASCOT_LANG = "mascot_lang"
        const val KEY_CUSTOM_LANGS_JSON = "custom_languages_json"
        const val PREFIX_TRANSLATION_MAP = "trans_map_"

        // Canonical English Master Strings used throughout OpenAssist
        val DEFAULT_STRINGS = listOf(
            "Digital assistant" to "Main system voice and circle-to-search service",
            "Choose default assistant" to "Open Android system settings to set OpenAssist as default",
            "Open Web Assistant (Gemini / AI Chat)" to "Launch multi-modal web assistant with camera & clipboard",
            "Open Setup Wizard (First-Run Guide)" to "Interactive walkthrough for mascots, models, and gestures",
            "Local Offline Whisper Models" to "Offline voice transcription models on device",
            "Import Model (.bin)" to "Load a Whisper GGML or GGUF binary file from storage",
            "Whisper Processing Mode" to "Transcription execution pipeline",
            "Post-Processing (Default)" to "Transcribe complete audio segment after speech ends",
            "Real-Time Streaming" to "Transcribe speech chunks continuously as you speak",
            "Per-Language Model Binding" to "Assign specific Whisper models to individual languages",
            "Test Voice Dictation & Transcription" to "Verify microphone recognition and model inference",
            "Tap to Speak / Test" to "Start test audio recording",
            "Stop Recording" to "Stop active speech recording and run model",
            "Active Speech Input Engine" to "Selected voice transcription provider",
            "Google Speech Recognition & Synthesis" to "Official Google engine for ColorOS / Oppo & Android",
            "Local Whisper Mode (Offline GGUF)" to "100% private offline transcription",
            "FUTO Voice Input / Keyboard" to "Fast neural offline voice typing",
            "System Default Speech Recognizer" to "Android platform speech service",
            "AI Web Assistant & Custom AI Services" to "Web conversational LLM configurations",
            "Switch AI" to "Select active AI provider",
            "+ Add Custom AI" to "Connect Ollama, Open WebUI, Grok, or custom endpoint",
            "Mascot Companion Selection" to "Choose your friendly desktop assistant",
            "Mascot Speech Bubbles & Petting" to "Show floating dialogue pills and affectionate responses",
            "Haptic Petting Feedback" to "Subtle vibration pulse when petting mascot",
            "Show Mascot Beside Top Chips" to "Display companion avatar in overlay action bar",
            "Show Mascot in Prompt Search Bar" to "Display companion avatar in web prompt input",
            "Toast Notifications" to "Floating mascot pill notifications",
            "About OpenAssist" to "Open-source digital assistant information",
            "All systems green! Ask me anything!" to "Default cheerful mascot status greeting",
            "Circle or box any element on screen" to "Instruction hint for screen capture selection",
            "Ready to help! Tap to pet me" to "Interactive pet mascot pill invitation",
            "Language & Community Translations" to "Customize languages and import community language packs",
            "Mascot Speech & Dialogue Studio" to "Customize and translate what mascots say across menus"
        )

        // Built-in Hinglish translations for Titles and Descriptions
        val HINGLISH_MAP = mapOf(
            "Digital assistant" to "Digital Sahayak",
            "Main system voice and circle-to-search service" to "Main system voice aur circle-to-search service",
            "Choose default assistant" to "Default Sahayak chuniye",
            "Open Android system settings to set OpenAssist as default" to "Android system settings kholein aur OpenAssist ko default banayein",
            "Open Web Assistant (Gemini / AI Chat)" to "Web Assistant kholein (Gemini / AI Chat)",
            "Launch multi-modal web assistant with camera & clipboard" to "Camera aur clipboard ke saath multi-modal web assistant launch karein",
            "Open Setup Wizard (First-Run Guide)" to "Setup Wizard kholein (First-Run Guide)",
            "Interactive walkthrough for mascots, models, and gestures" to "Mascots, models aur gestures ke liye interactive guide",
            "Local Offline Whisper Models" to "Local Offline Whisper Models",
            "Offline voice transcription models on device" to "Aapke phone par 100% offline voice transcription models",
            "Import Model (.bin)" to "Model (.bin) import karein",
            "Load a Whisper GGML or GGUF binary file from storage" to "Storage se Whisper GGML ya GGUF binary file load karein",
            "Whisper Processing Mode" to "Whisper Processing Mode",
            "Transcription execution pipeline" to "Voice transcription execution pipeline",
            "Post-Processing (Default)" to "Post-Processing (Default)",
            "Transcribe complete audio segment after speech ends" to "Bolna khatam hone ke baad poora audio transcribe karein",
            "Real-Time Streaming" to "Real-Time Streaming",
            "Transcribe speech chunks continuously as you speak" to "Bolte bolte live audio chunks transcribe karein",
            "Per-Language Model Binding" to "Bhasha ke anusaar Model Binding",
            "Assign specific Whisper models to individual languages" to "Har bhasha ke liye alag Whisper model chunein",
            "Test Voice Dictation & Transcription" to "Voice Dictation test karein",
            "Verify microphone recognition and model inference" to "Microphone aur model accuracy test karein",
            "Tap to Speak / Test" to "Bolne ke liye tap karein",
            "Start test audio recording" to "Test recording shuru karein",
            "Stop Recording" to "Recording rokein",
            "Stop active speech recording and run model" to "Active recording rokein aur model chalayein",
            "Active Speech Input Engine" to "Active Voice Input Engine",
            "Selected voice transcription provider" to "Chuna hua voice recognition provider",
            "Google Speech Recognition & Synthesis" to "Google Speech Recognition",
            "Official Google engine for ColorOS / Oppo & Android" to "ColorOS / Oppo aur Android ke liye official Google engine",
            "Local Whisper Mode (Offline GGUF)" to "Local Whisper Mode (100% Offline)",
            "100% private offline transcription" to "100% private aur offline voice typing",
            "FUTO Voice Input / Keyboard" to "FUTO Voice Input / Keyboard",
            "Fast neural offline voice typing" to "Tez on-device neural voice typing",
            "System Default Speech Recognizer" to "System Default Speech Recognizer",
            "Android platform speech service" to "Android system ki default speech service",
            "AI Web Assistant & Custom AI Services" to "AI Web Assistant & Custom AI",
            "Web conversational LLM configurations" to "Web conversational AI aur LLM configurations",
            "Switch AI" to "AI Switch karein",
            "Select active AI provider" to "Active AI provider chuniye",
            "+ Add Custom AI" to "+ Custom AI jodein",
            "Connect Ollama, Open WebUI, Grok, or custom endpoint" to "Ollama, Open WebUI, Grok ya custom endpoint connect karein",
            "Mascot Companion Selection" to "Mascot Companion chuniye",
            "Choose your friendly desktop assistant" to "Apna manpasand pyara sahayak chuniye",
            "Mascot Speech Bubbles & Petting" to "Mascot Speech Bubbles & Petting",
            "Show floating dialogue pills and affectionate responses" to "Mascot ke dialogue pills aur cute reactions dikhayein",
            "Haptic Petting Feedback" to "Haptic Petting Feedback",
            "Subtle vibration pulse when petting mascot" to "Mascot ko pet karne par haptic vibration pulse",
            "Show Mascot Beside Top Chips" to "Top Chips ke paas Mascot dikhayein",
            "Display companion avatar in overlay action bar" to "Screen overlay ke top bar me mascot icon dikhayein",
            "Show Mascot in Prompt Search Bar" to "Prompt bar me Mascot dikhayein",
            "Display companion avatar in web prompt input" to "Web prompt search bar me mascot avatar dikhayein",
            "Toast Notifications" to "Toast Notifications",
            "Floating mascot pill notifications" to "Floating mascot pill notifications",
            "About OpenAssist" to "OpenAssist ke baare me",
            "Open-source digital assistant information" to "Open-source digital assistant ki jankari",
            "All systems green! Ask me anything!" to "Sab theek hai! Kuch bhi poochhiye! ✨",
            "Default cheerful mascot status greeting" to "Default cheerful mascot status greeting",
            "Circle or box any element on screen" to "Screen par kisi bhi cheez ko circle karein",
            "Instruction hint for screen capture selection" to "Screen capture selection ke liye instruction hint",
            "Ready to help! Tap to pet me" to "Madad ke liye taiyaar! Tap karke pet karein 💖",
            "Interactive pet mascot pill invitation" to "Interactive pet mascot pill invitation",
            "Language & Community Translations" to "Bhasha aur Community Translations",
            "Customize languages and import community language packs" to "Bhasha customize karein aur community language packs import karein",
            "Mascot Speech & Dialogue Studio" to "Mascot Baatcheet Studio",
            "Customize and translate what mascots say across menus" to "Mascots kya bolte hain use customize aur translate karein"
        )

        // Built-in Hindi translations for Titles and Descriptions
        val HINDI_MAP = mapOf(
            "Digital assistant" to "डिजिटल सहायक",
            "Main system voice and circle-to-search service" to "मुख्य सिस्टम आवाज़ और सर्कल-टू-सर्च सेवा",
            "Choose default assistant" to "डिफ़ॉल्ट सहायक चुनें",
            "Open Android system settings to set OpenAssist as default" to "OpenAssist को डिफ़ॉल्ट बनाने के लिए Android सेटिंग्स खोलें",
            "Open Web Assistant (Gemini / AI Chat)" to "वेब सहायक खोलें (Gemini / AI चैट)",
            "Launch multi-modal web assistant with camera & clipboard" to "कैमरा और क्लिपबोर्ड के साथ मल्टी-मॉडल वेब सहायक शुरू करें",
            "Open Setup Wizard (First-Run Guide)" to "सेटअप विज़ार्ड खोलें (शुरुआती गाइड)",
            "Interactive walkthrough for mascots, models, and gestures" to "शुभंकर, मॉडल और जेस्चर के लिए इंटरैक्टिव गाइड",
            "Local Offline Whisper Models" to "ऑफ़लाइन व्हिस्पर मॉडल्स",
            "Offline voice transcription models on device" to "डिवाइस पर पूर्णतः ऑफ़लाइन वॉयस ट्रांसक्रिप्शन मॉडल",
            "Import Model (.bin)" to "मॉडल (.bin) जोड़ें",
            "Load a Whisper GGML or GGUF binary file from storage" to "स्टोरेज से व्हिस्पर GGML या GGUF बाइनरी फ़ाइल लोड करें",
            "Whisper Processing Mode" to "व्हिस्पर प्रोसेसिंग मोड",
            "Transcription execution pipeline" to "ट्रांसक्रिप्शन निष्पादन पाइपलाइन",
            "Post-Processing (Default)" to "पोस्ट-प्रोसेसिंग (डिफ़ॉल्ट)",
            "Transcribe complete audio segment after speech ends" to "बोलना समाप्त होने के बाद पूरे ऑडियो का ट्रांसक्रिप्शन करें",
            "Real-Time Streaming" to "रीयल-टाइम स्ट्रीमिंग",
            "Transcribe speech chunks continuously as you speak" to "बोलते समय लगातार ऑडियो का लाइव ट्रांसक्रिप्शन करें",
            "Per-Language Model Binding" to "भाषा अनुसार मॉडल बाइंडिंग",
            "Assign specific Whisper models to individual languages" to "प्रत्येक भाषा के लिए विशिष्ट व्हिस्पर मॉडल असाइन करें",
            "Test Voice Dictation & Transcription" to "आवाज़ श्रुतलेखन और परीक्षण",
            "Verify microphone recognition and model inference" to "माइक पहचान और मॉडल प्रदर्शन का परीक्षण करें",
            "Tap to Speak / Test" to "बोलने के लिए टैप करें",
            "Start test audio recording" to "परीक्षण ऑडियो रिकॉर्डिंग शुरू करें",
            "Stop Recording" to "रिकॉर्डिंग रोकें",
            "Stop active speech recording and run model" to "रिकॉर्डिंग रोकें और मॉडल निष्पादित करें",
            "Active Speech Input Engine" to "सक्रिय आवाज़ इनपुट इंजन",
            "Selected voice transcription provider" to "चुना गया वॉयस ट्रांसक्रिप्शन प्रदाता",
            "Google Speech Recognition & Synthesis" to "Google आवाज़ पहचान सेवा",
            "Official Google engine for ColorOS / Oppo & Android" to "ColorOS / Oppo और Android के लिए आधिकारिक Google इंजन",
            "Local Whisper Mode (Offline GGUF)" to "लोकल व्हिस्पर मोड (ऑफ़लाइन)",
            "100% private offline transcription" to "100% निजी ऑफ़लाइन ट्रांसक्रिप्शन",
            "FUTO Voice Input / Keyboard" to "FUTO वॉयस इनपुट / कीबोर्ड",
            "Fast neural offline voice typing" to "तेज़ ऑन-डिवाइस न्यूरल ऑफ़लाइन वॉयस टाइपिंग",
            "System Default Speech Recognizer" to "सिस्टम डिफ़ॉल्ट आवाज़ पहचानकर्ता",
            "Android platform speech service" to "Android प्लेटफ़ॉर्म डिफ़ॉल्ट स्पीच सेवा",
            "AI Web Assistant & Custom AI Services" to "AI वेब सहायक और कस्टम AI सेवाएं",
            "Web conversational LLM configurations" to "वेब वार्तालाप AI और LLM सेटिंग्स",
            "Switch AI" to "AI बदलें",
            "Select active AI provider" to "सक्रिय AI प्रदाता चुनें",
            "+ Add Custom AI" to "+ नया कस्टम AI जोड़ें",
            "Connect Ollama, Open WebUI, Grok, or custom endpoint" to "Ollama, Open WebUI, Grok या कस्टम लिंक जोड़ें",
            "Mascot Companion Selection" to "शुभंकर साथी का चयन",
            "Choose your friendly desktop assistant" to "अपना पसंदीदा प्यारा साथी चुनें",
            "Mascot Speech Bubbles & Petting" to "शुभंकर संवाद और सहलाना",
            "Show floating dialogue pills and affectionate responses" to "संवाद संदेश और प्यारे भाव दिखाएं",
            "Haptic Petting Feedback" to "हैप्टिक सहलाने का फीडबैक",
            "Subtle vibration pulse when petting mascot" to "शुभंकर से बातचीत पर हल्का कंपन फीडबैक",
            "Show Mascot Beside Top Chips" to "शीर्ष चिप्स के पास शुभंकर दिखाएं",
            "Display companion avatar in overlay action bar" to "ओवरले एक्शन बार में साथी का अवतार दिखाएं",
            "Show Mascot in Prompt Search Bar" to "प्रॉम्प्ट सर्च बार में शुभंकर दिखाएं",
            "Display companion avatar in web prompt input" to "वेब प्रॉम्प्ट इनपुट में साथी का अवतार दिखाएं",
            "Toast Notifications" to "टोस्ट सूचनाएं",
            "Floating mascot pill notifications" to "फ्लोटिंग शुभंकर पिल सूचनाएं",
            "About OpenAssist" to "OpenAssist के बारे में",
            "Open-source digital assistant information" to "ओपन-सोर्स डिजिटल सहायक की जानकारी",
            "All systems green! Ask me anything!" to "सब कुछ तैयार है! कुछ भी पूछें! ✨",
            "Default cheerful mascot status greeting" to "डिफ़ॉल्ट खुशमिजाज शुभंकर स्थिति संदेश",
            "Circle or box any element on screen" to "स्क्रीन पर किसी भी हिस्से पर घेरा बनाएं",
            "Instruction hint for screen capture selection" to "स्क्रीन कैप्चर चयन के लिए निर्देश संकेत",
            "Ready to help! Tap to pet me" to "मदद के लिए तैयार! प्यार से सहलाएं 💖",
            "Interactive pet mascot pill invitation" to "इंटरैक्टिव शुभंकर सहलाने का आमंत्रण",
            "Language & Community Translations" to "भाषा और समुदाय अनुवाद",
            "Customize languages and import community language packs" to "भाषाएं अनुकूलित करें और समुदाय अनुवाद पैक जोड़ें",
            "Mascot Speech & Dialogue Studio" to "शुभंकर संवाद स्टूडियो",
            "Customize and translate what mascots say across menus" to "शुभंकर क्या बोलते हैं उसे अनुकूलित और अनुवादित करें"
        )
    }

    var appLanguage: String
        get() = sp.getString(KEY_APP_LANG, "en") ?: "en"
        set(v) { sp.edit().putString(KEY_APP_LANG, v).apply() }

    var settingsLanguage: String
        get() = sp.getString(KEY_SETTINGS_LANG, "") ?: ""
        set(v) { sp.edit().putString(KEY_SETTINGS_LANG, v).apply() }

    var overlayLanguage: String
        get() = sp.getString(KEY_OVERLAY_LANG, "") ?: ""
        set(v) { sp.edit().putString(KEY_OVERLAY_LANG, v).apply() }

    var webUiLanguage: String
        get() = sp.getString(KEY_WEB_LANG, "") ?: ""
        set(v) { sp.edit().putString(KEY_WEB_LANG, v).apply() }

    var mascotLanguage: String
        get() = sp.getString(KEY_MASCOT_LANG, "") ?: ""
        set(v) { sp.edit().putString(KEY_MASCOT_LANG, v).apply() }

    fun getEffectiveLanguage(menuContext: String): String {
        val specific = when (menuContext.lowercase()) {
            "settings" -> settingsLanguage
            "overlay" -> overlayLanguage
            "web", "webui" -> webUiLanguage
            "mascot", "dialogue" -> mascotLanguage
            else -> ""
        }
        return if (specific.isNotBlank()) specific else appLanguage
    }

    fun getAvailableLanguages(): List<LanguageInfo> {
        val list = mutableListOf(
            LanguageInfo("en", "English", isBuiltIn = true),
            LanguageInfo("hinglish", "Hinglish (Hindi in English)", isBuiltIn = true),
            LanguageInfo("hi", "हिन्दी (Hindi)", isBuiltIn = true)
        )
        val raw = sp.getString(KEY_CUSTOM_LANGS_JSON, null) ?: return list
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val name = obj.optString(code, code)
                if (list.none { it.code == code }) {
                    list.add(LanguageInfo(code, name, isBuiltIn = false))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addCustomLanguage(code: String, name: String) {
        val cleanCode = code.lowercase().trim().replace(Regex("[^a-z0-9_-]"), "_")
        val cleanName = name.trim().ifBlank { cleanCode }
        val raw = sp.getString(KEY_CUSTOM_LANGS_JSON, "{}") ?: "{}"
        try {
            val obj = JSONObject(raw)
            obj.put(cleanCode, cleanName)
            sp.edit().putString(KEY_CUSTOM_LANGS_JSON, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeCustomLanguage(code: String) {
        val raw = sp.getString(KEY_CUSTOM_LANGS_JSON, "{}") ?: "{}"
        try {
            val obj = JSONObject(raw)
            obj.remove(code)
            sp.edit().putString(KEY_CUSTOM_LANGS_JSON, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Also remove translations map
        sp.edit().remove(PREFIX_TRANSLATION_MAP + code).apply()
        if (appLanguage == code) appLanguage = "en"
        if (settingsLanguage == code) settingsLanguage = ""
        if (overlayLanguage == code) overlayLanguage = ""
        if (webUiLanguage == code) webUiLanguage = ""
        if (mascotLanguage == code) mascotLanguage = ""
    }

    fun getTranslationMap(langCode: String): Map<String, String> {
        if (langCode == "en") return emptyMap()
        if (langCode == "hinglish") {
            val custom = loadStoredMap(langCode)
            return HINGLISH_MAP + custom
        }
        if (langCode == "hi") {
            val custom = loadStoredMap(langCode)
            return HINDI_MAP + custom
        }
        return loadStoredMap(langCode)
    }

    private fun loadStoredMap(langCode: String): Map<String, String> {
        val raw = sp.getString(PREFIX_TRANSLATION_MAP + langCode, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.getString(k)
                if (v.isNotBlank()) map[k] = v
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveStringOverride(langCode: String, englishKey: String, translatedValue: String) {
        val current = loadStoredMap(langCode).toMutableMap()
        if (translatedValue.isBlank()) {
            current.remove(englishKey)
        } else {
            current[englishKey] = translatedValue.trim()
        }
        saveStoredMap(langCode, current)
    }

    private fun saveStoredMap(langCode: String, map: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, v)
        }
        sp.edit().putString(PREFIX_TRANSLATION_MAP + langCode, obj.toString()).apply()
    }

    fun translate(englishText: String, menuContext: String = "general"): String {
        val targetLang = getEffectiveLanguage(menuContext)
        if (targetLang == "en" || targetLang.isBlank()) return englishText
        val map = getTranslationMap(targetLang)
        return map[englishText]?.ifBlank { englishText } ?: englishText
    }

    /**
     * Community Extensible Translation File Format:
     * Line 1: English source string
     * Line 2: Translated string (leave empty to fallback to English)
     * Line 3: Empty line separator
     */
    fun exportTemplateText(targetLangCode: String = "custom"): String {
        val existing = getTranslationMap(targetLangCode)
        val sb = StringBuilder()
        sb.append("# ====================================================================\n")
        sb.append("# OPENASSIST EXTENSIBLE COMMUNITY TRANSLATION FILE\n")
        sb.append("# Target Language: $targetLangCode\n")
        sb.append("# ====================================================================\n")
        sb.append("# INSTRUCTIONS FOR TRANSLATORS:\n")
        sb.append("# 1. Lines starting with # UI Context explain where text appears.\n")
        sb.append("# 2. Below each comment, line 1 is English text, line 2 is your translation.\n")
        sb.append("# 3. Keep 1 blank line between phrases for clean readability.\n")
        sb.append("# 4. If a translated line is left empty, the app defaults to English!\n")
        sb.append("# TIP: You can paste this entire file into Gemini / ChatGPT\n")
        sb.append("# to translate into your language or dialect, then import it back!\n")
        sb.append("# ====================================================================\n\n")

        for ((englishTitle, englishDesc) in DEFAULT_STRINGS) {
            sb.append("# UI Context: [Setting Title]\n")
            sb.append(englishTitle).append("\n")
            val transTitle = existing[englishTitle] ?: ""
            sb.append(transTitle).append("\n\n")

            sb.append("# UI Context: [Setting Description for \"").append(englishTitle).append("\"]\n")
            sb.append(englishDesc).append("\n")
            val transDesc = existing[englishDesc] ?: ""
            sb.append(transDesc).append("\n\n")
        }
        return sb.toString()
    }

    fun importTranslationText(content: String, targetLangCode: String): Int {
        val cleanLang = targetLangCode.lowercase().trim().replace(Regex("[^a-z0-9_-]"), "_")
        val lines = content.lines()
        val parsed = mutableMapOf<String, String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#") || line.isBlank()) {
                i++
                continue
            }
            // English key
            val englishKey = line
            // Translated value on next line if available
            val transLine = if (i + 1 < lines.size) lines[i + 1].trim() else ""
            if (transLine.isNotBlank() && !transLine.startsWith("#")) {
                parsed[englishKey] = transLine
                i += 2
            } else {
                i += 1
            }
            // Skip trailing empty lines
            while (i < lines.size && lines[i].trim().isBlank()) {
                i++
            }
        }
        val current = loadStoredMap(cleanLang).toMutableMap()
        current.putAll(parsed)
        saveStoredMap(cleanLang, current)
        return parsed.size
    }
}
