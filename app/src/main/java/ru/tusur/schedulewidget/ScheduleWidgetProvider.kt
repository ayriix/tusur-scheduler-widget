package ru.tusur.schedulewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        update(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_REFRESH -> enqueueRefresh(context, true)
            ACTION_DAY_PREVIOUS, ACTION_DAY_NEXT -> if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ScheduleStore.moveSelectedDate(context, widgetId, if (intent.action == ACTION_DAY_NEXT) 1 else -1)
                update(context, manager, widgetId)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "ru.tusur.schedulewidget.REFRESH"
        const val ACTION_DAY_PREVIOUS = "ru.tusur.schedulewidget.DAY_PREVIOUS"
        const val ACTION_DAY_NEXT = "ru.tusur.schedulewidget.DAY_NEXT"
        private val zone = ZoneId.of("Asia/Tomsk")
        private val dayFormatter = DateTimeFormatter.ofPattern("EEE · d MMMM", Locale.forLanguageTag("ru"))

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

            val background = WidgetAppearance.background(context)
            val foreground = WidgetAppearance.contrastColor(background)
            val muted = WidgetAppearance.mutedTextColor(background)
            val views = RemoteViews(context.packageName, R.layout.widget_schedule).apply {
                // Drawable resources are transferred by resource ID rather than serializing
                // large bitmaps through Binder. This is important for reliable widget updates.
                setImageViewResource(R.id.widget_background, WidgetAppearance.outerBackgroundDrawable(context))
                setInt(R.id.widget_background, "setColorFilter", background)
                setTextViewText(R.id.widget_group, group.name)
                setTextViewText(R.id.widget_day, dayFormatter.format(day).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) })
                setTextViewText(R.id.widget_updated, updatedLabel(context))
                setContentDescription(R.id.widget_updated, "Последнее обновление расписания: ${updatedAccessibleLabel(context)}")
                setTextColor(R.id.widget_group, foreground)
                setTextColor(R.id.widget_day, muted)
                setTextColor(R.id.widget_updated, muted)
                setTextColor(R.id.widget_previous, foreground)
                setTextColor(R.id.widget_next, foreground)
                setTextColor(R.id.widget_empty, muted)
                setTextViewText(
                    R.id.widget_empty,
                    if (allLessons.isEmpty()) ScheduleStore.error(context) ?: "Нажми ↻ в приложении, чтобы загрузить расписание"
                    else "На этот день занятий нет"
                )
                setEmptyView(R.id.widget_lessons, R.id.widget_empty)
                setOnClickPendingIntent(R.id.widget_root, activityIntent(context, widgetId))
                setOnClickPendingIntent(R.id.widget_previous, broadcastIntent(context, widgetId, ACTION_DAY_PREVIOUS, 1))
                setOnClickPendingIntent(R.id.widget_next, broadcastIntent(context, widgetId, ACTION_DAY_NEXT, 2))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+: RemoteCollectionItems is preferred for small collections.
                    // Each row now uses drawable resource IDs and tint actions, not Bitmaps,
                    // keeping the RemoteViews payload compact for launcher Binder updates.
                    val items = RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(true)
                        .setViewTypeCount(1)
                    dayLessons.forEach { lesson ->
                        items.addItem(lesson.start, ScheduleWidgetService.lessonView(context, lesson))
                    }
                    setRemoteAdapter(R.id.widget_lessons, items.build())
                } else {
                    val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        data = Uri.parse("tusur://widget-collection/$widgetId")
                    }
                    setRemoteAdapter(R.id.widget_lessons, serviceIntent)
                }
            }

            manager.updateAppWidget(widgetId, views)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_lessons)
            }
        }

        private fun activityIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("tusur://widget/$widgetId/open")
            }
            return PendingIntent.getActivity(context, widgetId * 10 + 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun broadcastIntent(context: Context, widgetId: Int, action: String, suffix: Int): PendingIntent {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("tusur://widget/$widgetId/$suffix")
            }
            return PendingIntent.getBroadcast(context, widgetId * 10 + suffix, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun updatedLabel(context: Context): String {
            val updated = ScheduleStore.updatedAt(context)
            if (updated <= 0L) return "Не обновлено"
            return DateTimeFormatter.ofPattern("dd.MM · HH:mm", Locale.ROOT)
                .format(Instant.ofEpochMilli(updated).atZone(zone))
        }

        private fun updatedAccessibleLabel(context: Context): String {
            val updated = ScheduleStore.updatedAt(context)
            if (updated <= 0L) return "не выполнялось"
            return DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
                .format(Instant.ofEpochMilli(updated).atZone(zone))
        }
    }
}
