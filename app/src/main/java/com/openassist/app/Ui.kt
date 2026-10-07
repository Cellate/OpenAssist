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

    val PANEL: Int = Color.parseColor("#F2202124")
    val SURFACE: Int = Color.parseColor("#FF303134")
    val SURFACE_HI: Int = Color.parseColor("#FF3C4043")
    val ACCENT: Int = Color.parseColor("#FF8AB4F8")
    val ON_ACCENT: Int = Color.parseColor("#FF0B1B3D")
    val TEXT: Int = Color.parseColor("#FFE8EAED")
    val MUTED: Int = Color.parseColor("#FF9AA0A6")
    val BG: Int = Color.parseColor("#FF121212")
    val BAR: Int = Color.parseColor("#FF202124")

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
