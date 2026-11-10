package ru.tusur.schedulewidget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val url = URL("https://timetable.tusur.ru/faculties/fvs/groups/576-1.ics")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TUSUR-Schedule-Widget/1.0")
                setRequestProperty("Accept", "text/calendar,*/*")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()
            val lessons = IcalParser.parse(text)
            if (lessons.isEmpty()) throw IllegalStateException("В календаре не найдены занятия")
            ScheduleStore.save(applicationContext, lessons)
            ScheduleStore.setError(applicationContext, "")
            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(ComponentName(applicationContext, ScheduleWidgetProvider::class.java))
            ids.forEach { ScheduleWidgetProvider.update(applicationContext, manager, it) }
            Result.success()
        } catch (e: Exception) {
            ScheduleStore.setError(applicationContext, e.message ?: "Ошибка загрузки")
            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(ComponentName(applicationContext, ScheduleWidgetProvider::class.java))
            ids.forEach { ScheduleWidgetProvider.update(applicationContext, manager, it) }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
