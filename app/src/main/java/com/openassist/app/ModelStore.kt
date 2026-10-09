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

    fun importModelFromUri(context: Context, uri: Uri, fileName: String, fileSize: Long): WhisperModel {
        val modelsDir = java.io.File(context.filesDir, "whisper_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val destFile = java.io.File(modelsDir, fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val finalPath = if (destFile.exists() && destFile.length() > 0) destFile.absolutePath else uri.toString()
        val finalSize = if (destFile.exists() && destFile.length() > 0) destFile.length() else fileSize

        val model = WhisperModel(
            id = "model_" + System.currentTimeMillis(),
            name = fileName,
            uriString = finalPath,
            sizeBytes = finalSize,
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

    fun removeModel(id: String) {
        val raw = sp.getString(KEY_MODELS_JSON, null)
        val current = parseModels(raw).filter { it.id != id }
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
