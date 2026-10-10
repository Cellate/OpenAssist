package com.openassist.app

import android.content.Context
import android.content.SharedPreferences

enum class VoiceLang(val id: String, val tag: String, val label: String, val chip: String) {
    HINGLISH("hinglish", "hi", "Hinglish (Hindi in English letters)", "Hinglish"),
    HINDI("hi", "hi", "Hindi (Devanagari)", "HI"),
    ENGLISH("en", "en", "English", "EN");

    fun next(): VoiceLang = values()[(ordinal + 1) % values().size]

    companion object {
        fun fromId(id: String?): VoiceLang = values().firstOrNull { it.id == id } ?: HINGLISH
    }
}

class Prefs(private val context: Context) {
    companion object {
        const val ENGINE_DEFAULT = "system_default"
        const val ENGINE_LOCAL_MODEL = "local_model"
        const val ENGINE_CUSTOM_MODEL = "local_model"
        const val ENGINE_GOOGLE_TTS = "google_tts"
        const val ENGINE_FUTO_VOICE_INPUT = "futo_voice_input"
        const val ENGINE_FUTO_KEYBOARD = "futo_voice_input"

        const val MODE_POST_PROCESSING = "post_processing"
        const val MODE_REAL_TIME = "real_time"
    }

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("open_assist", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = sp.getBoolean("dark_mode", true)
        set(v) { sp.edit().putBoolean("dark_mode", v).apply() }

    var processingMode: String
        get() = sp.getString("processing_mode", MODE_POST_PROCESSING) ?: MODE_POST_PROCESSING
        set(v) { sp.edit().putString("processing_mode", v).apply() }

    val isPostProcessing: Boolean
        get() = processingMode == MODE_POST_PROCESSING

    val modelStore: ModelStore
        get() = ModelStore(context)

    var serviceId: String
        get() = sp.getString("service", "gemini") ?: "gemini"
        set(v) { sp.edit().putString("service", v).apply() }

    var customUrl: String
        get() = sp.getString("custom_url", "") ?: ""
        set(v) { sp.edit().putString("custom_url", v).apply() }

    fun getCustomServices(): List<AiService> {
        val raw = sp.getString("custom_services_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<AiService>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AiService(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        isCustom = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomServices(services: List<AiService>) {
        val arr = org.json.JSONArray()
        for (s in services) {
            val obj = org.json.JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
            }
            arr.put(obj)
        }
        sp.edit().putString("custom_services_json", arr.toString()).apply()
    }

    fun addCustomService(name: String, url: String): AiService {
        val normalized = AiServices.normalizeUrl(url)
        val id = "custom_" + System.currentTimeMillis()
        val newService = AiService(id, name.trim().ifBlank { "Custom AI" }, normalized, isCustom = true)
        val current = getCustomServices().toMutableList()
        current.add(newService)
        saveCustomServices(current)
        serviceId = newService.id
        return newService
    }

    fun removeCustomService(id: String) {
        val current = getCustomServices().filter { it.id != id }
        saveCustomServices(current)
        if (serviceId == id) {
            serviceId = "gemini"
        }
    }

    var voiceLang: VoiceLang
        get() = VoiceLang.fromId(sp.getString("voice_lang", "hinglish"))
        set(v) { sp.edit().putString("voice_lang", v.id).apply() }

    var recognizer: String
        get() = sp.getString("recognizer", ENGINE_DEFAULT) ?: ENGINE_DEFAULT
        set(v) { sp.edit().putString("recognizer", v).apply() }

    var customModelName: String
        get() = getActiveLocalModel()?.name ?: ""
        set(_) {}

    var customModelPath: String
        get() = getActiveLocalModel()?.uriString ?: ""
        set(_) {}

    var newChatOnAssist: Boolean
        get() = sp.getBoolean("new_chat", true)
        set(v) { sp.edit().putBoolean("new_chat", v).apply() }

    var autoAttach: Boolean
        get() = sp.getBoolean("auto_attach", true)
        set(v) { sp.edit().putBoolean("auto_attach", v).apply() }

    var autoSubmit: Boolean
        get() = sp.getBoolean("auto_submit", false)
        set(v) { sp.edit().putBoolean("auto_submit", v).apply() }

    var composeAfterCircle: Boolean
        get() = sp.getBoolean("compose_after_circle", true)
        set(v) { sp.edit().putBoolean("compose_after_circle", v).apply() }

    var autoStartMic: Boolean
        get() = sp.getBoolean("auto_start_mic", false)
        set(v) { sp.edit().putBoolean("auto_start_mic", v).apply() }

    var firstRunDone: Boolean
        get() = sp.getBoolean("first_run_done", false)
        set(v) { sp.edit().putBoolean("first_run_done", v).apply() }

    var showMascotTopChips: Boolean
        get() = sp.getBoolean("show_mascot_top_chips", true)
        set(v) { sp.edit().putBoolean("show_mascot_top_chips", v).apply() }

    var showMascotSearchPill: Boolean
        get() = sp.getBoolean("show_mascot_search_pill", true)
        set(v) { sp.edit().putBoolean("show_mascot_search_pill", v).apply() }

    var showMascotSpeechBubble: Boolean
        get() = sp.getBoolean("show_mascot_speech_bubble", true)
        set(v) { sp.edit().putBoolean("show_mascot_speech_bubble", v).apply() }

    var hapticFeedbackEnabled: Boolean
        get() = sp.getBoolean("haptic_feedback_enabled", true)
        set(v) { sp.edit().putBoolean("haptic_feedback_enabled", v).apply() }

    var mascotStyle: String
        get() = sp.getString("mascot_style", "owl") ?: "owl"
        set(v) { sp.edit().putString("mascot_style", v).apply() }

    val isLocalModelMode: Boolean
        get() = recognizer == ENGINE_LOCAL_MODEL

    fun isFutoActive(): Boolean = recognizer == ENGINE_FUTO_VOICE_INPUT

    val isGoogleActive: Boolean
        get() = recognizer == ENGINE_GOOGLE_TTS || recognizer == ENGINE_DEFAULT || recognizer.isEmpty()

    fun getActiveLocalModel(): WhisperModel? {
        val store = ModelStore(context)
        val def = store.getDefaultModel()
        val langModel = store.getModelForLang(voiceLang)
        return def ?: langModel
    }

    fun getEngineChipLabel(): String {
        return when {
            isLocalModelMode -> {
                val model = getActiveLocalModel()
                if (model != null) "Local (${model.name.substringBeforeLast('.').take(10)})" else "Local Model"
            }
            recognizer == ENGINE_GOOGLE_TTS -> "Google"
            recognizer == ENGINE_FUTO_VOICE_INPUT -> "FUTO"
            else -> "Default"
        }
    }

    fun toggleEngine(): String {
        val store = ModelStore(context)
        return if (isLocalModelMode) {
            recognizer = ENGINE_GOOGLE_TTS
            "Google"
        } else {
            if (store.getModels().isNotEmpty()) {
                recognizer = ENGINE_LOCAL_MODEL
                "Local Model Mode"
            } else {
                recognizer = ENGINE_GOOGLE_TTS
                "Google"
            }
        }
    }
}
