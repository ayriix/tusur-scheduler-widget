package ru.tusur.schedulewidget

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.Executors

class GroupSelectionActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var adapter: GroupAdapter
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private var allGroups: List<GroupOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        showGroups(GroupDirectory.cached(this))
        loadDirectory()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.background)
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "Выбор группы"
            setTitleTextColor(AppUi.ink)
            setBackgroundColor(AppUi.background)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, AppUi.dp(this, 58)))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this, 20), AppUi.dp(this, 8), AppUi.dp(this, 20), AppUi.dp(this, 8))
        }
        top.addView(AppUi.text(this, "Найди свою группу", 24f, AppUi.ink, true))
        top.addView(AppUi.spacer(this, 5))
        top.addView(AppUi.text(this, "Список загружается с официального сайта расписания ТУСУРа. Ищи по номеру группы или названию факультета.", 13f, AppUi.muted))
        top.addView(AppUi.spacer(this, 12))

        val searchBox = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Например, 576-1, 576-М или факультет"
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        search = com.google.android.material.textfield.TextInputEditText(searchBox.context).apply {
            textSize = 15f
            setSingleLine(true)
        }
        searchBox.addView(search)
        top.addView(searchBox, LinearLayout.LayoutParams(-1, -2))
        root.addView(top)

        status = AppUi.text(this, "Загружаю список групп…", 12f, AppUi.muted).apply {
            setPadding(AppUi.dp(this@GroupSelectionActivity, 20), AppUi.dp(this@GroupSelectionActivity, 5), AppUi.dp(this@GroupSelectionActivity, 20), AppUi.dp(this@GroupSelectionActivity, 5))
        }
        root.addView(status)
        progress = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(AppUi.dp(this, 32), AppUi.dp(this, 32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = AppUi.dp(this@GroupSelectionActivity, 6)
            bottomMargin = AppUi.dp(this@GroupSelectionActivity, 6)
        })

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@GroupSelectionActivity)
            clipToPadding = false
            setPadding(AppUi.dp(this@GroupSelectionActivity, 16), AppUi.dp(this@GroupSelectionActivity, 4), AppUi.dp(this@GroupSelectionActivity, 16), AppUi.dp(this@GroupSelectionActivity, 20))
        }
        adapter = GroupAdapter { selected -> chooseGroup(selected) }
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        setContentView(root)
    }

    private fun loadDirectory() {
        progress.visibility = android.view.View.VISIBLE
        executor.execute {
            try {
                val groups = GroupDirectory.fetch(this)
                runOnUiThread {
                    progress.visibility = android.view.View.GONE
                    showGroups(groups)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.visibility = android.view.View.GONE
                    status.text = if (allGroups.size > 1) {
                        "Оффлайн: показываю сохранённый список (${allGroups.size})"
                    } else {
                        "Не удалось загрузить каталог. Проверь интернет и попробуй ещё раз."
                    }
                    Toast.makeText(this, "Каталог ТУСУРа недоступен: ${error.message ?: "ошибка сети"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showGroups(groups: List<GroupOption>) {
        allGroups = groups.distinctBy { it.path }.sortedWith(compareBy({ it.faculty }, { it.name }))
        val query = search.text?.toString().orEmpty()
        filter(query)
        if (query.isBlank()) status.text = "Найдено групп: ${allGroups.size}"
        progress.visibility = android.view.View.GONE
    }

    private fun filter(query: String) {
        if (!::adapter.isInitialized) return
        val needle = query.trim().lowercase()
        val filtered = if (needle.isBlank()) allGroups else allGroups.filter { group ->
            val name = group.name.lowercase()
            val faculty = group.faculty.lowercase()
            name.contains(needle) || faculty.contains(needle)
        }
        adapter.submit(filtered)
        if (allGroups.isNotEmpty()) status.text = "Показано: ${filtered.size} из ${allGroups.size}"
    }

    private fun chooseGroup(group: GroupOption) {
        ScheduleStore.setSelectedGroup(this, group)
        ScheduleStore.clearSchedule(this)
        ScheduleStore.resetDayOffsets(this)
        ScheduleWidgetProvider.updateAll(this)
        ScheduleWidgetProvider.enqueueRefresh(this, true)
        Toast.makeText(this, "Выбрана группа ${group.name}", Toast.LENGTH_SHORT).show()
        finish()
    }
}

private class GroupAdapter(private val onClick: (GroupOption) -> Unit) : RecyclerView.Adapter<GroupAdapter.Holder>() {
    private val items = mutableListOf<GroupOption>()

    fun submit(groups: List<GroupOption>) {
        items.clear()
        items.addAll(groups)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(context, 14), AppUi.dp(context, 13), AppUi.dp(context, 14), AppUi.dp(context, 13))
        }
        val title = AppUi.text(context, "576-1", 16f, AppUi.ink, true)
        val subtitle = AppUi.text(context, "Факультет вычислительных систем", 12f, AppUi.muted)
        textColumn.addView(title)
        textColumn.addView(AppUi.spacer(context, 4))
        textColumn.addView(subtitle)
        val card = AppUi.card(context, textColumn, 16).apply {
            isClickable = true
            isFocusable = true
            rippleColor = android.content.res.ColorStateList.valueOf(0x227D5546)
        }
        return Holder(card, title, subtitle)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val group = items[position]
        holder.title.text = group.name
        holder.subtitle.text = group.faculty
        holder.itemView.setOnClickListener { onClick(group) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: MaterialCardView, val title: TextView, val subtitle: TextView) : RecyclerView.ViewHolder(itemView)
}
