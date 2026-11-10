package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ScheduleRemoteViewsFactory(applicationContext, intent)
}

private class ScheduleRemoteViewsFactory(
    private val context: android.content.Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {
    private val widgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val zone = ZoneId.of("Asia/Tomsk")
    private var lessons: List<Lesson> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val date = ScheduleStore.selectedDate(context, widgetId)
        lessons = ScheduleStore.load(context).filter {
            Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == date
        }.sortedBy { it.start }
    }

    override fun onDestroy() {
        lessons = emptyList()
    }

    override fun getCount(): Int = lessons.size

    override fun getViewAt(position: Int): RemoteViews? {
        val lesson = lessons.getOrNull(position) ?: return null
        val now = System.currentTimeMillis()
        val isCurrent = now >= lesson.start && now < lesson.end
        val background = if (isCurrent) WidgetAppearance.current(context) else WidgetAppearance.lesson(context)
        val foreground = WidgetAppearance.contrastColor(background)
        val muted = WidgetAppearance.mutedTextColor(background)
        val backgroundBitmap = WidgetAppearance.roundedBitmap(
            color = background,
            width = 420,
            height = 88,
            radiusPx = 16f
        )

        return RemoteViews(context.packageName, R.layout.widget_lesson_item).apply {
            setImageViewBitmap(R.id.lesson_item_background, backgroundBitmap)
            setTextViewText(
                R.id.lesson_item_time,
                "${formatTime(lesson.start)}\n${formatTime(lesson.end)}"
            )
            setTextViewText(R.id.lesson_item_title, lesson.title)
            setTextViewText(R.id.lesson_item_room, lesson.room.ifBlank { "Аудитория не указана" })
            setTextColor(R.id.lesson_item_time, muted)
            setTextColor(R.id.lesson_item_title, foreground)
            setTextColor(R.id.lesson_item_room, muted)
            setInt(R.id.lesson_item_accent, "setBackgroundColor", if (isCurrent) foreground else muted)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = lessons.getOrNull(position)?.start ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun formatTime(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
