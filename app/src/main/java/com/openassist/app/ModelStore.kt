package com.openassist.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class WhisperModel(
    val id: String,
    val name: String,
    val uriString: String,
    val sizeBytes: Long = 0L,
    val addedTime: Long = System.currentTimeMillis()
) {
    val sizeMb: String
        get() = if (sizeBytes > 0) String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0)) else ""
}

class ModelStore(private val context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("openassist_models", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MODELS_JSON = "models_list_json"
        private const val KEY_DEFAULT_MODEL_ID = "default_model_id"
        private const val KEY_MODEL_HINGLISH = "model_hinglish"
        private const val KEY_MODEL_HINDI = "model_hindi"
        private const val KEY_MODEL_ENGLISH = "model_english"
    }

    private fun parseModels(raw: String?): List<WhisperModel> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<WhisperModel>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WhisperModel(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        uriString = obj.getString("uri"),
                        sizeBytes = obj.optLong("size", 0L),
                        addedTime = obj.optLong("added", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getModels(): List<WhisperModel> {
        val raw = sp.getString(KEY_MODELS_JSON, null)
        return parseModels(raw)
    }

    fun cycleModel(): WhisperModel? {
        val models = getModels()
        if (models.isEmpty()) return null
        val currentId = getDefaultModelId()
        val currentIndex = models.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % models.size else 0
        val nextModel = models[nextIndex]
        setDefaultModelId(nextModel.id)
        return nextModel
    }

    fun importModelFromUri(context: Context, uri: Uri): WhisperModel {
        var fileName = "whisper_model_${System.currentTimeMillis()}.bin"
        var fileSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        val n = cursor.getString(nameIndex)
                        if (!n.isNullOrBlank()) fileName = n
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return importModelFromUri(context, uri, fileName, fileSize)
    }

    fun importModelFromUri(context: Context, uri: Uri, fileName: String, fileSize: Long): WhisperModel {
        val safeName = java.io.File(fileName).name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "whisper_model_${System.currentTimeMillis()}.bin" }
        val modelsDir = java.io.File(context.filesDir, "whisper_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val destFile = java.io.File(modelsDir, safeName)

        var copyError: Exception? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            copyError = e
        }

        if (copyError != null) {
            throw java.io.IOException("Could not read file from storage: ${copyError.message}", copyError)
        }
        if (!destFile.exists() || destFile.length() == 0L) {
            throw java.io.IOException("Model binary could not be written or is 0 bytes.")
        }

        val finalPath = destFile.absolutePath
        val finalSize = destFile.length()

        val model = WhisperModel(
            id = "model_" + System.currentTimeMillis(),
            name = safeName,
            uriString = finalPath,
            sizeBytes = finalSize,
            addedTime = System.currentTimeMillis()
        )
        addModel(model)
        return model
    }

    fun addPresetModel(name: String, sizeBytes: Long, note: String = "Pre-loaded Model"): WhisperModel {
        val safeName = java.io.File(name).name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val modelsDir = java.io.File(context.filesDir, "whisper_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val destFile = java.io.File(modelsDir, safeName)
        if (!destFile.exists() || destFile.length() == 0L) {
            try {
                destFile.writeText("// OpenAssist Whisper Model: $safeName ($note)\n")
            } catch (e: Exception) {}
        }
        val model = WhisperModel(
            id = "model_" + safeName.replace(".", "_"),
            name = safeName,
            uriString = destFile.absolutePath,
            sizeBytes = sizeBytes,
            addedTime = System.currentTimeMillis()
        )
        addModel(model)
        return model
    }

    fun addModel(model: WhisperModel) {
        val raw = sp.getString(KEY_MODELS_JSON, null)
        val current = parseModels(raw).filter { it.id != model.id }.toMutableList()
        current.add(0, model)
        saveModels(current)
        if (getDefaultModelId().isNullOrBlank()) {
            setDefaultModelId(model.id)
        }
        if (getModelIdForLang(VoiceLang.HINGLISH).isNullOrBlank()) {
            setModelIdForLang(VoiceLang.HINGLISH, model.id)
        }
        if (getModelIdForLang(VoiceLang.HINDI).isNullOrBlank()) {
            setModelIdForLang(VoiceLang.HINDI, model.id)
        }
        if (getModelIdForLang(VoiceLang.ENGLISH).isNullOrBlank()) {
            setModelIdForLang(VoiceLang.ENGLISH, model.id)
        }
    }

    fun removeModel(id: String): Boolean {
        val raw = sp.getString(KEY_MODELS_JSON, null)
        val models = parseModels(raw)
        val target = models.firstOrNull { it.id == id }
        if (target != null) {
            try {
                val f = java.io.File(target.uriString)
                if (f.exists() && f.isFile) {
                    f.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val current = models.filter { it.id != id }
        saveModels(current)
        val firstId = current.firstOrNull()?.id
        if (getDefaultModelId() == id) {
            setDefaultModelId(firstId)
        }
        if (getModelIdForLang(VoiceLang.HINGLISH) == id) {
            setModelIdForLang(VoiceLang.HINGLISH, firstId)
        }
        if (getModelIdForLang(VoiceLang.HINDI) == id) {
            setModelIdForLang(VoiceLang.HINDI, firstId)
        }
        if (getModelIdForLang(VoiceLang.ENGLISH) == id) {
            setModelIdForLang(VoiceLang.ENGLISH, firstId)
        }
        // Also update any dynamic language bindings
        val dynamicKeys = getDynamicBoundLanguages()
        for (k in dynamicKeys) {
            if (getDynamicModelId(k) == id) {
                setDynamicModelId(k, firstId)
            }
        }
        return true
    }

    fun getModel(id: String?): WhisperModel? {
        if (id.isNullOrBlank()) return null
        return getModels().firstOrNull { it.id == id }
    }

    fun getDefaultModelId(): String? = sp.getString(KEY_DEFAULT_MODEL_ID, null)

    fun setDefaultModelId(id: String?) {
        sp.edit().putString(KEY_DEFAULT_MODEL_ID, id).apply()
    }

    fun getDefaultModel(): WhisperModel? {
        val models = getModels()
        if (models.isEmpty()) return null
        val defaultId = getDefaultModelId()
        return models.firstOrNull { it.id == defaultId } ?: models.first()
    }

    fun getModelIdForLang(lang: VoiceLang): String? {
        return when (lang) {
            VoiceLang.HINGLISH -> sp.getString(KEY_MODEL_HINGLISH, null)
            VoiceLang.HINDI -> sp.getString(KEY_MODEL_HINDI, null)
            VoiceLang.ENGLISH -> sp.getString(KEY_MODEL_ENGLISH, null)
        }
    }

    fun setModelIdForLang(lang: VoiceLang, modelId: String?) {
        val key = when (lang) {
            VoiceLang.HINGLISH -> KEY_MODEL_HINGLISH
            VoiceLang.HINDI -> KEY_MODEL_HINDI
            VoiceLang.ENGLISH -> KEY_MODEL_ENGLISH
        }
        sp.edit().putString(key, modelId).apply()
    }

    fun getModelForLang(lang: VoiceLang): WhisperModel? {
        val models = getModels()
        if (models.isEmpty()) return null
        val id = getModelIdForLang(lang)
        return models.firstOrNull { it.id == id } ?: getDefaultModel()
    }

    // Dynamic per-language bindings for any custom or standard language code
    fun getDynamicModelId(langCode: String): String? {
        val normalized = langCode.lowercase().trim()
        when (normalized) {
            "hinglish" -> return getModelIdForLang(VoiceLang.HINGLISH)
            "hi", "hindi" -> return getModelIdForLang(VoiceLang.HINDI)
            "en", "english" -> return getModelIdForLang(VoiceLang.ENGLISH)
        }
        return sp.getString("model_bind_$normalized", null) ?: getDefaultModelId()
    }

    fun setDynamicModelId(langCode: String, modelId: String?) {
        val normalized = langCode.lowercase().trim()
        when (normalized) {
            "hinglish" -> { setModelIdForLang(VoiceLang.HINGLISH, modelId); return }
            "hi", "hindi" -> { setModelIdForLang(VoiceLang.HINDI, modelId); return }
            "en", "english" -> { setModelIdForLang(VoiceLang.ENGLISH, modelId); return }
        }
        sp.edit().putString("model_bind_$normalized", modelId).apply()
    }

    fun getDynamicBoundLanguages(): List<String> {
        val all = sp.all
        val list = mutableListOf<String>()
        for (k in all.keys) {
            if (k.startsWith("model_bind_")) {
                list.add(k.removePrefix("model_bind_"))
            }
        }
        return list
    }

    fun getRemovedStandardLanguages(): Set<String> {
        return sp.getStringSet("removed_standard_languages", emptySet()) ?: emptySet()
    }

    fun removeLanguageBinding(langCode: String) {
        val normalized = langCode.lowercase().trim()
        val standardCodes = setOf("hinglish", "hi", "hindi", "en", "english")
        if (standardCodes.contains(normalized)) {
            val removed = getRemovedStandardLanguages().toMutableSet()
            removed.add(normalized)
            sp.edit().putStringSet("removed_standard_languages", removed).apply()
        }
        removeDynamicBinding(normalized)
    }

    fun restoreDefaultLanguages() {
        sp.edit().remove("removed_standard_languages").apply()
    }

    fun getAllBoundLanguages(): List<Triple<String, String, Boolean>> {
        val removed = getRemovedStandardLanguages()
        val result = mutableListOf<Triple<String, String, Boolean>>()
        if (!removed.contains("hinglish")) {
            result.add(Triple("hinglish", "Hinglish (Default)", true))
        }
        if (!removed.contains("hi") && !removed.contains("hindi")) {
            result.add(Triple("hi", "Hindi (Secondary)", true))
        }
        if (!removed.contains("en") && !removed.contains("english")) {
            result.add(Triple("en", "English", true))
        }
        for (custom in getCustomLanguageList()) {
            result.add(Triple(custom.first, custom.second, false))
        }
        return result
    }

    fun removeDynamicBinding(langCode: String) {
        val normalized = langCode.lowercase().trim()
        sp.edit().remove("model_bind_$normalized").apply()
        val customLangs = getCustomLanguageList().filter { it.first != normalized }
        saveCustomLanguageList(customLangs)
    }

    fun getCustomLanguageList(): List<Pair<String, String>> {
        val raw = sp.getString("custom_bound_languages_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.getString("code"), obj.getString("name")))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCustomLanguageBinding(code: String, name: String, modelId: String?) {
        val normalized = code.lowercase().trim()
        // If it was previously removed standard language, un-remove it
        val removed = getRemovedStandardLanguages().toMutableSet()
        if (removed.contains(normalized)) {
            removed.remove(normalized)
            sp.edit().putStringSet("removed_standard_languages", removed).apply()
        }
        val list = getCustomLanguageList().filter { it.first != normalized }.toMutableList()
        list.add(Pair(normalized, name.trim().ifBlank { normalized }))
        saveCustomLanguageList(list)
        setDynamicModelId(normalized, modelId)
    }

    private fun saveCustomLanguageList(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().apply {
                put("code", p.first)
                put("name", p.second)
            })
        }
        sp.edit().putString("custom_bound_languages_json", arr.toString()).apply()
    }

    private fun saveModels(models: List<WhisperModel>) {
        val arr = JSONArray()
        for (m in models) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("uri", m.uriString)
                put("size", m.sizeBytes)
                put("added", m.addedTime)
            }
            arr.put(obj)
        }
        sp.edit().putString(KEY_MODELS_JSON, arr.toString()).apply()
    }
}
