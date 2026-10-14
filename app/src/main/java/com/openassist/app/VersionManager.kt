package com.openassist.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class VersionInfo(
    val version: String = "1.64.0",
    val versionCode: Int = 64,
    val versionTag: String = "v.1.64.0",
    val displayName: String = "v1.64.0",
    val channel: String = "stable",
    val releaseDate: String = "2026-10-14",
    val githubRepo: String = "Cellate/OpenAssist",
    val buildPackage: String = "OpenAssist_v.1.64.0.zip",
    val apkName: String = "OpenAssist-v1.64.0.apk"
)

object VersionManager {
    @Volatile
    private var cachedInfo: VersionInfo? = null

    fun getVersionInfo(context: Context): VersionInfo {
        cachedInfo?.let { return it }

        val info = try {
            val inputStream = context.assets.open("version.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.readText()
            reader.close()

            val json = JSONObject(jsonStr)
            VersionInfo(
                version = json.optString("version", "1.64.0"),
                versionCode = json.optInt("versionCode", 64),
                versionTag = json.optString("versionTag", "v.1.64.0"),
                displayName = json.optString("displayName", "v1.64.0"),
                channel = json.optString("channel", "stable"),
                releaseDate = json.optString("releaseDate", "2026-10-14"),
                githubRepo = json.optString("githubRepo", "Cellate/OpenAssist"),
                buildPackage = json.optString("buildPackage", "OpenAssist_v.1.64.0.zip"),
                apkName = json.optString("apkName", "OpenAssist-v1.64.0.apk")
            )
        } catch (_: Exception) {
            VersionInfo()
        }

        cachedInfo = info
        return info
    }

    /**
     * Cleans prefixes like "v.", "v", "V.", "V" and extracts integer components.
     * Examples:
     *   "v.1.63.0" -> [1, 63, 0]
     *   "v1.63.0"  -> [1, 63, 0]
     *   "1.63.0"   -> [1, 63, 0]
     *   "v.1.64"   -> [1, 64, 0]
     */
    fun cleanVersionParts(tagOrVersion: String): List<Int> {
        if (tagOrVersion.isBlank()) return listOf(0, 0, 0)

        // Remove prefix v. or v (case insensitive) and whitespace
        var cleaned = tagOrVersion.trim()
        if (cleaned.startsWith("v.", ignoreCase = true)) {
            cleaned = cleaned.substring(2).trim()
        } else if (cleaned.startsWith("v", ignoreCase = true)) {
            cleaned = cleaned.substring(1).trim()
        }

        val rawParts = cleaned.split(".")
        val result = mutableListOf<Int>()
        for (part in rawParts) {
            // Keep leading numeric digits
            val digits = part.takeWhile { it.isDigit() }
            if (digits.isNotEmpty()) {
                result.add(digits.toIntOrNull() ?: 0)
            } else if (part.isNotBlank()) {
                result.add(0)
            }
        }

        while (result.size < 3) {
            result.add(0)
        }
        return result
    }

    /**
     * Compares two version strings semantically.
     * Returns:
     *   > 0 if v1 is newer than v2
     *   < 0 if v1 is older than v2
     *   = 0 if identical
     */
    fun compareVersions(v1: String, v2: String): Int {
        val p1 = cleanVersionParts(v1)
        val p2 = cleanVersionParts(v2)
        val maxLen = maxOf(p1.size, p2.size)

        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 > num2) return 1
            if (num1 < num2) return -1
        }
        return 0
    }

    /**
     * Returns true ONLY if remoteTag represents a strictly higher version than localVersion.
     */
    fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        return compareVersions(remoteTag, localVersion) > 0
    }
}
