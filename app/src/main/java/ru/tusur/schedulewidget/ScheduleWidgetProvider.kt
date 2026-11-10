package ru.tusur.schedulewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        enqueueRefresh(context, false)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        update(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            enqueueRefresh(context, true)
        }
    }

    companion object {
        const val ACTION_REFRESH = "ru.tusur.schedulewidget.REFRESH"
        private val zone = ZoneId.of("Asia/Tomsk")
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        fun enqueueRefresh(context: Context, now: Boolean) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork("tusur-refresh", if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
            val periodic = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork("tusur-periodic", ExistingPeriodicWorkPolicy.KEEP, periodic)

            // Recalculate "now / next" from the local cache every 15 minutes without using the network.
            val tick = PeriodicWorkRequestBuilder<WidgetTickWorker>(15, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork("tusur-widget-tick", ExistingPeriodicWorkPolicy.KEEP, tick)
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            val now = Instant.now()
            val today = now.atZone(zone).toLocalDate()
            val lessons = ScheduleStore.load(context).filter {
                Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == today
            }.sortedBy { it.start }
            val current = lessons.firstOrNull { now.toEpochMilli() in it.start until it.end }
            val next = lessons.firstOrNull { it.start > now.toEpochMilli() }
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            val updated = ScheduleStore.updatedAt(context)
            val status: String
            val details: String
            when {
                current != null -> {
                    status = current.title
                    details = "${Instant.ofEpochMilli(current.start).atZone(zone).format(fmt)}–${Instant.ofEpochMilli(current.end).atZone(zone).format(fmt)} · ${current.room.ifBlank { "аудитория не указана" }}"
                }
                next != null -> {
                    status = "Следующая: ${next.title}"
                    details = "Начало ${Instant.ofEpochMilli(next.start).atZone(zone).format(fmt)} · ${next.room.ifBlank { "аудитория не указана" }}"
                }
                lessons.isNotEmpty() -> {
                    status = "На сегодня пары закончились"
                    details = "Расписание группы 576-1"
                }
                else -> {
                    status = "Нет сохранённого расписания"
                    details = ScheduleStore.error(context) ?: "Нажмите для загрузки"
                }
            }
            val options = manager.getAppWidgetOptions(id)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            // A taller widget reveals more schedule rows; a compact widget keeps only the current/next lesson.
            val visibleRows = (((minHeightDp - 105) / 23).coerceIn(0, 6))
            val list = lessons.take(visibleRows).joinToString("\\n") {
                val s = Instant.ofEpochMilli(it.start).atZone(zone).format(fmt)
                val e = Instant.ofEpochMilli(it.end).atZone(zone).format(fmt)
                "$s–$e  ${it.title}  ${it.room}".trim()
            }
            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_details, details)
            views.setTextViewText(R.id.widget_list, list)
            views.setInt(R.id.widget_list, "setMaxLines", visibleRows.coerceAtLeast(1))
            views.setViewVisibility(R.id.widget_list, if (visibleRows > 0 && list.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(R.id.widget_footer,
                if (updated > 0) "Обновлено: ${DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(zone).format(Instant.ofEpochMilli(updated))} · нажмите для обновления"
                else "Нажмите для обновления")
            val refresh = Intent(context, ScheduleWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val pi = PendingIntent.getBroadcast(context, id, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, views)
        }
    }
}
