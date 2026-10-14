package com.openassist.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

enum class ServiceCategory(val displayName: String) {
    AI("AI Models"),
    SEARCH("Search & Knowledge"),
    SOCIAL("Social & Media"),
    PRODUCTIVITY("Productivity"),
    CUSTOM("Custom")
}

data class AiService(
    val id: String,
    val name: String,
    val url: String,
    val isCustom: Boolean = false,
    val category: ServiceCategory = ServiceCategory.AI,
    val isEnabled: Boolean = true,
    val openInApp: Boolean = false,
    val packageName: String? = null,
    val deepLinkTemplate: String? = null,
    val searchUrlTemplate: String? = null
) {
    fun getSearchUrl(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        val template = when {
            !searchUrlTemplate.isNullOrBlank() -> searchUrlTemplate
            url.contains("{q}") -> url
            else -> null
        }
        return if (template != null) {
            val encoded = Uri.encode(trimmed)
            template.replace("{q}", encoded)
        } else if (trimmed.isNotBlank() && category != ServiceCategory.AI) {
            val sep = if (url.contains("?")) "&" else "?"
            val encoded = Uri.encode(trimmed)
            "$url${sep}q=$encoded"
        } else {
            url
        }
    }

    fun getAppDeepLinkUri(rawQuery: String): String? {
        val template = deepLinkTemplate ?: return null
        val encoded = Uri.encode(rawQuery.trim())
        return template.replace("{q}", encoded)
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("url", url)
        json.put("isCustom", isCustom)
        json.put("category", category.name)
        json.put("isEnabled", isEnabled)
        json.put("openInApp", openInApp)
        if (packageName != null) json.put("packageName", packageName)
        if (deepLinkTemplate != null) json.put("deepLinkTemplate", deepLinkTemplate)
        if (searchUrlTemplate != null) json.put("searchUrlTemplate", searchUrlTemplate)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): AiService {
            val catName = json.optString("category", ServiceCategory.CUSTOM.name)
            val catEnum = try {
                ServiceCategory.valueOf(catName)
            } catch (e: Exception) {
                ServiceCategory.CUSTOM
            }
            return AiService(
                id = json.getString("id"),
                name = json.getString("name"),
                url = json.getString("url"),
                isCustom = json.optBoolean("isCustom", false),
                category = catEnum,
                isEnabled = json.optBoolean("isEnabled", true),
                openInApp = json.optBoolean("openInApp", false),
                packageName = json.optString("packageName").takeIf { !it.isNullOrBlank() },
                deepLinkTemplate = json.optString("deepLinkTemplate").takeIf { !it.isNullOrBlank() },
                searchUrlTemplate = json.optString("searchUrlTemplate").takeIf { !it.isNullOrBlank() }
            )
        }
    }
}

object AiServices {
    const val CUSTOM_ID = "custom"

