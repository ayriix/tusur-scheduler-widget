package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Returns an individual widget to today's schedule one hour after its last day navigation. */
class WidgetDateResetWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val KEY_RESET_AT_MILLIS = "reset_at_millis"
    }

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()
        val expectedResetAtMillis = inputData.getLong(KEY_RESET_AT_MILLIS, Long.MIN_VALUE)
        if (expectedResetAtMillis == Long.MIN_VALUE) return Result.failure()

        val manager = AppWidgetManager.getInstance(applicationContext)
        val activeWidgetIds = manager.getAppWidgetIds(
            ComponentName(applicationContext, ScheduleWidgetProvider::class.java)
        )
        if (widgetId !in activeWidgetIds) return Result.success()

        // Ignore an outdated worker if the user changed the day again while it was queued.
        val didReset = ScheduleStore.resetSelectedDateIfDeadlineMatches(
            applicationContext,
            widgetId,
            expectedResetAtMillis
        )
        if (didReset) {
            ScheduleWidgetProvider.update(applicationContext, manager, widgetId)
        }
        return Result.success()
    }
}
