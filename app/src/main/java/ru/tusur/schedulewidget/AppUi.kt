package ru.tusur.schedulewidget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

object AppUi {
    val background = Color.rgb(244, 241, 234)
    val surface = Color.rgb(255, 252, 246)
    val surfaceVariant = Color.rgb(234, 229, 219)
    val ink = Color.rgb(36, 37, 33)
    val muted = Color.rgb(103, 103, 95)
    val accent = Color.rgb(138, 78, 56)
    val outline = Color.rgb(220, 214, 203)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun text(context: Context, value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    fun sectionLabel(context: Context, value: String): TextView = text(
        context,
        value.uppercase(),
        11f,
        muted,
        true
    ).apply { letterSpacing = 0.09f }

    fun card(context: Context, content: View, radius: Int = 22): MaterialCardView = MaterialCardView(context).apply {
        this.radius = dp(context, radius).toFloat()
        cardElevation = 0f
        strokeWidth = dp(context, 1)
        strokeColor = outline
        setCardBackgroundColor(surface)
        addView(content)
    }

    fun column(context: Context, padding: Int = 16): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val inset = dp(context, padding)
        setPadding(inset, inset, inset, inset)
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun spacer(context: Context, height: Int = 12): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, height))
    }

    fun applyCardTextColor(view: TextView, background: Int) {
        view.setTextColor(WidgetAppearance.contrastColor(background))
    }
}
