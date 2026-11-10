package ru.tusur.schedulewidget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.view.ViewGroup

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 48, 28, 28)
            setBackgroundColor(Color.rgb(244, 246, 249))
        }
        fun text(value: String, size: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(Color.rgb(25, 31, 42))
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            }
        layout.addView(text("TUSUR Schedule", 26f, true))
        layout.addView(text("Группа 576-1 · Факультет вычислительных систем", 15f))
        layout.addView(text("Добавьте виджет на рабочий стол: зажмите пустое место → Виджеты → TUSUR Schedule. Размер можно менять растягиванием рамки.", 16f))
        val refresh = Button(this).apply {
            this.text = "Обновить расписание"
            setOnClickListener { ScheduleWidgetProvider.enqueueRefresh(this@MainActivity, true) }
        }
        layout.addView(refresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val site = Button(this).apply {
            this.text = "Открыть расписание ТУСУРа"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://timetable.tusur.ru/faculties/fvs/groups/576-1")))
            }
        }
        layout.addView(site, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(text("Расписание кэшируется на телефоне. При отсутствии интернета виджет продолжает показывать последние загруженные данные. Для получения изменений требуется подключение к сети.", 13f))
        setContentView(layout)
        ScheduleWidgetProvider.enqueueRefresh(this, false)
    }
}
