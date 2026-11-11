package ru.tusur.schedulewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

object WidgetAppearance {
    private const val PREFS = "widget_appearance"
    private const val KEY_RADIUS = "radius"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_LESSON = "lesson"
    private const val KEY_CURRENT = "current"
    private const val KEY_V3_INITIALIZED = "dark_defaults_v3_initialized"

    const val DEFAULT_BACKGROUND = 0xFF1A2029.toInt()
    const val DEFAULT_LESSON = 0xFF282F3B.toInt()
    const val DEFAULT_CURRENT = 0xFF314B78.toInt()
    const val DEFAULT_RADIUS = 24f

    fun radius(context: Context): Float { initializeV3Defaults(context); return prefs(context).getFloat(KEY_RADIUS, DEFAULT_RADIUS).coerceIn(0f, 32f) }
    fun background(context: Context): Int { initializeV3Defaults(context); return prefs(context).getInt(KEY_BACKGROUND, DEFAULT_BACKGROUND) }
    fun lesson(context: Context): Int { initializeV3Defaults(context); return prefs(context).getInt(KEY_LESSON, DEFAULT_LESSON) }
    fun current(context: Context): Int { initializeV3Defaults(context); return prefs(context).getInt(KEY_CURRENT, DEFAULT_CURRENT) }

    fun setRadius(context: Context, value: Float) { initializeV3Defaults(context); prefs(context).edit().putFloat(KEY_RADIUS, value.coerceIn(0f, 32f)).apply() }
    fun setBackground(context: Context, value: Int) { initializeV3Defaults(context); prefs(context).edit().putInt(KEY_BACKGROUND, value).apply() }
    fun setLesson(context: Context, value: Int) { initializeV3Defaults(context); prefs(context).edit().putInt(KEY_LESSON, value).apply() }
    fun setCurrent(context: Context, value: Int) { initializeV3Defaults(context); prefs(context).edit().putInt(KEY_CURRENT, value).apply() }

    fun roundedBitmap(color: Int, width: Int, height: Int, radiusPx: Float): Bitmap {
        val safeWidth = max(1, width)
        val safeHeight = max(1, height)
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val radius = radiusPx.coerceAtMost(minOf(safeWidth, safeHeight) / 2f)
        canvas.drawRoundRect(RectF(0f, 0f, safeWidth.toFloat(), safeHeight.toFloat()), radius, radius, paint)
        return bitmap
    }

    fun contrastColor(background: Int): Int {
        val r = Color.red(background) / 255.0
        val g = Color.green(background) / 255.0
        val b = Color.blue(background) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (luminance < 0.48) Color.WHITE else 0xFF20242C.toInt()
    }

    fun mutedTextColor(background: Int): Int =
        if (contrastColor(background) == Color.WHITE) 0xFFB7C0CF.toInt() else 0xFF626A76.toInt()

    private fun initializeV3Defaults(context: Context) {
        val preferences = prefs(context)
        if (!preferences.getBoolean(KEY_V3_INITIALIZED, false)) {
            // Version 2 used a light default. Reset only once during the 2.1 upgrade;
            // subsequent user-selected colors are retained across launches and upgrades.
            preferences.edit()
                .putBoolean(KEY_V3_INITIALIZED, true)
                .putFloat(KEY_RADIUS, DEFAULT_RADIUS)
                .putInt(KEY_BACKGROUND, DEFAULT_BACKGROUND)
                .putInt(KEY_LESSON, DEFAULT_LESSON)
                .putInt(KEY_CURRENT, DEFAULT_CURRENT)
                .apply()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
