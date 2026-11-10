package ru.tusur.schedulewidget

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val zone = ZoneId.of("Asia/Tomsk")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScheduleWidgetProvider.enqueueRefresh(this, false)
        androidx.work.WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData("tusur-refresh")
            .observe(this) { workInfos ->
                if (workInfos.any { it.state.isFinished }) render()
            }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.background)
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "TUSUR Schedule"
            setTitleTextColor(AppUi.ink)
            setBackgroundColor(AppUi.background)
            elevation = 0f
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, AppUi.dp(this, 58)))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this@MainActivity, 20), AppUi.dp(this@MainActivity, 10), AppUi.dp(this@MainActivity, 20), AppUi.dp(this@MainActivity, 30))
        }

        val group = ScheduleStore.selectedGroup(this)
        val today = LocalDate.now(zone)
        val lessonsToday = ScheduleStore.load(this).filter {
            Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == today
        }.sortedBy { it.start }
        val now = System.currentTimeMillis()
        val current = lessonsToday.firstOrNull { now >= it.start && now < it.end }
        val next = lessonsToday.firstOrNull { it.start > now }

        content.addView(AppUi.sectionLabel(this, "РАСПИСАНИЕ ЗАНЯТИЙ"))
        content.addView(AppUi.spacer(this, 7))
        content.addView(AppUi.text(this, "${dateFormatter.format(today).replaceFirstChar { it.titlecase(Locale("ru")) }}", 27f, AppUi.ink, true))
        content.addView(AppUi.spacer(this, 18))

        val groupContent = AppUi.column(this, 17)
        val groupRow = AppUi.row(this)
        val groupLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        groupLabels.addView(AppUi.sectionLabel(this, "ТВОЯ ГРУППА"))
        groupLabels.addView(AppUi.spacer(this, 5))
        groupLabels.addView(AppUi.text(this, group.name, 22f, AppUi.ink, true))
        groupLabels.addView(AppUi.spacer(this, 3))
        groupLabels.addView(AppUi.text(this, group.faculty, 13f, AppUi.muted))
        groupRow.addView(groupLabels, LinearLayout.LayoutParams(0, -2, 1f))
        val change = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Изменить"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivity, GroupSelectionActivity::class.java)) }
        }
        groupRow.addView(change)
        groupContent.addView(groupRow)
        content.addView(AppUi.card(this, groupContent))
        content.addView(AppUi.spacer(this, 22))

        val todayRow = AppUi.row(this)
        todayRow.addView(AppUi.sectionLabel(this, "СЕГОДНЯШНИЕ ПАРЫ"), LinearLayout.LayoutParams(0, -2, 1f))
        val countText = if (lessonsToday.isEmpty()) "Нет пар" else "${lessonsToday.size} ${lessonWord(lessonsToday.size)}"
        todayRow.addView(AppUi.text(this, countText, 12f, AppUi.muted))
        content.addView(todayRow)
        content.addView(AppUi.spacer(this, 10))

        when {
            lessonsToday.isEmpty() -> {
                val empty = AppUi.column(this, 18)
                empty.addView(AppUi.text(this, "На сегодня занятий нет", 16f, AppUi.ink, true))
                empty.addView(AppUi.spacer(this, 5))
                empty.addView(AppUi.text(this, ScheduleStore.error(this) ?: "Обнови расписание, чтобы проверить изменения.", 13f, AppUi.muted))
                content.addView(AppUi.card(this, empty))
            }
            else -> lessonsToday.forEach { lesson ->
                val isCurrent = now >= lesson.start && now < lesson.end
                val isNext = !isCurrent && next?.start == lesson.start
                content.addView(lessonCard(lesson, isCurrent, isNext))
                content.addView(AppUi.spacer(this, 8))
            }
        }

        if (current != null || next != null) {
            content.addView(AppUi.spacer(this, 3))
            val status = when {
                current != null -> "Сейчас идёт: ${current.title}"
                next != null -> "Следующая пара в ${timeFormatter.format(Instant.ofEpochMilli(next.start).atZone(zone))}"
                else -> ""
            }
            content.addView(AppUi.text(this, status, 12f, AppUi.muted))
        }

        content.addView(AppUi.spacer(this, 22))
        val refresh = MaterialButton(this).apply {
            text = "Обновить расписание"
            isAllCaps = false
            setOnClickListener {
                ScheduleWidgetProvider.enqueueRefresh(this@MainActivity, true)
                android.widget.Toast.makeText(this@MainActivity, "Обновление запущено", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(refresh, LinearLayout.LayoutParams(-1, AppUi.dp(this, 50)))
        content.addView(AppUi.spacer(this, 8))
        val openSite = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Открыть расписание в браузере"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(group.browserUrl)))
            }
        }
        content.addView(openSite, LinearLayout.LayoutParams(-1, AppUi.dp(this, 50)))

        content.addView(AppUi.spacer(this, 22))
        content.addView(AppUi.sectionLabel(this, "ВИДЖЕТ"))
        content.addView(AppUi.spacer(this, 9))
        val widgetContent = AppUi.column(this, 16)
        widgetContent.addView(AppUi.text(this, "Настрой внешний вид", 17f, AppUi.ink, true))
        widgetContent.addView(AppUi.spacer(this, 4))
        widgetContent.addView(AppUi.text(this, "Цвета, фон и степень закругления можно подобрать под рабочий стол.", 13f, AppUi.muted))
        widgetContent.addView(AppUi.spacer(this, 10))
        val appearance = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Настроить виджет"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivity, AppearanceActivity::class.java)) }
        }
        widgetContent.addView(appearance, LinearLayout.LayoutParams(-1, AppUi.dp(this, 46)))
        content.addView(AppUi.card(this, widgetContent))
        content.addView(AppUi.spacer(this, 15))
        content.addView(AppUi.text(this, "Добавление: зажми пустое место на рабочем столе → Виджеты → TUSUR Schedule. Список занятий прокручивается внутри виджета, а стрелки внизу переключают дни.", 12f, AppUi.muted))
        val updatedAt = ScheduleStore.updatedAt(this)
        if (updatedAt > 0) {
            content.addView(AppUi.spacer(this, 10))
            content.addView(AppUi.text(this, "Последнее обновление: ${DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru")).format(Instant.ofEpochMilli(updatedAt).atZone(zone))}", 11f, AppUi.muted))
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun lessonCard(lesson: Lesson, isCurrent: Boolean, isNext: Boolean): MaterialCardView {
        val cardContent = AppUi.row(this).apply {
            setPadding(AppUi.dp(this@MainActivity, 14), AppUi.dp(this@MainActivity, 12), AppUi.dp(this@MainActivity, 14), AppUi.dp(this@MainActivity, 12))
        }
        val timeColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeColumn.addView(AppUi.text(this, timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone)), 13f, AppUi.muted, true))
        timeColumn.addView(AppUi.text(this, timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone)), 12f, AppUi.muted))
        cardContent.addView(timeColumn, LinearLayout.LayoutParams(AppUi.dp(this, 58), -2))
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(AppUi.text(this, lesson.title, 15f, AppUi.ink, true))
        if (lesson.room.isNotBlank()) {
            details.addView(AppUi.spacer(this, 3))
            details.addView(AppUi.text(this, lesson.room, 12f, AppUi.muted))
        }
        cardContent.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        if (isCurrent || isNext) {
            val tag = AppUi.text(this, if (isCurrent) "СЕЙЧАС" else "ДАЛЕЕ", 9f, AppUi.accent, true).apply {
                gravity = Gravity.TOP or Gravity.END
                letterSpacing = 0.04f
            }
            cardContent.addView(tag)
        }
        val card = AppUi.card(this, cardContent, 17)
        if (isCurrent) {
            card.setCardBackgroundColor(0xFFF0DED3.toInt())
            card.strokeColor = 0xFFD9B5A3.toInt()
        } else if (isNext) {
            card.strokeColor = 0xFFB98A75.toInt()
        }
        return card
    }

    private fun lessonWord(count: Int): String = when {
        count % 10 == 1 && count % 100 != 11 -> "пара"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "пары"
        else -> "пар"
    }
}
