package com.openassist.app

data class AiService(
    val id: String,
    val name: String,
    val url: String,
    val isCustom: Boolean = false
)

/** The AI web apps the chat window can show. Add more here or use "Custom" in settings. */
object AiServices {

    const val CUSTOM_ID = "custom"

    val builtIn: List<AiService> = listOf(
        AiService("gemini", "Gemini", "https://gemini.google.com/app"),
        AiService("chatgpt", "ChatGPT", "https://chatgpt.com/"),
        AiService("claude", "Claude", "https://claude.ai/new"),
        AiService("perplexity", "Perplexity", "https://www.perplexity.ai/"),
        AiService("deepseek", "DeepSeek", "https://chat.deepseek.com/"),
        AiService("grok", "Grok", "https://grok.com/"),
        AiService("mistral", "Le Chat", "https://chat.mistral.ai/chat"),
        AiService("copilot", "Copilot", "https://copilot.microsoft.com/"),
        AiService("qwen", "Qwen", "https://chat.qwen.ai/")
    )

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("https://") || t.startsWith("http://") -> t
            else -> "https://$t"
        }
    }

    fun all(prefs: Prefs): List<AiService> {
        val customList = prefs.getCustomServices()
        val legacy = prefs.customUrl.takeIf { it.isNotBlank() && customList.none { c -> c.id == CUSTOM_ID } }?.let {
            AiService(CUSTOM_ID, "Custom AI", normalizeUrl(it), isCustom = true)
        }
        return builtIn + customList + listOfNotNull(legacy)
    }

    fun current(prefs: Prefs): AiService =
        all(prefs).firstOrNull { it.id == prefs.serviceId } ?: builtIn.first()
}
