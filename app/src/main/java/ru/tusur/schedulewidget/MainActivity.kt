package ru.tusur.schedulewidget

import android.app.PendingIntent
import android.content.ClipData
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.widget.ImageView
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.appcompat.widget.AppCompatImageButton
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        // Adjust these three values to fine-tune the refresh and add-widget controls.
        private const val REFRESH_BUTTON_SIZE_DP = 44
        private const val REFRESH_ICON_PADDING_DP = 10
        private const val ADD_ICON_SIZE_DP = 18
        private const val STATE_SELECTED_DATE = "selected_schedule_date"
    }

    private val zone = ZoneId.of("Asia/Tomsk")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private var selectedDate: LocalDate = LocalDate.now(zone)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedDate = savedInstanceState?.getString(STATE_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now(zone)
        // Rebuild existing pinned widgets after an app update, then refresh cached data in the background.
        ScheduleWidgetProvider.updateAll(this)
        ScheduleWidgetProvider.enqueueRefresh(this, false)
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("tusur-refresh")
            .observe(this) { workInfos -> if (workInfos.any { it.state.isFinished }) render() }

        checkForUpdates(automatic = true)
    }

    override fun onResume() {
        super.onResume()
        render()
        continuePendingInstaller()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_DATE, selectedDate.toString())
        super.onSaveInstanceState(outState)
    }

    private fun render(restoreScrollY: Int? = null) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.background)
        }
        AppUi.installSystemBars(this, root)

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this@MainActivity, 20), AppUi.dp(this@MainActivity, 18), AppUi.dp(this@MainActivity, 20), AppUi.dp(this@MainActivity, 8))
        }

        val group = ScheduleStore.selectedGroup(this)
        val displayedDate = selectedDate
        val allLessons = ScheduleStore.load(this)
        val lessonsToday = allLessons.filter {
            Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == displayedDate
        }.sortedBy { it.start }
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()

        content.addView(AppUi.text(this, "Расписание занятий", 26f, AppUi.ink, true))
        content.addView(AppUi.spacer(this, 5))
        content.addView(AppUi.text(this, RussianDateText.mainDate(displayedDate), 21f, AppUi.muted))
        content.addView(AppUi.spacer(this, 12))

        val groupContent = AppUi.column(this, 17)
        val groupRow = AppUi.row(this)
        val groupLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        groupLabels.addView(AppUi.sectionLabel(this, "ГРУППА"))
        groupLabels.addView(AppUi.spacer(this, 5))
        groupLabels.addView(AppUi.text(this, group.name, 23f, AppUi.ink, true))
        groupLabels.addView(AppUi.spacer(this, 3))
        groupLabels.addView(AppUi.text(this, group.faculty, 13f, AppUi.muted))
        groupRow.addView(groupLabels, LinearLayout.LayoutParams(0, -2, 1f))
        val change = iconButton(R.drawable.ic_edit_24, "Изменить группу", iconPaddingDp = 13) {
            startActivity(Intent(this, GroupSelectionActivity::class.java))
        }
        groupRow.addView(change, LinearLayout.LayoutParams(AppUi.dp(this, 52), AppUi.dp(this, 52)))
        groupContent.addView(groupRow)
        content.addView(AppUi.card(this, groupContent))
        content.addView(AppUi.spacer(this, 12))
        // Restored verbatim from the working 2.5.3 implementation:
        // this container intercepts horizontal gestures inside the schedule only.
        val scheduleArea = ScheduleSwipeContainer(this) { dayDelta ->
            selectedDate = selectedDate.plusDays(dayDelta.toLong())
            render(restoreScrollY = scroll.scrollY)
        }
        val scheduleContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val count = if (lessonsToday.isEmpty()) "" else "${lessonsToday.size} ${lessonWord(lessonsToday.size)}"
        val countLabel = AppUi.text(this, count, 13f, AppUi.muted, true)
        if (lessonsToday.isEmpty()) countLabel.visibility = android.view.View.INVISIBLE
        scheduleContent.addView(countLabel)
        scheduleContent.addView(AppUi.spacer(this, 10))

        if (lessonsToday.isEmpty()) {
            if (allLessons.isEmpty()) {
                val errorContent = AppUi.column(this, 16)
                errorContent.addView(AppUi.text(
                    this,
                    ScheduleStore.error(this) ?: "Расписание пока не загружено. Обнови данные.",
                    13f,
                    AppUi.muted
                ))
                scheduleContent.addView(AppUi.card(this, errorContent))
            } else {
                scheduleContent.addView(emptyDayCard())
            }
        } else {
            lessonsToday.forEach { lesson ->
                val isCurrent = displayedDate == today && now >= lesson.start && now < lesson.end
                scheduleContent.addView(lessonCard(lesson, isCurrent))
                scheduleContent.addView(AppUi.spacer(this, 8))
            }
        }

        scheduleArea.addView(
            scheduleContent,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        content.addView(scheduleArea, LinearLayout.LayoutParams(-1, -2))

        content.addView(AppUi.spacer(this, 16))
        val actions = AppUi.row(this)
        val refresh = iconButton(R.drawable.ic_refresh_24, "Обновить расписание", iconPaddingDp = REFRESH_ICON_PADDING_DP) {
            ScheduleWidgetProvider.enqueueRefresh(this, true)
            Toast.makeText(this, "Обновляю", Toast.LENGTH_SHORT).show()
        }
        actions.addView(refresh, LinearLayout.LayoutParams(AppUi.dp(this, REFRESH_BUTTON_SIZE_DP), AppUi.dp(this, REFRESH_BUTTON_SIZE_DP)))
        val openBrowser = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Открыть в браузере"
            isAllCaps = false
            setTextColor(AppUi.accent)
            strokeColor = android.content.res.ColorStateList.valueOf(AppUi.outline)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(group.browserUrl))) }
        }
        val browserParams = LinearLayout.LayoutParams(0, AppUi.dp(this, 52), 1f).apply {
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
            // MaterialButton has default vertical insets; remove them so its visible height
            // matches the adjacent custom "Add to home screen" button.
            insetTop = 0
            insetBottom = 0
            setOnClickListener { startActivity(Intent(this@MainActivity, AppearanceActivity::class.java)) }
        }
        widgetContent.addView(editWidget, LinearLayout.LayoutParams(-1, AppUi.dp(this, 46)))
        widgetContent.addView(AppUi.spacer(this, 8))
        val addWidget = FrameLayout(this).apply {
            contentDescription = "Добавить виджет расписания на главный экран"
            isClickable = true
            isFocusable = true
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AppUi.dp(this@MainActivity, 24).toFloat()
                setColor(AppUi.accent)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(0x35FFFFFF), shape, null
            )
            val label = AppUi.text(this@MainActivity, "Добавить виджет на экран", 14f, 0xFF142449.toInt(), false).apply {
                gravity = Gravity.CENTER
            }
            addView(label, FrameLayout.LayoutParams(-1, -1))
            val plus = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_add_24)
                setColorFilter(0xFF142449.toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            addView(plus, FrameLayout.LayoutParams(
                AppUi.dp(this@MainActivity, ADD_ICON_SIZE_DP), AppUi.dp(this@MainActivity, ADD_ICON_SIZE_DP),
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply { marginStart = AppUi.dp(this@MainActivity, 12) })
            setOnClickListener { requestAddWidget() }
        }
        widgetContent.addView(addWidget, LinearLayout.LayoutParams(-1, AppUi.dp(this, 46)))
        content.addView(AppUi.card(this, widgetContent))

        content.addView(AppUi.spacer(this, 22))
        content.addView(AppUi.sectionLabel(this, "ФОНОВОЕ ОБНОВЛЕНИЕ"))
        content.addView(AppUi.spacer(this, 9))
        val batteryContent = AppUi.column(this, 16)
        batteryContent.addView(AppUi.text(this, "Работа в фоне", 17f, AppUi.ink, true))
        batteryContent.addView(AppUi.spacer(this, 4))
        val powerManager = getSystemService(PowerManager::class.java)
        val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            powerManager?.isIgnoringBatteryOptimizations(packageName) != true) {
            "Система может блокировать фоновую синхронизацию. Проверьте режим работы приложения в настройках батареи."
        } else {
            "Для приложения не действуют стандартные ограничения оптимизации батареи."
        }
        batteryContent.addView(AppUi.text(this, batteryStatus, 13f, AppUi.muted))
        batteryContent.addView(AppUi.spacer(this, 12))
        val batteryButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Настройки батареи"
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(AppUi.accent)
            strokeColor = android.content.res.ColorStateList.valueOf(AppUi.outline)
            setOnClickListener { openBatterySettings() }
        }
        batteryContent.addView(batteryButton, LinearLayout.LayoutParams(-1, AppUi.dp(this, 46)))
        content.addView(AppUi.card(this, batteryContent))

        // App updates are independent from the schedule worker and widget refresh lifecycle.
        content.addView(AppUi.spacer(this, 22))
        content.addView(AppUi.sectionLabel(this, "ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ"))
        content.addView(AppUi.spacer(this, 9))
        val updateContent = AppUi.column(this, 16)
        updateContent.addView(AppUi.text(this, "Новые версии", 17f, AppUi.ink, true))
        updateContent.addView(AppUi.spacer(this, 4))
        updateContent.addView(AppUi.text(
            this,
            "Проверяй обновления приложения. Возможно, найденная проблема уже была исправлена :)",
            13f,
            AppUi.muted
        ))
        updateContent.addView(AppUi.spacer(this, 12))
        val checkUpdatesButton = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Проверить обновления"
            textSize = 14f
            isAllCaps = false
            setTextColor(AppUi.accent)
            strokeColor = ColorStateList.valueOf(AppUi.outline)
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            setOnClickListener { onCheckUpdatesClicked() }
        }
        updateContent.addView(
            checkUpdatesButton,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 46))
        )

        content.addView(AppUi.card(this, updateContent))

        // Footer link for bug reports. The whole row opens the project's Telegram contact.
        content.addView(AppUi.spacer(this, 12))
        val reportFooter = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = AppUi.dp(this@MainActivity, 48)
            isClickable = true
            isFocusable = true
            contentDescription = "Сообщить об ошибке в Telegram"
            setOnClickListener {
                val telegramIntent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://t.me/kotyndy")
                )
                try {
                    startActivity(telegramIntent)
                } catch (_: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось открыть Telegram",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val helpIcon = android.widget.TextView(this).apply {
            text = "?"
            textSize = 11f
            setTextColor(AppUi.accent)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(
                    AppUi.dp(this@MainActivity, 1),
                    AppUi.accent
                )
            }
        }
        reportFooter.addView(
            helpIcon,
            LinearLayout.LayoutParams(
                AppUi.dp(this, 18),
                AppUi.dp(this, 18)
            )
        )

        val reportText = android.widget.TextView(this).apply {
            text = "Сообщить об ошибке"
            textSize = 12f
            setTextColor(AppUi.accent)
            gravity = Gravity.CENTER_VERTICAL
        }
        reportFooter.addView(
            reportText,
            LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = AppUi.dp(this@MainActivity, 8)
            }
        )

        content.addView(
            reportFooter,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 48))
        )

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        restoreScrollY?.let { y ->
            scroll.post { scroll.scrollTo(0, y.coerceAtLeast(0)) }
        }
    }

    private fun onCheckUpdatesClicked() {
        checkForUpdates(automatic = false)
    }

    private fun checkForUpdates(automatic: Boolean) {
        AppUpdateManager.checkLatest(this) { result ->
            if (isFinishing || isDestroyed) return@checkLatest
            result.onSuccess { update ->
                if (update == null) {
                    if (!automatic) {
                        Toast.makeText(this, "Установлена последняя версия :)", Toast.LENGTH_SHORT).show()
                    }
                } else if (!automatic || !AppUpdateManager.wasDismissed(this, update.tagName)) {
                    showUpdateDialog(update)
                }
            }.onFailure { error ->
                if (!automatic) {
                    Toast.makeText(
                        this,
                        error.message ?: "Не удалось проверить обновления. Проверь интернет-соединение.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showUpdateDialog(update: AppUpdateManager.AvailableUpdate) {
        if (isFinishing || isDestroyed) return
        val notes = update.releaseNotes.trim().take(420)
        val message = buildString {
            append("Доступна новая версия ")
            append(update.tagName.removePrefix("v"))
            append(".")
            if (notes.isNotBlank()) {
                append("\n\n")
                append(notes)
            }
            append("\n\n")
            append("После загрузки откроется окно установки. Подтверди обновление там.")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Доступно обновление")
            .setMessage(message)
            .setPositiveButton("Установить обновление") { _, _ -> downloadAndInstall(update) }
            .setNegativeButton("Позже") { _, _ -> AppUpdateManager.markDismissed(this, update.tagName) }
            .show()
    }

    private fun downloadAndInstall(update: AppUpdateManager.AvailableUpdate) {
        Toast.makeText(this, "Загружаю обновление…", Toast.LENGTH_SHORT).show()
        AppUpdateManager.downloadApk(this, update) { result ->
            if (isFinishing || isDestroyed) return@downloadApk
            result.onSuccess { file ->
                val validationError = AppUpdateManager.validateDownloadedApk(this, file)
                if (validationError != null) {
                    Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
                    return@downloadApk
                }
                Toast.makeText(this, "APK загружен и проверен. Подготавливаю установку…", Toast.LENGTH_SHORT).show()
                installDownloadedApk(file)
            }.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "Не удалось загрузить обновление",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun installDownloadedApk(file: File) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "Файл обновления не найден. Проверь обновления ещё раз.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AppUpdateManager.rememberPendingInstall(this, file)
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:$packageName")
            )
            try {
                startActivity(settingsIntent)
                Toast.makeText(this, "Разреши установку приложений из этого источника, затем вернись сюда.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Разреши установку APK в настройках приложения.", Toast.LENGTH_LONG).show()
            }
            return
        }
        launchPackageInstaller(file)
    }

    private fun continuePendingInstaller() {
        val pending = AppUpdateManager.takePendingInstall(this) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AppUpdateManager.rememberPendingInstall(this, pending)
            return
        }
        launchPackageInstaller(pending)
    }

    private fun launchPackageInstaller(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val installerIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newUri(contentResolver, "Обновление приложения", uri)
            }
            startActivity(installerIntent)
        } catch (error: Exception) {
            Toast.makeText(
                this,
                "Не удалось открыть установщик Android: ${error.message ?: "неизвестная ошибка"}",
                Toast.LENGTH_LONG
            ).show()
            AppUpdateManager.openReleasePage(this)
        }
    }

    private fun emptyDayCard(): FrameLayout {
        val outer = FrameLayout(this).apply {
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF72D6D0.toInt(), 0xFF8ED66F.toInt(), 0xFFB99AFF.toInt(), 0xFFFFC56B.toInt())
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AppUi.dp(this@MainActivity, 19).toFloat()
            }
            background = gradient
            setPadding(AppUi.dp(this@MainActivity, 2), AppUi.dp(this@MainActivity, 2), AppUi.dp(this@MainActivity, 2), AppUi.dp(this@MainActivity, 2))
        }
        val inner = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AppUi.dp(this@MainActivity, 17).toFloat()
                setColor(AppUi.surface)
            }
        }
        val message = AppUi.text(this, "В этот день нет занятий 🎉", 16f, AppUi.ink, true).apply {
            gravity = Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }
        inner.addView(message, FrameLayout.LayoutParams(-1, AppUi.dp(this, 88)))
        outer.addView(inner, FrameLayout.LayoutParams(-1, -1))
        return outer.apply {
            minimumHeight = AppUi.dp(this@MainActivity, 92)
            layoutParams = LinearLayout.LayoutParams(-1, AppUi.dp(this@MainActivity, 92))
        }
    }

    private fun iconButton(iconRes: Int, description: String, iconPaddingDp: Int = 13, action: () -> Unit): AppCompatImageButton =
        AppCompatImageButton(this).apply {
            contentDescription = description
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(AppUi.accent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(AppUi.dp(this@MainActivity, iconPaddingDp), AppUi.dp(this@MainActivity, iconPaddingDp), AppUi.dp(this@MainActivity, iconPaddingDp), AppUi.dp(this@MainActivity, iconPaddingDp))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppUi.surface)
                setStroke(AppUi.dp(this@MainActivity, 1), AppUi.outline)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun requestAddWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, ScheduleWidgetProvider::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                val callbackIntent = Intent(this, WidgetPinReceiver::class.java).apply {
                    action = WidgetPinReceiver.ACTION_WIDGET_PINNED
                    data = android.net.Uri.parse("tusur://widget-pinned/${System.currentTimeMillis()}")
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val callback = PendingIntent.getBroadcast(
                    this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), callbackIntent, flags
                )
                val accepted = runCatching { manager.requestPinAppWidget(provider, null, callback) }.getOrDefault(false)
                if (accepted) {
                    // true means that the launcher accepts pin requests, not that the widget
                    // has already been placed. The launcher displays its own confirmation UI.
                    Toast.makeText(this, "Если ничего не появилось - добавь виджет вручную.", Toast.LENGTH_LONG).show()
                    return
                }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавление виджета")
            .setMessage("Этот главный экран не поддержал быстрый запрос. Зажмите пустое место рабочего стола, откройте «Виджеты» и выберите «Расписание ТУСУР».")
            .setPositiveButton("Открыть рабочий стол") { _, _ ->
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(home) }
            }
            .setNegativeButton("Понятно", null)
            .show()
    }

    private fun openBatterySettings() {
        val primary = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName"))
        try {
            startActivity(primary)
        } catch (_: Exception) {
            try { startActivity(appDetails) } catch (_: Exception) {
                Toast.makeText(this, "Откройте настройки приложения и раздел «Батарея» вручную.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun lessonCard(lesson: Lesson, isCurrent: Boolean): MaterialCardView {
        val cardContent = AppUi.row(this).apply {
            setPadding(AppUi.dp(this@MainActivity, 14), AppUi.dp(this@MainActivity, 13), AppUi.dp(this@MainActivity, 14), AppUi.dp(this@MainActivity, 13))
        }
        val timeColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeColumn.addView(AppUi.text(this, timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone)), 13f, AppUi.muted, true))
        timeColumn.addView(AppUi.spacer(this, 4))
        timeColumn.addView(AppUi.text(this, timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone)), 12f, AppUi.muted))
        cardContent.addView(timeColumn, LinearLayout.LayoutParams(AppUi.dp(this, 58), -2))
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(AppUi.text(this, lesson.title, 14f, AppUi.ink, true))
        details.addView(AppUi.spacer(this, 3))
        details.addView(AppUi.text(this, LessonText.room(lesson.room), 11f, AppUi.muted))
        cardContent.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        if (isCurrent) {
            val tag = AppUi.text(this, "СЕЙЧАС", 9f, AppUi.accent, true).apply {
                gravity = Gravity.TOP or Gravity.END
                letterSpacing = 0.04f
            }
            cardContent.addView(tag)
        }
        val card = AppUi.card(this, cardContent, 17)
        if (isCurrent) card.setCardBackgroundColor(0xFF26334D.toInt())
        val borderColor = LessonTypeStyle.color(lesson.type, "${lesson.note} ${lesson.teacher}")
        if (LessonTypeStyle.isDashed(lesson.type, "${lesson.note} ${lesson.teacher}")) {
            // MaterialCardView exposes a solid stroke only. Use a transparent foreground
            // shape for the dotted independent-work outline while keeping the card fill.
            card.strokeWidth = 0
            card.foreground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = AppUi.dp(this@MainActivity, 17).toFloat()
                setStroke(
                    AppUi.dp(this@MainActivity, 2),
                    borderColor,
                    AppUi.dp(this@MainActivity, 5).toFloat(),
                    AppUi.dp(this@MainActivity, 4).toFloat()
                )
            }
        } else {
            card.strokeWidth = AppUi.dp(this, 2)
            card.strokeColor = borderColor
        }
        return card
    }

    private fun lessonWord(count: Int): String = when {
        count % 10 == 1 && count % 100 != 11 -> "пара"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "пары"
        else -> "пар"
    }
}
