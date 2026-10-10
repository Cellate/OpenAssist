package com.openassist.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    // Cohesive Theme definition supporting both Dark (Gemini Dark) and Light (Material 3 Light)
    data class Theme(val isDark: Boolean) {
        val PANEL: Int = if (isDark) Color.parseColor("#F01F2023") else Color.parseColor("#FFF8FAFC")
        val SURFACE: Int = if (isDark) Color.parseColor("#FF282A2C") else Color.parseColor("#FFFFFFFF")
        val SURFACE_HI: Int = if (isDark) Color.parseColor("#FF37393E") else Color.parseColor("#FFF1F5F9")
        val ACCENT: Int = if (isDark) Color.parseColor("#8AB4F8") else Color.parseColor("#1D4ED8") // Strong readable blue in light mode
        val ON_ACCENT: Int = if (isDark) Color.parseColor("#041E49") else Color.parseColor("#FFFFFFFF")
        val TEXT: Int = if (isDark) Color.parseColor("#FFF1F5F9") else Color.parseColor("#FF0F172A") // Deep Slate-900 black (Max Contrast)
        val MUTED: Int = if (isDark) Color.parseColor("#FF94A3B8") else Color.parseColor("#FF334155") // Deep Slate-700 (High Contrast on White)
        val BG: Int = if (isDark) Color.parseColor("#FF131314") else Color.parseColor("#FFF8FAFC") // Slate-50 background for clean separation
        val BAR: Int = if (isDark) Color.parseColor("#FF1E1F20") else Color.parseColor("#FFFFFFFF")
        val DRAG_HANDLE: Int = if (isDark) Color.parseColor("#559AA0A6") else Color.parseColor("#FF64748B")
        val CARD: Int = if (isDark) Color.parseColor("#FF282A2C") else Color.parseColor("#FFFFFFFF")
        val CARD_BORDER: Int = if (isDark) Color.parseColor("#26FFFFFF") else Color.parseColor("#FFE2E8F0")
        val INPUT_BG: Int = if (isDark) Color.parseColor("#FF121418") else Color.parseColor("#FFF8FAFC")
    }

    fun theme(ctx: Context): Theme = Theme(Prefs(ctx).isDarkMode)

    // Legacy Dark Palette constants for backward compatibility
    val PANEL: Int = Color.parseColor("#F01F2023")
    val SURFACE: Int = Color.parseColor("#FF282A2C")
    val SURFACE_HI: Int = Color.parseColor("#FF37393E")
    val ACCENT: Int = Color.parseColor("#8AB4F8")
    val ON_ACCENT: Int = Color.parseColor("#041E49")
    val TEXT: Int = Color.parseColor("#FFE3E3E3")
    val MUTED: Int = Color.parseColor("#FF9AA0A6")
    val BG: Int = Color.parseColor("#FF131314")
    val BAR: Int = Color.parseColor("#FF1E1F20")
    val DRAG_HANDLE: Int = Color.parseColor("#559AA0A6")

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())

    fun rounded(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
        }

    fun card(ctx: Context, theme: Theme): GradientDrawable =
        GradientDrawable().apply {
            setColor(theme.CARD)
            cornerRadius = dp(ctx, 14).toFloat()
            setStroke(dp(ctx, 1), theme.CARD_BORDER)
        }

    fun pill(ctx: Context, color: Int): GradientDrawable = rounded(color, dp(ctx, 100).toFloat())

    fun chip(
        ctx: Context,
        label: String,
        selected: Boolean,
        theme: Theme = theme(ctx),
        onClick: () -> Unit
    ): TextView =
        TextView(ctx).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setTextColor(if (selected) theme.ON_ACCENT else theme.TEXT)
            background = pill(ctx, if (selected) theme.ACCENT else theme.SURFACE_HI)
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
            setOnClickListener { onClick() }
        }

    fun iconButton(
        ctx: Context,
        iconRes: Int,
        description: String,
        theme: Theme = theme(ctx),
        tint: Int = theme.TEXT,
        bg: Int? = null,
        sizeDp: Int = 44,
        onClick: () -> Unit
    ): ImageButton =
        ImageButton(ctx).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tint)
            contentDescription = description
            background = if (bg != null) pill(ctx, bg) else null
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
            setOnClickListener { onClick() }
        }

    @android.annotation.TargetApi(30)
    private object Api30Helper {
        fun setAppearance(activity: Activity, isDark: Boolean) {
            try {
                activity.window?.insetsController?.let { controller ->
                    val lightStatus = !isDark
                    val appearance = if (lightStatus) {
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    } else 0
                    controller.setSystemBarsAppearance(
                        appearance,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } catch (t: Throwable) {}
        }
    }

    fun applySystemBars(activity: Activity, theme: Theme) {
        try {
            val window = activity.window ?: return
            try {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = theme.BAR
                window.navigationBarColor = theme.BAR
            } catch (t: Throwable) {}

            val applyBars = {
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Api30Helper.setAppearance(activity, theme.isDark)
                    } else if (Build.VERSION.SDK_INT >= 26) {
                        @Suppress("DEPRECATION")
                        var flags = window.decorView.systemUiVisibility
                        flags = if (!theme.isDark) {
                            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        } else {
                            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                        }
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = flags
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        @Suppress("DEPRECATION")
                        var flags = window.decorView.systemUiVisibility
                        flags = if (!theme.isDark) {
                            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        } else {
                            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                        }
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = flags
                    }
                } catch (t: Throwable) {}
            }
            try {
                window.decorView.post { applyBars() }
            } catch (t: Throwable) {
                applyBars()
            }
        } catch (t: Throwable) {
            // Never crash activity
        }
    }
}
