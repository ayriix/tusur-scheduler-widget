package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetTickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(applicationContext, ScheduleWidgetProvider::class.java)
        )
        ids.forEach { ScheduleWidgetProvider.update(applicationContext, manager, it) }
        return Result.success()
    }
}
