package com.openassist.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherAliasHelper {
    private val ALIAS_MAP = mapOf(
        // Owl variants
        "owl-orbital" to "LauncherOwl",
        "owl" to "LauncherOwl",
        "owl-cosmic" to "LauncherOwl",
        "owl-talons" to "LauncherOwl",
        "owl-radar" to "LauncherOwl",
        "owl-wings" to "LauncherOwl",
        "owl-monocle" to "LauncherOwl",
        "owl-minimal" to "LauncherOwl",
        "ozzy" to "LauncherOwl",

        // Fox variants
        "fox" to "LauncherFox",
        "fox-orbital" to "LauncherFox",
        "fox-cyber" to "LauncherFox",
        "fox-paws" to "LauncherFox",
        "fox-firefly" to "LauncherFox",
        "fox-halo" to "LauncherFox",
        "fox-headset" to "LauncherFox",
        "fox-monocle" to "LauncherFox",
        "fox-minimal" to "LauncherFox",
        "pip" to "LauncherFox",
        "fennec" to "LauncherFox",

        // Other mascots
        "bat" to "LauncherBat",
        "echo" to "LauncherBat",
        "otter" to "LauncherOtter",
        "nova" to "LauncherOtter",
        "chameleon" to "LauncherChameleon",
        "pico" to "LauncherChameleon",
        "tanuki" to "LauncherTanuki",
        "taro" to "LauncherTanuki",
        "momo" to "LauncherTanuki",
        "cat" to "LauncherKitten",
        "kitten" to "LauncherKitten",
        "byte" to "LauncherKitten",
        "luna" to "LauncherKitten",
        "sprite" to "LauncherSprite",
        "ollie" to "LauncherSprite",
        "bolt" to "LauncherSprite",
        "axolotl" to "LauncherAxolotl",
        "kiko" to "LauncherAxolotl",
        "firefly" to "LauncherFirefly",
        "lumi" to "LauncherFirefly",
        "whale" to "LauncherWhale",
        "moby" to "LauncherWhale",
        "orbital" to "LauncherOrbital",
        "iris" to "LauncherOrbital"
    )

    fun getTargetAlias(mascotStyle: String): String {
        val s = mascotStyle.lowercase().trim()
        return ALIAS_MAP[s] ?: when {
            s.startsWith("owl") -> "LauncherOwl"
            s.startsWith("fox") -> "LauncherFox"
            else -> "LauncherOwl"
        }
    }

    fun updateLauncherIcon(context: Context, mascotStyle: String) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            val targetAlias = getTargetAlias(mascotStyle)
            val allAliases = setOf(
                "LauncherOwl",
                "LauncherFox",
                "LauncherBat",
                "LauncherOtter",
                "LauncherChameleon",
                "LauncherTanuki",
                "LauncherKitten",
                "LauncherSprite",
                "LauncherAxolotl",
                "LauncherFirefly",
                "LauncherWhale",
                "LauncherOrbital"
            )

            // Enable target first
            val targetComp = ComponentName(pkg, "$pkg.$targetAlias")
            if (pm.getComponentEnabledSetting(targetComp) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    targetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Disable others
            for (alias in allAliases) {
                if (alias == targetAlias) continue
                val comp = ComponentName(pkg, "$pkg.$alias")
                if (pm.getComponentEnabledSetting(comp) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("LauncherAliasHelper", "Could not switch launcher alias", t)
        }
    }

    fun restartApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.finishAffinity()
            }
            Runtime.getRuntime().exit(0)
        } catch (e: Exception) {
            android.util.Log.e("LauncherAliasHelper", "Could not restart app", e)
        }
    }
}
