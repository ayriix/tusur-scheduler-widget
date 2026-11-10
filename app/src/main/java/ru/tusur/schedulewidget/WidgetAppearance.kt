package ru.tusur.schedulewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import kotlin.math.max

object WidgetAppearance {
    private const val PREFS = "widget_appearance"
    private const val KEY_RADIUS = "radius"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_LESSON = "lesson"
    private const val KEY_CURRENT = "current"

    const val DEFAULT_BACKGROUND = 0xFFF0EDE5.toInt()
    const val DEFAULT_LESSON = 0xFFFAF8F2.toInt()
    const val DEFAULT_CURRENT = 0xFFE9D1C3.toInt()
    const val DEFAULT_RADIUS = 24f

    fun radius(context: Context) = prefs(context).getFloat(KEY_RADIUS, DEFAULT_RADIUS).coerceIn(0f, 32f)
    fun background(context: Context) = prefs(context).getInt(KEY_BACKGROUND, DEFAULT_BACKGROUND)
    fun lesson(context: Context) = prefs(context).getInt(KEY_LESSON, DEFAULT_LESSON)
    fun current(context: Context) = prefs(context).getInt(KEY_CURRENT, DEFAULT_CURRENT)

    fun setRadius(context: Context, value: Float) = prefs(context).edit().putFloat(KEY_RADIUS, value.coerceIn(0f, 32f)).apply()
    fun setBackground(context: Context, value: Int) = prefs(context).edit().putInt(KEY_BACKGROUND, value).apply()
    fun setLesson(context: Context, value: Int) = prefs(context).edit().putInt(KEY_LESSON, value).apply()
    fun setCurrent(context: Context, value: Int) = prefs(context).edit().putInt(KEY_CURRENT, value).apply()

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
        return if (luminance < 0.48) Color.WHITE else 0xFF252622.toInt()
    }

    fun mutedTextColor(background: Int): Int {
        val main = contrastColor(background)
        return if (main == Color.WHITE) 0xFFD1D0CB.toInt() else 0xFF696A63.toInt()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
