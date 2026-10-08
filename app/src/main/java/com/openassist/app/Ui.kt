package com.openassist.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    // Material You / Gemini Material 3 Dark Palette
    val PANEL: Int = Color.parseColor("#F01F2023")       // Gemini M3 surface container with gentle opacity
    val SURFACE: Int = Color.parseColor("#FF282A2C")     // Gemini pill / container background
    val SURFACE_HI: Int = Color.parseColor("#FF37393E")  // Elevated container / button surface
    val ACCENT: Int = Color.parseColor("#8AB4F8")        // Gemini light blue accent
    val ON_ACCENT: Int = Color.parseColor("#041E49")     // High-contrast deep navy on accent
    val TEXT: Int = Color.parseColor("#FFE3E3E3")        // Material You primary light text
    val MUTED: Int = Color.parseColor("#FF9AA0A6")       // Material You secondary muted text
    val BG: Int = Color.parseColor("#FF131314")          // Gemini dark surface container
    val BAR: Int = Color.parseColor("#FF1E1F20")         // Tonal navigation bar
    val DRAG_HANDLE: Int = Color.parseColor("#559AA0A6") // Pill drag handle indicator

    // Gemini signature 4-color wave visualizer palette
    val GEMINI_BLUE: Int = Color.parseColor("#4285F4")
    val GEMINI_RED: Int = Color.parseColor("#EA4335")
    val GEMINI_YELLOW: Int = Color.parseColor("#FBBC05")
    val GEMINI_GREEN: Int = Color.parseColor("#34A853")

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())

    fun rounded(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
        }

    fun pill(ctx: Context, color: Int): GradientDrawable = rounded(color, dp(ctx, 100).toFloat())

    fun chip(ctx: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setTextColor(if (selected) ON_ACCENT else TEXT)
            background = pill(ctx, if (selected) ACCENT else SURFACE_HI)
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
            setOnClickListener { onClick() }
        }

    fun iconButton(
        ctx: Context,
        iconRes: Int,
        description: String,
        tint: Int = TEXT,
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
}
