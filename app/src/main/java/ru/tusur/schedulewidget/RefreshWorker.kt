package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "TusurRefreshWorker"
    }

    override suspend fun doWork(): Result {
        var connection: HttpURLConnection? = null
        return try {
            val group = ScheduleStore.selectedGroup(applicationContext)
            val url = URL(group.calendarUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                // Android 7+ uses the host-specific Network Security Config. Android 6
                // needs the same GlobalSign R46 compatibility via an explicit trust manager.
                if (this is HttpsURLConnection && TusurTls.isSupportedHost(url.host) &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    sslSocketFactory = TusurTls.legacySocketFactory(applicationContext, url.host)
                }
                connectTimeout = 25_000
                readTimeout = 40_000
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
            Log.e(TAG, "Не удалось обновить расписание", error)
            ScheduleStore.setError(applicationContext, TusurTls.userFacingError(error))
            updateWidgets(applicationContext)
            if (TusurTls.shouldRetry(error) && runAttemptCount < 2) Result.retry() else Result.failure()
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
