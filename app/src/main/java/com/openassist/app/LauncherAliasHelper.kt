package com.openassist.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherAliasHelper {
    private val ALIAS_MAP = mapOf(
        "owl-orbital" to "LauncherOwl",
        "owl" to "LauncherOwl",
        "fox-orbital" to "LauncherFox",
        "fox" to "LauncherFox",
        "bat" to "LauncherBat",
        "otter" to "LauncherOtter",
        "chameleon" to "LauncherChameleon",
        "tanuki" to "LauncherTanuki",
        "cat" to "LauncherKitten",
        "kitten" to "LauncherKitten",
        "sprite" to "LauncherSprite",
        "axolotl" to "LauncherAxolotl",
        "firefly" to "LauncherFirefly",
        "whale" to "LauncherWhale"
    )

    fun updateLauncherIcon(context: Context, mascotStyle: String) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            val targetAlias = ALIAS_MAP[mascotStyle.lowercase()] ?: "LauncherOwl"
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
                "LauncherWhale"
            )

            for (alias in allAliases) {
                val comp = ComponentName(pkg, "$pkg.$alias")
                val isTarget = (alias == targetAlias)
                val newState = if (isTarget) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                val curState = pm.getComponentEnabledSetting(comp)
                if (curState != newState) {
                    pm.setComponentEnabledSetting(comp, newState, PackageManager.DONT_KILL_APP)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("LauncherAliasHelper", "Could not switch launcher alias", t)
        }
    }
}