    val builtIn: List<AiService> = listOf(
        // 1. AI Models
        AiService(
            id = "gemini",
            name = "Gemini",
            url = "https://gemini.google.com/app",
            category = ServiceCategory.AI,
            packageName = "com.google.android.apps.bard"
        ),
        AiService(
            id = "chatgpt",
            name = "ChatGPT",
            url = "https://chatgpt.com/",
            category = ServiceCategory.AI,
            packageName = "com.openai.chatgpt"
        ),
        AiService(
            id = "claude",
            name = "Claude",
            url = "https://claude.ai/new",
            category = ServiceCategory.AI,
            packageName = "com.anthropic.claude"
        ),
        AiService(
            id = "perplexity",
            name = "Perplexity",
            url = "https://www.perplexity.ai/search?q={q}",
            category = ServiceCategory.AI,
            searchUrlTemplate = "https://www.perplexity.ai/search?q={q}",
            packageName = "ai.perplexity.app.android"
        ),
        AiService(
            id = "deepseek",
            name = "DeepSeek",
            url = "https://chat.deepseek.com/",
            category = ServiceCategory.AI,
            packageName = "com.deepseek.chat"
        ),
        AiService(
            id = "grok",
            name = "Grok",
            url = "https://grok.com/",
            category = ServiceCategory.AI
        ),
        AiService(
            id = "mistral",
            name = "Le Chat",
            url = "https://chat.mistral.ai/chat",
            category = ServiceCategory.AI
        ),
        AiService(
            id = "copilot",
            name = "Copilot",
            url = "https://copilot.microsoft.com/",
            category = ServiceCategory.AI,
            packageName = "com.microsoft.copilot"
        ),
        AiService(
            id = "qwen",
            name = "Qwen",
            url = "https://chat.qwen.ai/",
            category = ServiceCategory.AI
        ),
        AiService(
            id = "ollama",
            name = "Ollama (Local)",
            url = "http://localhost:11434/",
            category = ServiceCategory.AI
        ),
        AiService(
            id = "huggingchat",
            name = "HuggingChat",
            url = "https://huggingface.co/chat/",
            category = ServiceCategory.AI
        ),
        AiService(
            id = "poe",
            name = "Poe",
            url = "https://poe.com/",
            category = ServiceCategory.AI,
            packageName = "com.poe.android"
        ),

        // 2. Search & Knowledge
        AiService(
            id = "google",
            name = "Google",
            url = "https://www.google.com/search?q={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.google.android.googlequicksearchbox",
            deepLinkTemplate = "https://www.google.com/search?q={q}",
            searchUrlTemplate = "https://www.google.com/search?q={q}"
        ),
        AiService(
            id = "youtube",
            name = "YouTube",
            url = "https://www.youtube.com/results?search_query={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.google.android.youtube",
            deepLinkTemplate = "vnd.youtube://results?search_query={q}",
            searchUrlTemplate = "https://www.youtube.com/results?search_query={q}"
        ),
        AiService(
            id = "reddit",
            name = "Reddit",
            url = "https://www.reddit.com/search/?q={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.reddit.frontpage",
            deepLinkTemplate = "https://www.reddit.com/search/?q={q}",
            searchUrlTemplate = "https://www.reddit.com/search/?q={q}"
        ),
        AiService(
            id = "x",
            name = "X (Twitter)",
            url = "https://x.com/search?q={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.twitter.android",
            deepLinkTemplate = "twitter://search?query={q}",
            searchUrlTemplate = "https://x.com/search?q={q}"
        ),
        AiService(
            id = "wikipedia",
            name = "Wikipedia",
            url = "https://en.wikipedia.org/w/index.php?search={q}",
            category = ServiceCategory.SEARCH,
            packageName = "org.wikipedia",
            searchUrlTemplate = "https://en.wikipedia.org/w/index.php?search={q}"
        ),
        AiService(
            id = "github",
            name = "GitHub",
            url = "https://github.com/search?q={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.github.android",
            searchUrlTemplate = "https://github.com/search?q={q}"
        ),
        AiService(
            id = "duckduckgo",
            name = "DuckDuckGo",
            url = "https://duckduckgo.com/?q={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.duckduckgo.mobile.android",
            searchUrlTemplate = "https://duckduckgo.com/?q={q}"
        ),
        AiService(
            id = "stackoverflow",
            name = "StackOverflow",
            url = "https://stackoverflow.com/search?q={q}",
            category = ServiceCategory.SEARCH,
            searchUrlTemplate = "https://stackoverflow.com/search?q={q}"
        ),
        AiService(
            id = "arxiv",
            name = "ArXiv",
            url = "https://arxiv.org/search/?query={q}&searchtype=all",
            category = ServiceCategory.SEARCH,
            searchUrlTemplate = "https://arxiv.org/search/?query={q}&searchtype=all"
        ),
        AiService(
            id = "quora",
            name = "Quora",
            url = "https://www.quora.com/search?q={q}",
            category = ServiceCategory.SEARCH,
            packageName = "com.quora.android",
            searchUrlTemplate = "https://www.quora.com/search?q={q}"
        ),

        // 3. Social & Media
        AiService(
            id = "instagram",
            name = "Instagram",
            url = "https://www.instagram.com/explore/search/permalink/?query={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.instagram.android",
            deepLinkTemplate = "instagram://search?query={q}",
            searchUrlTemplate = "https://www.instagram.com/explore/search/permalink/?query={q}"
        ),
        AiService(
            id = "tiktok",
            name = "TikTok",
            url = "https://www.tiktok.com/search?q={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.zhiliaoapp.musically",
            deepLinkTemplate = "snssdk1128://search?keyword={q}",
            searchUrlTemplate = "https://www.tiktok.com/search?q={q}"
        ),
        AiService(
            id = "facebook",
            name = "Facebook",
            url = "https://www.facebook.com/search/top/?q={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.facebook.katana",
            deepLinkTemplate = "fb://search/top?q={q}",
            searchUrlTemplate = "https://www.facebook.com/search/top/?q={q}"
        ),
        AiService(
            id = "pinterest",
            name = "Pinterest",
            url = "https://www.pinterest.com/search/pins/?q={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.pinterest",
            deepLinkTemplate = "pinterest://search/pins/?q={q}",
            searchUrlTemplate = "https://www.pinterest.com/search/pins/?q={q}"
        ),
        AiService(
            id = "spotify",
            name = "Spotify",
            url = "https://open.spotify.com/search/{q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.spotify.music",
            deepLinkTemplate = "spotify:search:{q}",
            searchUrlTemplate = "https://open.spotify.com/search/{q}"
        ),
        AiService(
            id = "twitch",
            name = "Twitch",
            url = "https://www.twitch.tv/search?term={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "tv.twitch.android.app",
            searchUrlTemplate = "https://www.twitch.tv/search?term={q}"
        ),
        AiService(
            id = "linkedin",
            name = "LinkedIn",
            url = "https://www.linkedin.com/search/results/all/?keywords={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.linkedin.android",
            searchUrlTemplate = "https://www.linkedin.com/search/results/all/?keywords={q}"
        ),
        AiService(
            id = "threads",
            name = "Threads",
            url = "https://www.threads.net/search?q={q}",
            category = ServiceCategory.SOCIAL,
            packageName = "com.instagram.barcelona",
            searchUrlTemplate = "https://www.threads.net/search?q={q}"
        ),

        // 4. Productivity & Shopping
        AiService(
            id = "amazon",
            name = "Amazon",
            url = "https://www.amazon.com/s?k={q}",
            category = ServiceCategory.PRODUCTIVITY,
            packageName = "com.amazon.mShop.android.shopping",
            searchUrlTemplate = "https://www.amazon.com/s?k={q}"
        ),
        AiService(
            id = "googlemaps",
            name = "Google Maps",
            url = "https://www.google.com/maps/search/{q}",
            category = ServiceCategory.PRODUCTIVITY,
            packageName = "com.google.android.apps.maps",
            deepLinkTemplate = "geo:0,0?q={q}",
            searchUrlTemplate = "https://www.google.com/maps/search/{q}"
        ),
        AiService(
            id = "ebay",
            name = "eBay",
            url = "https://www.ebay.com/sch/i.html?_nkw={q}",
            category = ServiceCategory.PRODUCTIVITY,
            packageName = "com.ebay.mobile",
            searchUrlTemplate = "https://www.ebay.com/sch/i.html?_nkw={q}"
        ),
        AiService(
            id = "wolframalpha",
            name = "WolframAlpha",
            url = "https://www.wolframalpha.com/input?i={q}",
            category = ServiceCategory.PRODUCTIVITY,
            searchUrlTemplate = "https://www.wolframalpha.com/input?i={q}"
        ),
        AiService(
            id = "translate",
            name = "Google Translate",
            url = "https://translate.google.com/?sl=auto&tl=en&text={q}&op=translate",
            category = ServiceCategory.PRODUCTIVITY,
            packageName = "com.google.android.apps.translate",
            searchUrlTemplate = "https://translate.google.com/?sl=auto&tl=en&text={q}&op=translate"
        )
    )

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("https://") || t.startsWith("http://") -> t
            else -> "https://$t"
        }
    }

    fun isAppInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns all services configured in user preference order (both enabled and disabled).
     */
    fun allConfigured(prefs: Prefs): List<AiService> {
        val customList = prefs.getCustomServices()
        val legacy = prefs.customUrl.takeIf { it.isNotBlank() && customList.none { c -> c.id == CUSTOM_ID } }?.let {
            AiService(CUSTOM_ID, "Custom AI", normalizeUrl(it), isCustom = true, category = ServiceCategory.CUSTOM)
        }
        val pool = (builtIn + customList + listOfNotNull(legacy)).associateBy { it.id }.toMutableMap()

        // Apply overrides (e.g. enabled status, openInApp)
        val overrides = prefs.getServiceOverrides()
        for ((id, over) in overrides) {
            pool[id]?.let { s ->
                pool[id] = s.copy(
                    isEnabled = over.optBoolean("isEnabled", s.isEnabled),
                    openInApp = over.optBoolean("openInApp", s.openInApp)
                )
            }
        }

        // Apply user ordering
        val order = prefs.getServiceOrder()
        val ordered = mutableListOf<AiService>()
        for (id in order) {
            pool.remove(id)?.let { ordered.add(it) }
        }
        // Append remaining items not explicitly ordered yet
        ordered.addAll(pool.values)
        return ordered
    }

    /**
     * Alias for allConfigured(prefs) ensuring backward and cross-component compatibility.
     */
    fun getAll(prefs: Prefs): List<AiService> = allConfigured(prefs)

    /**
     * Returns only the enabled services in user preference order.
     */
    fun all(prefs: Prefs): List<AiService> {
        val configured = allConfigured(prefs)
        val enabled = configured.filter { it.isEnabled }
        return if (enabled.isNotEmpty()) enabled else listOf(builtIn.first())
    }

    fun current(prefs: Prefs): AiService {
        val active = all(prefs).firstOrNull { it.id == prefs.serviceId }
        return active ?: all(prefs).firstOrNull() ?: builtIn.first()
    }
}
