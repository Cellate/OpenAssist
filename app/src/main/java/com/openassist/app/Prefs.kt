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

    private fun isSystemDark(): Boolean {
        val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    var isDarkMode: Boolean
        get() {
            if (!sp.contains("dark_mode")) {
                return isSystemDark()
            }
            return sp.getBoolean("dark_mode", isSystemDark())
        }
        set(v) { sp.edit().putBoolean("dark_mode", v).apply() }

    var processingMode: String
        get() = sp.getString("processing_mode", MODE_POST_PROCESSING) ?: MODE_POST_PROCESSING
        set(v) { sp.edit().putString("processing_mode", v).apply() }

    val isPostProcessing: Boolean
        get() = processingMode == MODE_POST_PROCESSING

    val modelStore: ModelStore
        get() = ModelStore(context)

    val translationStore: TranslationStore
        get() = TranslationStore(context)

    val mascotDialogueStore: MascotDialogueStore
        get() = MascotDialogueStore(context)

    var appLanguage: String
        get() = translationStore.appLanguage
        set(v) { translationStore.appLanguage = v }

    var settingsLanguage: String
        get() = translationStore.settingsLanguage
        set(v) { translationStore.settingsLanguage = v }

    var overlayLanguage: String
        get() = translationStore.overlayLanguage
        set(v) { translationStore.overlayLanguage = v }

    var webUiLanguage: String
        get() = translationStore.webUiLanguage
        set(v) { translationStore.webUiLanguage = v }

    var mascotLanguage: String
        get() = translationStore.mascotLanguage
        set(v) { translationStore.mascotLanguage = v }

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
                list.add(AiService.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomServices(services: List<AiService>) {
        val arr = org.json.JSONArray()
        for (s in services) {
            arr.put(s.toJson())
        }
        sp.edit().putString("custom_services_json", arr.toString()).apply()
    }

    fun addCustomService(
        name: String,
        url: String,
        category: ServiceCategory = ServiceCategory.CUSTOM,
        openInApp: Boolean = false,
        packageName: String? = null,
        deepLinkTemplate: String? = null,
        searchUrlTemplate: String? = null
    ): AiService {
        val normalized = AiServices.normalizeUrl(url)
        val id = "custom_" + System.currentTimeMillis()
        val template = if (!searchUrlTemplate.isNullOrBlank()) {
            searchUrlTemplate
        } else if (url.contains("{q}")) {
            url
        } else {
            val sep = if (normalized.contains("?")) "&" else "?"
            "$normalized${sep}q={q}"
        }
        val newService = AiService(
            id = id,
            name = name.trim().ifBlank { "Custom Web" },
            url = normalized,
            isCustom = true,
            category = category,
            isEnabled = true,
            openInApp = openInApp,
            packageName = packageName?.trim()?.takeIf { it.isNotBlank() },
            deepLinkTemplate = deepLinkTemplate?.trim()?.takeIf { it.isNotBlank() },
            searchUrlTemplate = template
        )
        val current = getCustomServices().toMutableList()
        current.add(newService)
        saveCustomServices(current)
        serviceId = newService.id
        return newService
    }

    fun updateCustomService(service: AiService) {
        val current = getCustomServices().map { if (it.id == service.id) service else it }
        saveCustomServices(current)
    }

    fun removeCustomService(id: String) {
        val current = getCustomServices().filter { it.id != id }
        saveCustomServices(current)
        if (serviceId == id) {
            serviceId = "gemini"
        }
    }

    fun getServiceOrder(): List<String> {
        val raw = sp.getString("service_order_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveServiceOrder(order: List<String>) {
        val arr = org.json.JSONArray()
        for (id in order) {
            arr.put(id)
        }
        sp.edit().putString("service_order_json", arr.toString()).apply()
    }

    fun getServiceOverrides(): Map<String, org.json.JSONObject> {
        val raw = sp.getString("service_overrides_json", null) ?: return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            val map = mutableMapOf<String, org.json.JSONObject>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getJSONObject(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setServiceEnabled(id: String, enabled: Boolean) {
        val overrides = org.json.JSONObject(sp.getString("service_overrides_json", "{}") ?: "{}")
        val current = overrides.optJSONObject(id) ?: org.json.JSONObject()
        current.put("isEnabled", enabled)
        overrides.put(id, current)
        sp.edit().putString("service_overrides_json", overrides.toString()).apply()

        // Also check custom services if it's custom
        val customList = getCustomServices()
        if (customList.any { it.id == id }) {
            saveCustomServices(customList.map { if (it.id == id) it.copy(isEnabled = enabled) else it })
        }
    }

    fun setServiceOpenInApp(id: String, openInApp: Boolean) {
        val overrides = org.json.JSONObject(sp.getString("service_overrides_json", "{}") ?: "{}")
        val current = overrides.optJSONObject(id) ?: org.json.JSONObject()
        current.put("openInApp", openInApp)
        overrides.put(id, current)
        sp.edit().putString("service_overrides_json", overrides.toString()).apply()

        // Also check custom services if it's custom
        val customList = getCustomServices()
        if (customList.any { it.id == id }) {
            saveCustomServices(customList.map { if (it.id == id) it.copy(openInApp = openInApp) else it })
        }
    }

    fun resetServiceCustomizations() {
        sp.edit()
            .remove("service_order_json")
            .remove("service_overrides_json")
            .apply()
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

    var showSpeechBubble: Boolean
        get() = showMascotSpeechBubble
        set(v) { showMascotSpeechBubble = v }

    var enableTranslit: Boolean
        get() = sp.getBoolean("enable_translit", false)
        set(v) { sp.edit().putBoolean("enable_translit", v).apply() }

    var hapticFeedbackEnabled: Boolean
        get() = sp.getBoolean("haptic_feedback_enabled", true)
        set(v) { sp.edit().putBoolean("haptic_feedback_enabled", v).apply() }

    var mascotStyle: String
        get() = sp.getString("mascot_style", "owl-orbital") ?: "owl-orbital"
        set(v) { sp.edit().putString("mascot_style", v).apply() }

    fun getAssistantHistory(): List<AssistantHistoryItem> {
        val stored = sp.getString("assistant_history_json", null) ?: return emptyList()
        val raw = SecurityHelper.decrypt(stored)
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<AssistantHistoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AssistantHistoryItem(
                        id = obj.getString("id"),
                        query = obj.getString("query"),
                        serviceId = obj.optString("serviceId", "gemini"),
                        serviceName = obj.optString("serviceName", "Gemini"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        url = obj.optString("url", "")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    var isHistoryEnabled: Boolean
        get() = sp.getBoolean("is_history_enabled", true)
        set(v) { sp.edit().putBoolean("is_history_enabled", v).apply() }

    fun addAssistantHistory(query: String, serviceId: String, serviceName: String, url: String = "") {
        if (!isHistoryEnabled || query.isBlank()) return
        val item = AssistantHistoryItem(
            id = "hist_" + System.currentTimeMillis() + "_" + (100..999).random(),
            query = query.trim(),
            serviceId = serviceId,
            serviceName = serviceName,
            timestamp = System.currentTimeMillis(),
            url = url
        )
        val current = getAssistantHistory().filter { !it.query.equals(query.trim(), ignoreCase = true) }.toMutableList()
        current.add(0, item)
        val trimmed = if (current.size > 60) current.subList(0, 60) else current
        saveAssistantHistory(trimmed)
    }

    fun removeAssistantHistory(id: String) {
        val current = getAssistantHistory().filter { it.id != id }
        saveAssistantHistory(current)
    }

    
    // Toast Notification preferences
    var toastMascotPillEnabled: Boolean
        get() = sp.getBoolean("toast_mascot_pill_enabled", true)
        set(v) { sp.edit().putBoolean("toast_mascot_pill_enabled", v).apply() }

    var toastSpeechEnabled: Boolean
        get() = sp.getBoolean("toast_speech_enabled", true)
        set(v) { sp.edit().putBoolean("toast_speech_enabled", v).apply() }

    var toastCircleEnabled: Boolean
        get() = sp.getBoolean("toast_circle_enabled", true)
        set(v) { sp.edit().putBoolean("toast_circle_enabled", v).apply() }

    var toastAssistantEnabled: Boolean
        get() = sp.getBoolean("toast_assistant_enabled", true)
        set(v) { sp.edit().putBoolean("toast_assistant_enabled", v).apply() }

    var toastGeneralEnabled: Boolean
        get() = sp.getBoolean("toast_general_enabled", true)
        set(v) { sp.edit().putBoolean("toast_general_enabled", v).apply() }

    fun updateLatestAssistantHistoryUrl(newUrl: String) {
        if (!isHistoryEnabled || newUrl.isBlank() || newUrl.equals("about:blank", ignoreCase = true)) return
        val current = getAssistantHistory().toMutableList()
        if (current.isEmpty()) return
        val first = current.first()
        if (first.url == newUrl) return
        current[0] = first.copy(url = newUrl)
        saveAssistantHistory(current)
    }

    fun clearAssistantHistory() {
        sp.edit().remove("assistant_history_json").apply()
    }

    private fun saveAssistantHistory(list: List<AssistantHistoryItem>) {
        val arr = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("query", item.query)
                put("serviceId", item.serviceId)
                put("serviceName", item.serviceName)
                put("timestamp", item.timestamp)
                put("url", item.url)
            }
            arr.put(obj)
        }
        val encrypted = SecurityHelper.encrypt(arr.toString())
        sp.edit().putString("assistant_history_json", encrypted).apply()
    }

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

    var isAutoSpeakEnabled: Boolean
        get() = sp.getBoolean("auto_speak_enabled", true)
        set(v) { sp.edit().putBoolean("auto_speak_enabled", v).apply() }

    var speechRate: Float
        get() = sp.getFloat("auto_speak_rate", 1.0f)
        set(v) { sp.edit().putFloat("auto_speak_rate", v).apply() }

    var isAudioBarMinimized: Boolean
        get() = sp.getBoolean("audio_bar_minimized", false)
        set(v) { sp.edit().putBoolean("audio_bar_minimized", v).apply() }

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
