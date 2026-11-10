package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var connection: HttpURLConnection? = null
        return try {
            val group = ScheduleStore.selectedGroup(applicationContext)
            connection = (URL(group.calendarUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 18_000
                readTimeout = 22_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TUSUR-Schedule/2.0 (Android)")
                setRequestProperty("Accept", "text/calendar, text/plain, */*")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Сайт ТУСУРа ответил кодом $code")
            val calendar = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!calendar.contains("BEGIN:VCALENDAR")) throw IllegalStateException("Сайт вернул ответ не в формате календаря")
            val lessons = IcalParser.parse(calendar)
            if (lessons.isEmpty()) throw IllegalStateException("В календаре этой группы пока нет занятий")

            ScheduleStore.save(applicationContext, lessons)
            ScheduleStore.setError(applicationContext, "")
            updateWidgets(applicationContext)
            Result.success()
        } catch (error: Exception) {
            ScheduleStore.setError(applicationContext, error.message ?: "Не удалось загрузить расписание")
            updateWidgets(applicationContext)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            connection?.disconnect()
        }
    }

    private fun updateWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        ids.forEach { ScheduleWidgetProvider.update(context, manager, it) }
    }
}
