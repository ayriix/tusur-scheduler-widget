package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_PINNED) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val manager = AppWidgetManager.getInstance(context)
            ScheduleWidgetProvider.update(context, manager, widgetId)
            Toast.makeText(context, "Виджет TUSUR Schedule добавлен", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_WIDGET_PINNED = "ru.tusur.schedulewidget.WIDGET_PINNED"
    }
}
