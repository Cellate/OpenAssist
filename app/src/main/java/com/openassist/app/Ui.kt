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

enum class ToastType {
    GENERAL,
    SPEECH,
    CIRCLE,
    ASSISTANT
}

object Ui {
    data class Theme(val isDark: Boolean) {
        val PANEL: Int = if (isDark) Color.parseColor("#F01E293B") else Color.parseColor("#FFF8FAFC")
        val SURFACE: Int = if (isDark) Color.parseColor("#FF1E293B") else Color.parseColor("#FFFFFFFF")
        val SURFACE_HI: Int = if (isDark) Color.parseColor("#FF334155") else Color.parseColor("#FFF1F5F9")
        val ACCENT: Int = if (isDark) Color.parseColor("#60A5FA") else Color.parseColor("#2563EB")
        val ON_ACCENT: Int = if (isDark) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFFFF")
        val TEXT: Int = if (isDark) Color.parseColor("#FFF8FAFC") else Color.parseColor("#FF0F172A")
        val MUTED: Int = if (isDark) Color.parseColor("#FF94A3B8") else Color.parseColor("#FF475569")
        val BG: Int = if (isDark) Color.parseColor("#FF0F172A") else Color.parseColor("#FFF8FAFC")
        val BAR: Int = if (isDark) Color.parseColor("#FF1E293B") else Color.parseColor("#FFFFFFFF")
        val DRAG_HANDLE: Int = if (isDark) Color.parseColor("#559AA0A6") else Color.parseColor("#FF64748B")
        val CARD: Int = if (isDark) Color.parseColor("#FF1E293B") else Color.parseColor("#FFFFFFFF")
        val CARD_BORDER: Int = if (isDark) Color.parseColor("#FF334155") else Color.parseColor("#FFE2E8F0")
        val INPUT_BG: Int = if (isDark) Color.parseColor("#FF0F172A") else Color.parseColor("#FFF1F5F9")
    }

    fun theme(ctx: Context): Theme = Theme(Prefs(ctx).isDarkMode)

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
            setColor(if (theme.isDark) Color.parseColor("#FF1E293B") else Color.parseColor("#FFFFFFFF"))
            cornerRadius = dp(ctx, 16).toFloat()
            setStroke(dp(ctx, 1), if (theme.isDark) Color.parseColor("#FF334155") else Color.parseColor("#FFE2E8F0"))
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

    @Suppress("DEPRECATION")
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
                        var flags = window.decorView.systemUiVisibility
                        flags = if (!theme.isDark) {
                            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        } else {
                            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                        }
                        window.decorView.systemUiVisibility = flags
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        var flags = window.decorView.systemUiVisibility
                        flags = if (!theme.isDark) {
                            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        } else {
                            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                        }
                        window.decorView.systemUiVisibility = flags
                    }
                } catch (t: Throwable) {}
            }
            try {
                window.decorView.post { applyBars() }
            } catch (t: Throwable) {
                applyBars()
            }
        } catch (t: Throwable) {}
    }

    fun themedDialogBuilder(activity: Activity): android.app.AlertDialog.Builder {
        val isDark = Prefs(activity).isDarkMode
        val themeRes = if (isDark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
        return android.app.AlertDialog.Builder(activity, themeRes)
    }

    fun styleDialog(dialog: android.app.AlertDialog, theme: Theme) {
        dialog.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(theme.ACCENT)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(theme.MUTED)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(theme.MUTED)
        }
    }

    fun showMascotToast(
        context: Context,
        message: String,
        mascotStyle: String? = null,
        duration: Int = android.widget.Toast.LENGTH_SHORT,
        type: ToastType = ToastType.GENERAL
    ) {
        val prefs = Prefs(context)
        if (!prefs.toastMascotPillEnabled) return
        when (type) {
            ToastType.SPEECH -> if (!prefs.toastSpeechEnabled) return
            ToastType.CIRCLE -> if (!prefs.toastCircleEnabled) return
            ToastType.ASSISTANT -> if (!prefs.toastAssistantEnabled) return
            ToastType.GENERAL -> if (!prefs.toastGeneralEnabled) return
        }
        val style = mascotStyle ?: prefs.mascotStyle
        val mascot = MascotHelper.getMascot(style)
        val theme = theme(context)

        // In-App Floating Mascot Pill (Guaranteed to show mascot icon on all Android 11+ OEM skins)
        if (context is Activity) {
            try {
                val activity = context
                val decor = activity.window?.decorView as? android.view.ViewGroup
                if (decor != null) {
                    val toastCard = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF"), dp(activity, 22).toFloat()).apply {
                            setStroke(dp(activity, 1), if (theme.isDark) Color.parseColor("#4F46E5") else Color.parseColor("#818CF8"))
                        }
                        elevation = dp(activity, 12).toFloat()
                        val padH = dp(activity, 14)
                        val padV = dp(activity, 10)
                        setPadding(padH, padV, padH, padV)
                        val lp = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            bottomMargin = dp(activity, 72)
                            leftMargin = dp(activity, 20)
                            rightMargin = dp(activity, 20)
                        }
                        layoutParams = lp
                    }

                    val img = ImageView(activity).apply {
                        setImageResource(mascot.drawableRes)
                        val s = dp(activity, 28)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply {
                            rightMargin = dp(activity, 10)
                        }
                    }
                    toastCard.addView(img)

                    val textCol = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    textCol.addView(TextView(activity).apply {
                        text = mascot.name
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(theme.ACCENT)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    })
                    textCol.addView(TextView(activity).apply {
                        text = message
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(theme.TEXT)
                    })
                    toastCard.addView(textCol)

                    toastCard.alpha = 0f
                    toastCard.translationY = dp(activity, 20).toFloat()
                    decor.addView(toastCard)

                    toastCard.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .withEndAction {
                            val stayTime = if (duration == android.widget.Toast.LENGTH_LONG) 3600L else 2400L
                            toastCard.postDelayed({
                                toastCard.animate()
                                    .alpha(0f)
                                    .translationY(dp(activity, 20).toFloat())
                                    .setDuration(220)
                                    .withEndAction {
                                        try { decor.removeView(toastCard) } catch (e: Exception) {}
                                    }
                                    .start()
                            }, stayTime)
                        }
                        .start()
                    return
                }
            } catch (t: Throwable) {
                // fallback to system toast
            }
        }

        try {
            android.widget.Toast.makeText(context, "${mascot.name}: $message", duration).show()
        } catch (t: Throwable) {}
    }
}

