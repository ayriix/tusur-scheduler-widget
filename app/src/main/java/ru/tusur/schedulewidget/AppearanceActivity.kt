
package ru.tusur.schedulewidget

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.slider.Slider
import java.util.Locale

class AppearanceActivity : AppCompatActivity() {

    private lateinit var preview: MaterialCardView
    private lateinit var previewGroup: TextView
    private lateinit var previewDate: TextView
    private lateinit var radiusLabel: TextView

    private val colorRows = mutableMapOf<String, Pair<View, TextView>>()

    private data class PreviewLesson(
        val card: MaterialCardView,
        val title: TextView,
        val info: TextView,
        val isCurrent: Boolean,
        val type: String
    )

    private val previewLessons = mutableListOf<PreviewLesson>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        colorRows.clear()
        previewLessons.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.background)
        }

        AppUi.installSystemBars(this, root)

        val toolbar = MaterialToolbar(this).apply {
            title = "Редактор виджета"
            setTitleTextColor(AppUi.ink)
            setBackgroundColor(AppUi.background)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationIconTint(AppUi.ink)
            setNavigationOnClickListener { finish() }
            elevation = 0f
        }

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 58))
        )

        val scroll = ScrollView(this).apply {
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AppUi.dp(this@AppearanceActivity, 20),
                AppUi.dp(this@AppearanceActivity, 14),
                AppUi.dp(this@AppearanceActivity, 20),
                AppUi.dp(this@AppearanceActivity, 28)
            )
        }

        content.addView(
            AppUi.text(
                this,
                "Цвета и форма применяются сразу ко всем установленным виджетам.",
                13f,
                AppUi.muted
            )
        )

        content.addView(AppUi.spacer(this, 18))
        content.addView(AppUi.sectionLabel(this, "ПРЕДПРОСМОТР"))
        content.addView(AppUi.spacer(this, 9))

        // Контейнер предпросмотра
        val previewContent = AppUi.column(this, 9)

        // Заголовок: группа слева, дата справа
        val header = AppUi.row(this)

        previewGroup = AppUi.text(
            this,
            "Группа: ${ScheduleStore.selectedGroup(this).name}",
            12f,
            AppUi.ink,
            true
        )

        previewDate = AppUi.text(
            this,
            "Пн · 1 марта",
            11f,
            AppUi.muted
        ).apply {
            gravity = Gravity.END
        }

        header.addView(
            previewGroup,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(
            previewDate,
            LinearLayout.LayoutParams(-2, -2)
        )

        previewContent.addView(header)
        // Отступ между заголовком виджета и первой карточкой пары.
        previewContent.addView(AppUi.spacer(this, 10))

        // Первая пара: текущая
        previewContent.addView(
            createPreviewLesson(
                start = "10:40",
                end = "12:15",
                subject = "Математика",
                room = "ФЭТ 428 кабинет",
                type = "lecture",
                isCurrent = true
            ),
            LinearLayout.LayoutParams(-1, -2)
        )

        // Отступ между карточками пар.
        previewContent.addView(AppUi.spacer(this, 8))

        // Вторая пара: обычная
        previewContent.addView(
            createPreviewLesson(
                start = "13:15",
                end = "14:50",
                subject = "Информатика",
                room = "ФЭТ 327 кабинет",
                type = "laboratory",
                isCurrent = false
            ),
            LinearLayout.LayoutParams(-1, -2)
        )

        preview = AppUi.card(
            this,
            previewContent,
            WidgetAppearance.radius(this).toInt()
        )

        preview.strokeWidth = 0

        content.addView(
            preview,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 188))
        )

        // Форма
        content.addView(AppUi.spacer(this, 24))
        content.addView(AppUi.sectionLabel(this, "ФОРМА"))
        content.addView(AppUi.spacer(this, 8))

        val radiusContent = AppUi.column(this, 16)

        radiusLabel = AppUi.text(
            this,
            "Радиус углов · ${WidgetAppearance.radius(this).toInt()} dp",
            14f,
            AppUi.ink,
            true
        )

        radiusContent.addView(radiusLabel)

        val radiusSlider = Slider(this).apply {
            valueFrom = 0f
            valueTo = 28f
            stepSize = 1f
            value = WidgetAppearance.radius(this@AppearanceActivity)

            thumbHeight = AppUi.dp(this@AppearanceActivity, 32)

            contentDescription = "Радиус углов виджета"

            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    WidgetAppearance.setRadius(
                        this@AppearanceActivity,
                        value
                    )

                    radiusLabel.text =
                        "Радиус углов · ${value.toInt()} dp"

                    preview.radius = AppUi.dp(
                        this@AppearanceActivity,
                        value.toInt()
                    ).toFloat()

                    ScheduleWidgetProvider.updateAll(
                        this@AppearanceActivity
                    )
                }
            }
        }

        radiusContent.addView(
            radiusSlider,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 44))
        )

        val hints = AppUi.row(this)

        hints.addView(
            AppUi.text(this, "Квадратный", 11f, AppUi.muted),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        hints.addView(
            AppUi.text(this, "Скруглённый", 11f, AppUi.muted)
        )

        radiusContent.addView(hints)
        content.addView(AppUi.card(this, radiusContent))

        // Цвета
        content.addView(AppUi.spacer(this, 24))

        addColorSection(
            content,
            "ФОН ВИДЖЕТА",
            "background",
            WidgetAppearance.background(this)
        )

        content.addView(AppUi.spacer(this, 14))

        addColorSection(
            content,
            "ФОН ОБЫЧНЫХ ПАР",
            "lesson",
            WidgetAppearance.lesson(this)
        )

        content.addView(AppUi.spacer(this, 14))

        addColorSection(
            content,
            "ФОН ТЕКУЩЕЙ ПАРЫ",
            "current",
            WidgetAppearance.current(this)
        )

        // Сброс оформления
        content.addView(AppUi.spacer(this, 23))

        val reset = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Сбросить настройки"
            isAllCaps = false
            setTextColor(AppUi.accent)
            strokeColor = android.content.res.ColorStateList.valueOf(
                AppUi.outline
            )

            setOnClickListener {
                WidgetAppearance.setRadius(
                    this@AppearanceActivity,
                    WidgetAppearance.DEFAULT_RADIUS
                )

                WidgetAppearance.setBackground(
                    this@AppearanceActivity,
                    WidgetAppearance.DEFAULT_BACKGROUND
                )

                WidgetAppearance.setLesson(
                    this@AppearanceActivity,
                    WidgetAppearance.DEFAULT_LESSON
                )

                WidgetAppearance.setCurrent(
                    this@AppearanceActivity,
                    WidgetAppearance.DEFAULT_CURRENT
                )

                render()

                ScheduleWidgetProvider.updateAll(
                    this@AppearanceActivity
                )
            }
        }

        content.addView(
            reset,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 48))
        )

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
        refreshPreview()
    }

    /**
     * Создаёт карточку пары с вертикальным временем слева.
     * Структура соответствует расположению элементов
     * в настоящем виджете.
     */
    private fun createPreviewLesson(
        start: String,
        end: String,
        subject: String,
        room: String,
        type: String,
        isCurrent: Boolean
    ): MaterialCardView {

        val row = AppUi.row(this).apply {
            setPadding(
                AppUi.dp(this@AppearanceActivity, 10),
                AppUi.dp(this@AppearanceActivity, 8),
                AppUi.dp(this@AppearanceActivity, 10),
                AppUi.dp(this@AppearanceActivity, 8)
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        // Время: отдельная вертикальная колонка
        val timeColumn = AppUi.column(this, 1)

        timeColumn.addView(
            AppUi.text(this, start, 11f, AppUi.muted)
        )

        timeColumn.addView(
            AppUi.text(this, end, 11f, AppUi.muted)
        )

        row.addView(
            timeColumn,
            LinearLayout.LayoutParams(
                AppUi.dp(this, 58),
                -2
            )
        )

        // Название и аудитория
        val lessonContent = AppUi.column(this, 3)

        val title = AppUi.text(
            this,
            subject,
            13f,
            AppUi.ink,
            true
        )

        val info = AppUi.text(
            this,
            room,
            11f,
            AppUi.muted
        )

        lessonContent.addView(title)
        lessonContent.addView(info)

        row.addView(
            lessonContent,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val card = AppUi.card(this, row, 12).apply {
            strokeWidth = AppUi.dp(this@AppearanceActivity, 2)
            strokeColor = LessonTypeStyle.color(type)
        }

        previewLessons.add(
            PreviewLesson(
                card = card,
                title = title,
                info = info,
                isCurrent = isCurrent,
                type = type
            )
        )

        return card
    }

    private fun addColorSection(
        parent: LinearLayout,
        title: String,
        key: String,
        color: Int
    ) {
        parent.addView(AppUi.sectionLabel(this, title))
        parent.addView(AppUi.spacer(this, 8))

        val inner = AppUi.row(this).apply {
            setPadding(
                AppUi.dp(this@AppearanceActivity, 14),
                AppUi.dp(this@AppearanceActivity, 12),
                AppUi.dp(this@AppearanceActivity, 12),
                AppUi.dp(this@AppearanceActivity, 12)
            )
        }

        val swatch = View(this).apply {
            background = swatchDrawable(color)
        }

        inner.addView(
            swatch,
            LinearLayout.LayoutParams(
                AppUi.dp(this, 42),
                AppUi.dp(this, 42)
            )
        )

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AppUi.dp(this@AppearanceActivity, 12),
                0,
                AppUi.dp(this@AppearanceActivity, 8),
                0
            )
        }

        textColumn.addView(
            AppUi.text(
                this,
                title.lowercase().replaceFirstChar {
                    it.titlecase(Locale.forLanguageTag("ru"))
                },
                13f,
                AppUi.ink,
                true
            )
        )

        val hex = AppUi.text(this, hex(color), 12f, AppUi.muted)

        textColumn.addView(AppUi.spacer(this, 3))
        textColumn.addView(hex)

        inner.addView(
            textColumn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val pick = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Изменить"
            isAllCaps = false
            setTextColor(AppUi.accent)
            strokeColor = android.content.res.ColorStateList.valueOf(
                AppUi.outline
            )

            setOnClickListener {
                showColorEditor(
                    title.lowercase(Locale.forLanguageTag("ru")),
                    colorFor(key),
                    key
                )
            }
        }

        inner.addView(pick)

        val card = AppUi.card(this, inner, 16)

        parent.addView(
            card,
            LinearLayout.LayoutParams(-1, -2)
        )

        colorRows[key] = swatch to hex
    }

    private fun showColorEditor(
        label: String,
        initial: Int,
        key: String
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AppUi.dp(this@AppearanceActivity, 20),
                AppUi.dp(this@AppearanceActivity, 8),
                AppUi.dp(this@AppearanceActivity, 20),
                0
            )
        }

        val previewSwatch = View(this).apply {
            background = swatchDrawable(initial)
        }

        content.addView(
            previewSwatch,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 54))
        )

        content.addView(AppUi.spacer(this, 12))

        val inputLayout = TextInputLayout(this).apply {
            hint = "HEX · например #4169E1"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val input = com.google.android.material.textfield.TextInputEditText(
            inputLayout.context
        ).apply {
            setSingleLine(true)
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(AppUi.ink)
            setText(hex(initial))
            textSize = 15f
        }

        inputLayout.addView(input)
        content.addView(inputLayout)

        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)

        val hue = colorSlider(content, "Оттенок", 360, hsv[0].toInt())
        val saturation = colorSlider(
            content,
            "Насыщенность",
            100,
            (hsv[1] * 100).toInt()
        )
        val brightness = colorSlider(
            content,
            "Яркость",
            100,
            (hsv[2] * 100).toInt()
        )

        var syncing = false

        fun showColor(value: Int, updateField: Boolean) {
            previewSwatch.background = swatchDrawable(value)

            if (updateField) {
                syncing = true
                input.setText(hex(value))
                syncing = false
            }

            Color.colorToHSV(value, hsv)

            syncing = true
            hue.progress = hsv[0].toInt().coerceIn(0, 360)
            saturation.progress =
                (hsv[1] * 100).toInt().coerceIn(0, 100)
            brightness.progress =
                (hsv[2] * 100).toInt().coerceIn(0, 100)
            syncing = false
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (syncing) return

                when (seekBar) {
                    hue -> hsv[0] = progress.toFloat()
                    saturation -> hsv[1] = progress / 100f
                    brightness -> hsv[2] = progress / 100f
                }

                showColor(Color.HSVToColor(hsv), true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

        hue.setOnSeekBarChangeListener(seekListener)
        saturation.setOnSeekBarChangeListener(seekListener)
        brightness.setOnSeekBarChangeListener(seekListener)

        input.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (syncing) return

                val parsed = parseColor(s?.toString().orEmpty()) ?: return

                previewSwatch.background = swatchDrawable(parsed)
                Color.colorToHSV(parsed, hsv)

                syncing = true
                hue.progress = hsv[0].toInt().coerceIn(0, 360)
                saturation.progress =
                    (hsv[1] * 100).toInt().coerceIn(0, 100)
                brightness.progress =
                    (hsv[2] * 100).toInt().coerceIn(0, 100)
                syncing = false
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Цвет: $label")
            .setView(content)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Применить", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val parsed = parseColor(
                    input.text?.toString().orEmpty()
                )

                if (parsed == null) {
                    inputLayout.error = "Введи цвет в формате #RRGGBB"
                } else {
                    applyColor(key, parsed)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun colorSlider(
        parent: LinearLayout,
        label: String,
        max: Int,
        progress: Int
    ): SeekBar {

        parent.addView(AppUi.spacer(this, 8))
        parent.addView(AppUi.text(this, label, 12f, AppUi.muted))

        val seek = SeekBar(this).apply {
            this.max = max
            this.progress = progress.coerceIn(0, max)
            progressTintList =
                android.content.res.ColorStateList.valueOf(AppUi.accent)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(AppUi.accent)
        }

        parent.addView(
            seek,
            LinearLayout.LayoutParams(-1, AppUi.dp(this, 34))
        )

        return seek
    }

    private fun applyColor(key: String, color: Int) {
        when (key) {
            "background" -> WidgetAppearance.setBackground(this, color)
            "lesson" -> WidgetAppearance.setLesson(this, color)
            "current" -> WidgetAppearance.setCurrent(this, color)
        }

        refreshPreview()

        colorRows[key]?.let { (swatch, label) ->
            swatch.background = swatchDrawable(color)
            label.text = hex(color)
        }

        ScheduleWidgetProvider.updateAll(this)
    }

    private fun colorFor(key: String): Int = when (key) {
        "background" -> WidgetAppearance.background(this)
        "lesson" -> WidgetAppearance.lesson(this)
        else -> WidgetAppearance.current(this)
    }

    private fun refreshPreview() {
        val bg = WidgetAppearance.background(this)
        val lesson = WidgetAppearance.lesson(this)
        val current = WidgetAppearance.current(this)

        preview.setCardBackgroundColor(bg)

        preview.radius = AppUi.dp(
            this,
            WidgetAppearance.radius(this).toInt()
        ).toFloat()

        previewGroup.text = "Группа: ${ScheduleStore.selectedGroup(this).name}"
        previewGroup.setTextColor(WidgetAppearance.contrastColor(bg))
        previewDate.setTextColor(WidgetAppearance.contrastColor(bg))

        previewLessons.forEach { item ->

            val cardColor = if (item.isCurrent) current else lesson

            item.card.setCardBackgroundColor(cardColor)

            item.title.setTextColor(
                WidgetAppearance.contrastColor(cardColor)
            )

            item.info.setTextColor(
                WidgetAppearance.mutedTextColor(cardColor)
            )

            // Цвет границы определяется типом занятия,
            // а не пользовательской цветовой настройкой.
            item.card.strokeColor = LessonTypeStyle.color(item.type)
        }
    }

    private fun swatchDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = AppUi.dp(this@AppearanceActivity, 12).toFloat()
        setStroke(
            AppUi.dp(this@AppearanceActivity, 1),
            AppUi.outline
        )
    }

    private fun hex(color: Int): String =
        String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    private fun parseColor(raw: String): Int? = runCatching {
        val value = raw.trim().let {
            if (it.startsWith("#")) it else "#$it"
        }

        if (!value.matches(Regex("#[0-9A-Fa-f]{6}"))) {
            null
        } else {
            Color.parseColor(value)
        }
    }.getOrNull()
}
