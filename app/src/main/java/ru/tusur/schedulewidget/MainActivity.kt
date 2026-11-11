package ru.tusur.schedulewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val zone = ZoneId.of("Asia/Tomsk")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Rebuild existing pinned widgets after an app update, then refresh cached data in the background.
        ScheduleWidgetProvider.updateAll(this)
        ScheduleWidgetProvider.enqueueRefresh(this, false)
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("tusur-refresh")
            .observe(this) { workInfos -> if (workInfos.any { it.state.isFinished }) render() }
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
        AppUi.installSystemBars(this, root)

        val scroll = ScrollView(this).apply { clipToPadding = false; setFillViewport(false) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this@MainActivity, 20), AppUi.dp(this@MainActivity, 18), AppUi.dp(this@MainActivity, 20), AppUi.dp(this@MainActivity, 28))
        }

        val group = ScheduleStore.selectedGroup(this)
        val today = LocalDate.now(zone)
        val lessonsToday = ScheduleStore.load(this).filter {
            Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == today
        }.sortedBy { it.start }
        val now = System.currentTimeMillis()
        val current = lessonsToday.firstOrNull { now >= it.start && now < it.end }

        content.addView(AppUi.text(this, "Расписание занятий", 26f, AppUi.ink, true))
        content.addView(AppUi.spacer(this, 5))
        content.addView(AppUi.text(this, dateFormatter.format(today).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) }, 21f, AppUi.muted))
        content.addView(AppUi.spacer(this, 19))

        val groupContent = AppUi.column(this, 17)
        val groupRow = AppUi.row(this)
        val groupLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        groupLabels.addView(AppUi.sectionLabel(this, "ГРУППА"))
        groupLabels.addView(AppUi.spacer(this, 5))
        groupLabels.addView(AppUi.text(this, group.name, 23f, AppUi.ink, true))
        groupLabels.addView(AppUi.spacer(this, 3))
        groupLabels.addView(AppUi.text(this, group.faculty, 13f, AppUi.muted))
        groupRow.addView(groupLabels, LinearLayout.LayoutParams(0, -2, 1f))
        val change = iconButton(R.drawable.ic_edit_24, "Изменить группу") {
            startActivity(Intent(this, GroupSelectionActivity::class.java))
        }
        groupRow.addView(change, LinearLayout.LayoutParams(AppUi.dp(this, 48), AppUi.dp(this, 46)))
        groupContent.addView(groupRow)
        content.addView(AppUi.card(this, groupContent))
        content.addView(AppUi.spacer(this, 22))

        val count = if (lessonsToday.isEmpty()) "Нет пар" else "${lessonsToday.size} ${lessonWord(lessonsToday.size)}"
        content.addView(AppUi.text(this, count, 13f, AppUi.muted, true))
        content.addView(AppUi.spacer(this, 10))

        if (lessonsToday.isEmpty()) {
            val empty = AppUi.column(this, 18)
            empty.addView(AppUi.text(this, "На сегодня занятий нет", 16f, AppUi.ink, true))
            empty.addView(AppUi.spacer(this, 5))
            empty.addView(AppUi.text(this, ScheduleStore.error(this) ?: "Расписание появится здесь после обновления.", 13f, AppUi.muted))
            content.addView(AppUi.card(this, empty))
        } else {
            lessonsToday.forEach { lesson ->
                val isCurrent = now >= lesson.start && now < lesson.end
                content.addView(lessonCard(lesson, isCurrent))
                content.addView(AppUi.spacer(this, 8))
            }
        }

        content.addView(AppUi.spacer(this, 16))
        val actions = AppUi.row(this)
        val refresh = iconButton(R.drawable.ic_refresh_24, "Обновить расписание") {
            ScheduleWidgetProvider.enqueueRefresh(this, true)
            Toast.makeText(this, "Обновление запущено", Toast.LENGTH_SHORT).show()
        }
        actions.addView(refresh, LinearLayout.LayoutParams(AppUi.dp(this, 52), AppUi.dp(this, 50)))
        val openBrowser = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Открыть в браузере"
            isAllCaps = false
            setTextColor(AppUi.accent)
            strokeColor = android.content.res.ColorStateList.valueOf(AppUi.outline)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(group.browserUrl))) }
        }
        val browserParams = LinearLayout.LayoutParams(0, AppUi.dp(this, 50), 1f).apply {
            marginStart = AppUi.dp(this@MainActivity, 10)
        }
        actions.addView(openBrowser, browserParams)
        content.addView(actions)

        content.addView(AppUi.spacer(this, 25))
        content.addView(AppUi.sectionLabel(this, "НАСТРОЙКИ ВИДЖЕТА"))
        content.addView(AppUi.spacer(this, 9))
        val widgetContent = AppUi.column(this, 16)
        widgetContent.addView(AppUi.text(this, "Оформление и размещение", 17f, AppUi.ink, true))
        widgetContent.addView(AppUi.spacer(this, 4))
        widgetContent.addView(AppUi.text(this, "Настрой цвета и форму виджета или добавь его на главный экран.", 13f, AppUi.muted))
        widgetContent.addView(AppUi.spacer(this, 12))
        val editWidget = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Редактор виджета"
            isAllCaps = false
            setTextColor(AppUi.accent)
            strokeColor = android.content.res.ColorStateList.valueOf(AppUi.outline)
            setOnClickListener { startActivity(Intent(this@MainActivity, AppearanceActivity::class.java)) }
        }
        widgetContent.addView(editWidget, LinearLayout.LayoutParams(-1, AppUi.dp(this, 46)))
        widgetContent.addView(AppUi.spacer(this, 8))
        val addWidget = MaterialButton(this).apply {
            text = "Добавить на экран"
            isAllCaps = false
            setIcon(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_add_24))
            iconTint = android.content.res.ColorStateList.valueOf(0xFF142449.toInt())
            setOnClickListener { requestAddWidget() }
        }
        widgetContent.addView(addWidget, LinearLayout.LayoutParams(-1, AppUi.dp(this, 46)))
        content.addView(AppUi.card(this, widgetContent))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun iconButton(iconRes: Int, description: String, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = ""
            contentDescription = description
            isAllCaps = false
            icon = ContextCompat.getDrawable(this@MainActivity, iconRes)
            iconTint = android.content.res.ColorStateList.valueOf(AppUi.accent)
            iconPadding = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            strokeColor = android.content.res.ColorStateList.valueOf(AppUi.outline)
            setOnClickListener { action() }
        }

    private fun requestAddWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, ScheduleWidgetProvider::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                val accepted = manager.requestPinAppWidget(provider, null, null)
                if (accepted) {
                    Toast.makeText(this, "Подтверди добавление в окне главного экрана", Toast.LENGTH_LONG).show()
                    return
                }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавление виджета")
            .setMessage("Текущий главный экран не поддерживает вызов системного окна добавления из приложения. Зажми пустое место рабочего стола, открой «Виджеты» и выбери TUSUR Schedule.")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun lessonCard(lesson: Lesson, isCurrent: Boolean): MaterialCardView {
        val cardContent = AppUi.row(this).apply {
            setPadding(AppUi.dp(this@MainActivity, 14), AppUi.dp(this@MainActivity, 13), AppUi.dp(this@MainActivity, 14), AppUi.dp(this@MainActivity, 13))
        }
        val timeColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeColumn.addView(AppUi.text(this, timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone)), 13f, AppUi.muted, true))
        timeColumn.addView(AppUi.text(this, timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone)), 12f, AppUi.muted))
        cardContent.addView(timeColumn, LinearLayout.LayoutParams(AppUi.dp(this, 58), -2))
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(AppUi.text(this, lesson.title, 15f, AppUi.ink, true))
        details.addView(AppUi.spacer(this, 3))
        details.addView(AppUi.text(this, LessonText.room(lesson.room), 12f, AppUi.muted))
        cardContent.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        if (isCurrent) {
            val tag = AppUi.text(this, "СЕЙЧАС", 9f, AppUi.accent, true).apply {
                gravity = Gravity.TOP or Gravity.END
                letterSpacing = 0.04f
            }
            cardContent.addView(tag)
        }
        val card = AppUi.card(this, cardContent, 17)
        if (isCurrent) {
            card.setCardBackgroundColor(0xFF26334D.toInt())
            card.strokeColor = 0xFF5876B8.toInt()
        }
        return card
    }

    private fun lessonWord(count: Int): String = when {
        count % 10 == 1 && count % 100 != 11 -> "пара"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "пары"
        else -> "пар"
    }
}
