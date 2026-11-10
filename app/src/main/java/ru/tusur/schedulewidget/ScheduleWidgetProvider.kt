package ru.tusur.schedulewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        enqueueRefresh(context, false)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        update(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_REFRESH -> enqueueRefresh(context, true)
            ACTION_DAY_PREVIOUS, ACTION_DAY_NEXT -> {
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val delta = if (intent.action == ACTION_DAY_NEXT) 1 else -1
                    ScheduleStore.moveSelectedDate(context, widgetId, delta)
                    update(context, manager, widgetId)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "ru.tusur.schedulewidget.REFRESH"
        const val ACTION_DAY_PREVIOUS = "ru.tusur.schedulewidget.DAY_PREVIOUS"
        const val ACTION_DAY_NEXT = "ru.tusur.schedulewidget.DAY_NEXT"
        private val zone = ZoneId.of("Asia/Tomsk")
        private val dayFormatter = DateTimeFormatter.ofPattern("EEE · d MMMM", Locale("ru"))
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        fun enqueueRefresh(context: Context, now: Boolean) {
            val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                "tusur-refresh",
                if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )

            val periodic = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(connected)
                .build()
            workManager.enqueueUniquePeriodicWork("tusur-periodic", ExistingPeriodicWorkPolicy.UPDATE, periodic)

            val tick = PeriodicWorkRequestBuilder<WidgetTickWorker>(15, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork("tusur-widget-tick", ExistingPeriodicWorkPolicy.KEEP, tick)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val group = ScheduleStore.selectedGroup(context)
            val day = ScheduleStore.selectedDate(context, widgetId)
            val allLessons = ScheduleStore.load(context)
            val dayLessons = allLessons.filter {
                Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day
            }.sortedBy { it.start }

            val today = day == LocalDate.now(zone)
            val now = System.currentTimeMillis()
            val current = if (today) dayLessons.firstOrNull { now >= it.start && now < it.end } else null
            val next = if (today) dayLessons.firstOrNull { it.start > now } else null
            val status = when {
                allLessons.isEmpty() -> "НЕТ ДАННЫХ · ОБНОВИ РАСПИСАНИЕ"
                current != null -> "СЕЙЧАС · до ${timeFormatter.format(Instant.ofEpochMilli(current.end).atZone(zone))}"
                next != null -> "СЛЕДУЮЩАЯ · ${timeFormatter.format(Instant.ofEpochMilli(next.start).atZone(zone))}"
                today && dayLessons.isNotEmpty() -> "ЗАНЯТИЯ НА СЕГОДНЯ ЗАВЕРШЕНЫ"
                dayLessons.isEmpty() -> "РАСПИСАНИЕ НА ЭТОТ ДЕНЬ ПУСТО"
                else -> "${dayLessons.size} ${lessonCountWord(dayLessons.size)}"
            }

            val background = WidgetAppearance.background(context)
            val foreground = WidgetAppearance.contrastColor(background)
            val muted = WidgetAppearance.mutedTextColor(background)
            val density = context.resources.displayMetrics.density
            val options = manager.getAppWidgetOptions(widgetId)
            val widthDp = maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 220),
                180
            )
            val heightDp = maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140),
                120
            )
            // Keep the bitmap compact: RemoteViews are transferred across Binder and large
            // backgrounds can exceed Android's transaction limit on resizable widgets.
            val bitmapScale = minOf(1.5f, 420f / maxOf(widthDp, heightDp).toFloat())
            val bgWidth = (widthDp * bitmapScale).toInt().coerceIn(180, 420)
            val bgHeight = (heightDp * bitmapScale).toInt().coerceIn(150, 420)
            val bgBitmap = WidgetAppearance.roundedBitmap(
                background,
                bgWidth,
                bgHeight,
                WidgetAppearance.radius(context) * bitmapScale
            )

            val views = RemoteViews(context.packageName, R.layout.widget_schedule).apply {
                setImageViewBitmap(R.id.widget_background, bgBitmap)
                setTextViewText(R.id.widget_group, group.name)
                setTextViewText(R.id.widget_day, dayFormatter.format(day).uppercase(Locale("ru")))
                setTextViewText(R.id.widget_status, status)
                setTextViewText(R.id.widget_updated, updatedLabel(context))
                setContentDescription(R.id.widget_updated, "Последнее обновление расписания: ${updatedAccessibleLabel(context)}")
                setTextColor(R.id.widget_group, foreground)
                setTextColor(R.id.widget_day, muted)
                // Status sits on the widget surface, not on the current-lesson card.
                setTextColor(R.id.widget_status, foreground)
                setTextColor(R.id.widget_updated, muted)
                setTextColor(R.id.widget_previous, foreground)
                setTextColor(R.id.widget_next, foreground)

                val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                setRemoteAdapter(R.id.widget_lessons, serviceIntent)
                setEmptyView(R.id.widget_lessons, R.id.widget_empty)
                setTextViewText(
                    R.id.widget_empty,
                    if (allLessons.isEmpty()) ScheduleStore.error(context) ?: "Открой приложение и обнови расписание"
                    else "На этот день занятий нет"
                )
                setTextColor(R.id.widget_empty, muted)

                setOnClickPendingIntent(R.id.widget_root, activityIntent(context, widgetId))
                setOnClickPendingIntent(R.id.widget_previous, broadcastIntent(context, widgetId, ACTION_DAY_PREVIOUS, 1))
                setOnClickPendingIntent(R.id.widget_next, broadcastIntent(context, widgetId, ACTION_DAY_NEXT, 2))
            }

            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_lessons)
        }

        private fun activityIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("tusur://widget/$widgetId/open")
            }
            return PendingIntent.getActivity(
                context, widgetId * 10 + 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun broadcastIntent(context: Context, widgetId: Int, action: String, suffix: Int): PendingIntent {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("tusur://widget/$widgetId/$suffix")
            }
            return PendingIntent.getBroadcast(
                context, widgetId * 10 + suffix, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun updatedLabel(context: Context): String {
            val updated = ScheduleStore.updatedAt(context)
            if (updated <= 0L) return "Ещё не обновлено"
            val stamp = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.ROOT)
                .format(Instant.ofEpochMilli(updated).atZone(zone))
            return stamp
        }

        private fun updatedAccessibleLabel(context: Context): String {
            val updated = ScheduleStore.updatedAt(context)
            if (updated <= 0L) return "не выполнялось"
            return DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
                .format(Instant.ofEpochMilli(updated).atZone(zone))
        }

        private fun lessonCountWord(count: Int): String = when {
            count % 10 == 1 && count % 100 != 11 -> "пара"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "пары"
            else -> "пар"
        }
    }
}
