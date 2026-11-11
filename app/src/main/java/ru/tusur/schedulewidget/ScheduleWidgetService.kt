package ru.tusur.schedulewidget

import android.content.Context
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScheduleWidgetService {
    private val zone = ZoneId.of("Asia/Tomsk")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun lessonView(context: Context, lesson: Lesson): RemoteViews {
        val now = System.currentTimeMillis()
        val isCurrent = now >= lesson.start && now < lesson.end
        val cardColor = if (isCurrent) WidgetAppearance.current(context) else WidgetAppearance.lesson(context)
        val typeColor = LessonTypeStyle.color(lesson.type)
        val foreground = WidgetAppearance.contrastColor(cardColor)
        val muted = WidgetAppearance.mutedTextColor(cardColor)
        return RemoteViews(context.packageName, R.layout.widget_lesson_item).apply {
            setImageViewResource(R.id.lesson_item_background, R.drawable.widget_lesson_shape)
            setInt(R.id.lesson_item_background, "setColorFilter", cardColor)
            setImageViewResource(R.id.lesson_item_border, R.drawable.widget_lesson_border)
            setInt(R.id.lesson_item_border, "setColorFilter", typeColor)
            setTextViewText(
                R.id.lesson_item_time,
                "${timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone))}\n${timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone))}"
            )
            setTextViewText(R.id.lesson_item_title, lesson.title)
            setTextViewText(R.id.lesson_item_room, LessonText.room(lesson.room))
            setTextColor(R.id.lesson_item_time, muted)
            setTextColor(R.id.lesson_item_title, foreground)
            setTextColor(R.id.lesson_item_room, muted)
            setViewVisibility(R.id.lesson_item_accent, android.view.View.GONE)
        }
    }
}

