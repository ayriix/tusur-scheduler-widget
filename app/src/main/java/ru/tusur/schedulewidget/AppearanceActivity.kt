package ru.tusur.schedulewidget

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider

private data class WidgetTone(val name: String, val color: Int)

class AppearanceActivity : AppCompatActivity() {
    private lateinit var preview: MaterialCardView
    private lateinit var previewGroup: TextView
    private lateinit var previewTitle: TextView
    private lateinit var previewRoom: TextView
    private lateinit var previewNormalTitle: TextView
    private lateinit var previewCurrentCard: MaterialCardView
    private lateinit var previewNormalCard: MaterialCardView
    private lateinit var radiusLabel: TextView

    private val backgrounds = listOf(
        WidgetTone("Бумага", 0xFFF0EDE5.toInt()),
        WidgetTone("Мел", 0xFFF6F5F1.toInt()),
        WidgetTone("Олива", 0xFFE7EADF.toInt()),
        WidgetTone("Сланец", 0xFF303431.toInt()),
        WidgetTone("Ночной", 0xFF1F2223.toInt())
    )
    private val lessonTones = listOf(
        WidgetTone("Молочный", 0xFFFAF8F2.toInt()),
        WidgetTone("Камень", 0xFFE5E0D7.toInt()),
        WidgetTone("Серо-зелёный", 0xFFDDE4DA.toInt()),
        WidgetTone("Графит", 0xFF444946.toInt())
    )
    private val currentTones = listOf(
        WidgetTone("Терракота", 0xFFE9D1C3.toInt()),
        WidgetTone("Янтарь", 0xFFE8D8A9.toInt()),
        WidgetTone("Шалфей", 0xFFC9D6C1.toInt()),
        WidgetTone("Голубой", 0xFFC9D9E8.toInt()),
        WidgetTone("Розовый", 0xFFE8CCD0.toInt())
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.background)
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "Внешний вид виджета"
            setTitleTextColor(AppUi.ink)
            setBackgroundColor(AppUi.background)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, AppUi.dp(this, 58)))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this@AppearanceActivity, 20), AppUi.dp(this@AppearanceActivity, 8), AppUi.dp(this@AppearanceActivity, 20), AppUi.dp(this@AppearanceActivity, 28))
        }
        content.addView(AppUi.text(this, "Подстрой виджет под свой экран", 25f, AppUi.ink, true))
        content.addView(AppUi.spacer(this, 5))
        content.addView(AppUi.text(this, "Изменения применяются сразу ко всем установленным виджетам.", 13f, AppUi.muted))
        content.addView(AppUi.spacer(this, 18))
        content.addView(AppUi.sectionLabel(this, "ПРЕДПРОСМОТР"))
        content.addView(AppUi.spacer(this, 8))

        val previewContent = AppUi.column(this, 14)
        previewGroup = AppUi.text(this, ScheduleStore.selectedGroup(this).name, 13f, WidgetAppearance.contrastColor(WidgetAppearance.background(this)), true)
        previewContent.addView(previewGroup)
        previewContent.addView(AppUi.spacer(this, 5))
        val currentLessonContent = AppUi.column(this, 9)
        previewTitle = AppUi.text(this, "СЕЙЧАС · Математика", 14f, WidgetAppearance.contrastColor(WidgetAppearance.current(this)), true)
        currentLessonContent.addView(previewTitle)
        previewRoom = AppUi.text(this, "10:40–12:15 · гк 428", 11f, WidgetAppearance.mutedTextColor(WidgetAppearance.current(this)))
        currentLessonContent.addView(previewRoom)
        previewCurrentCard = AppUi.card(this, currentLessonContent, 12).apply {
            setCardBackgroundColor(WidgetAppearance.current(this@AppearanceActivity))
            strokeWidth = 0
        }
        previewContent.addView(previewCurrentCard, LinearLayout.LayoutParams(-1, -2))
        previewContent.addView(AppUi.spacer(this, 5))
        previewNormalTitle = AppUi.text(this, "13:15–14:50   Информатика · ф 327", 11f, WidgetAppearance.contrastColor(WidgetAppearance.lesson(this)))
        val normalContent = AppUi.column(this, 8).apply { addView(previewNormalTitle) }
        previewNormalCard = AppUi.card(this, normalContent, 12).apply {
            setCardBackgroundColor(WidgetAppearance.lesson(this@AppearanceActivity))
            strokeWidth = 0
        }
        previewContent.addView(previewNormalCard, LinearLayout.LayoutParams(-1, -2))
        preview = AppUi.card(this, previewContent, 24)
        preview.setCardBackgroundColor(WidgetAppearance.background(this))
        preview.strokeWidth = 0
        content.addView(preview, LinearLayout.LayoutParams(-1, AppUi.dp(this, 154)))

        content.addView(AppUi.spacer(this, 22))
        content.addView(AppUi.sectionLabel(this, "ЗАКРУГЛЕНИЕ"))
        content.addView(AppUi.spacer(this, 8))
        val radiusCardContent = AppUi.column(this, 15)
        radiusLabel = AppUi.text(this, "${WidgetAppearance.radius(this).toInt()} dp", 14f, AppUi.ink, true)
        radiusCardContent.addView(radiusLabel)
        val slider = Slider(this).apply {
            valueFrom = 0f
            valueTo = 32f
            stepSize = 2f
            value = WidgetAppearance.radius(this@AppearanceActivity)
            contentDescription = "Радиус углов виджета"
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    WidgetAppearance.setRadius(this@AppearanceActivity, value)
                    radiusLabel.text = "${value.toInt()} dp"
                    refreshPreview()
                    ScheduleWidgetProvider.updateAll(this@AppearanceActivity)
                }
            }
        }
        radiusCardContent.addView(slider, LinearLayout.LayoutParams(-1, AppUi.dp(this, 42)))
        val radiusHints = AppUi.row(this)
        radiusHints.addView(AppUi.text(this, "Прямые углы", 11f, AppUi.muted), LinearLayout.LayoutParams(0, -2, 1f))
        radiusHints.addView(AppUi.text(this, "Более мягкие", 11f, AppUi.muted))
        radiusCardContent.addView(radiusHints)
        content.addView(AppUi.card(this, radiusCardContent))

        content.addView(AppUi.spacer(this, 22))
        addPaletteSection(content, "ФОН ВИДЖЕТА", backgrounds, WidgetAppearance.background(this)) { WidgetAppearance.setBackground(this, it) }
        content.addView(AppUi.spacer(this, 18))
        addPaletteSection(content, "ОБЫЧНЫЕ ПРЕДМЕТЫ", lessonTones, WidgetAppearance.lesson(this)) { WidgetAppearance.setLesson(this, it) }
        content.addView(AppUi.spacer(this, 18))
        addPaletteSection(content, "ТЕКУЩАЯ ПАРА", currentTones, WidgetAppearance.current(this)) { WidgetAppearance.setCurrent(this, it) }

        content.addView(AppUi.spacer(this, 22))
        val reset = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Сбросить оформление"
            isAllCaps = false
            setOnClickListener {
                WidgetAppearance.setRadius(this@AppearanceActivity, WidgetAppearance.DEFAULT_RADIUS)
                WidgetAppearance.setBackground(this@AppearanceActivity, WidgetAppearance.DEFAULT_BACKGROUND)
                WidgetAppearance.setLesson(this@AppearanceActivity, WidgetAppearance.DEFAULT_LESSON)
                WidgetAppearance.setCurrent(this@AppearanceActivity, WidgetAppearance.DEFAULT_CURRENT)
                render()
                ScheduleWidgetProvider.updateAll(this@AppearanceActivity)
            }
        }
        content.addView(reset, LinearLayout.LayoutParams(-1, AppUi.dp(this, 48)))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshPreview()
    }

    private fun addPaletteSection(
        parent: LinearLayout,
        title: String,
        tones: List<WidgetTone>,
        selected: Int,
        save: (Int) -> Unit
    ) {
        parent.addView(AppUi.sectionLabel(this, title))
        parent.addView(AppUi.spacer(this, 8))
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val choices = AppUi.row(this)
        tones.forEach { tone ->
            val choiceContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(AppUi.dp(this@AppearanceActivity, 8), AppUi.dp(this@AppearanceActivity, 8), AppUi.dp(this@AppearanceActivity, 8), AppUi.dp(this@AppearanceActivity, 8))
            }
            val swatch = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(tone.color)
                    cornerRadius = AppUi.dp(this@AppearanceActivity, 12).toFloat()
                    setStroke(AppUi.dp(this@AppearanceActivity, 1), 0x55777777)
                }
            }
            choiceContent.addView(swatch, LinearLayout.LayoutParams(AppUi.dp(this, 48), AppUi.dp(this, 34)))
            choiceContent.addView(AppUi.spacer(this, 6))
            choiceContent.addView(AppUi.text(this, tone.name, 10f, AppUi.muted, tone.color == selected))
            val card = MaterialCardView(this).apply {
                radius = AppUi.dp(this@AppearanceActivity, 15).toFloat()
                cardElevation = 0f
                strokeWidth = AppUi.dp(this@AppearanceActivity, if (tone.color == selected) 2 else 1)
                strokeColor = if (tone.color == selected) AppUi.accent else AppUi.outline
                setCardBackgroundColor(AppUi.surface)
                addView(choiceContent)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    save(tone.color)
                    ScheduleWidgetProvider.updateAll(this@AppearanceActivity)
                    render()
                }
            }
            choices.addView(card, LinearLayout.LayoutParams(AppUi.dp(this, 92), AppUi.dp(this, 83)).apply {
                marginEnd = AppUi.dp(this@AppearanceActivity, 8)
            })
        }
        horizontal.addView(choices)
        parent.addView(horizontal)
    }

    private fun refreshPreview() {
        val background = WidgetAppearance.background(this)
        val lesson = WidgetAppearance.lesson(this)
        val current = WidgetAppearance.current(this)
        preview.setCardBackgroundColor(background)
        preview.radius = AppUi.dp(this, WidgetAppearance.radius(this).toInt()).toFloat()
        previewGroup.setTextColor(WidgetAppearance.contrastColor(background))
        previewCurrentCard.setCardBackgroundColor(current)
        previewNormalCard.setCardBackgroundColor(lesson)
        previewTitle.setTextColor(WidgetAppearance.contrastColor(current))
        previewRoom.setTextColor(WidgetAppearance.mutedTextColor(current))
        previewNormalTitle.setTextColor(WidgetAppearance.contrastColor(lesson))
    }
}
