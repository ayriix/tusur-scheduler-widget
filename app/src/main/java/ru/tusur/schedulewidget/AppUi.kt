package ru.tusur.schedulewidget

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.card.MaterialCardView

object AppUi {
    // Dark, restrained palette: graphite surfaces with a cobalt accent.
    val background = Color.rgb(15, 18, 23)
    val surface = Color.rgb(25, 30, 37)
    val surfaceVariant = Color.rgb(34, 41, 51)
    val ink = Color.rgb(239, 241, 245)
    val muted = Color.rgb(164, 172, 184)
    val accent = Color.rgb(139, 169, 255)
    val outline = Color.rgb(57, 66, 80)

    fun installSystemBars(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = background
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            activity.window.setStatusBarContrastEnforced(false)
            activity.window.setNavigationBarContrastEnforced(false)
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val system = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(system.left, system.top, system.right, system.bottom)
            insets
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                ViewCompat.requestApplyInsets(view)
                view.removeOnAttachStateChangeListener(this)
            }
            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }

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
        context, value.uppercase(), 11f, muted, true
    ).apply { letterSpacing = 0.10f }

    fun card(context: Context, content: View, radius: Int = 20): MaterialCardView = MaterialCardView(context).apply {
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

    fun contrastColor(background: Int): Int = WidgetAppearance.contrastColor(background)
}
