package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ScheduleRemoteViewsFactory(applicationContext, intent)

    companion object {
        private val zone = ZoneId.of("Asia/Tomsk")
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        fun lessonView(context: Context, lesson: Lesson): RemoteViews {
            val now = System.currentTimeMillis()
            val isCurrent = now >= lesson.start && now < lesson.end
            val cardColor = if (isCurrent) WidgetAppearance.current(context) else WidgetAppearance.lesson(context)
            val foreground = WidgetAppearance.contrastColor(cardColor)
            val muted = WidgetAppearance.mutedTextColor(cardColor)
            val cardBackground = WidgetAppearance.roundedBitmap(
                color = cardColor,
                width = 160,
                height = 56,
                radiusPx = 11f
            )
            return RemoteViews(context.packageName, R.layout.widget_lesson_item).apply {
                setImageViewBitmap(R.id.lesson_item_background, cardBackground)
                setTextViewText(
                    R.id.lesson_item_time,
                    "${timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone))}\n${timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone))}"
                )
                setTextViewText(R.id.lesson_item_title, lesson.title)
                setTextViewText(R.id.lesson_item_room, LessonText.room(lesson.room))
                setTextColor(R.id.lesson_item_time, muted)
                setTextColor(R.id.lesson_item_title, foreground)
                setTextColor(R.id.lesson_item_room, muted)
                setInt(R.id.lesson_item_accent, "setBackgroundColor", if (isCurrent) 0xFF8BA9FF.toInt() else muted)
                setViewVisibility(R.id.lesson_item_accent, if (isCurrent) android.view.View.VISIBLE else android.view.View.INVISIBLE)
            }
        }
    }
}

private class ScheduleRemoteViewsFactory(
    private val context: Context,
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

    override fun onDestroy() { lessons = emptyList() }
    override fun getCount(): Int = lessons.size
    override fun getViewAt(position: Int): RemoteViews? =
        lessons.getOrNull(position)?.let { ScheduleWidgetService.lessonView(context, it) }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = lessons.getOrNull(position)?.start ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
