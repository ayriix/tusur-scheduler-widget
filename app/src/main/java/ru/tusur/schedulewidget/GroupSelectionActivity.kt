package ru.tusur.schedulewidget

import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors

class GroupSelectionActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var adapter: GroupAdapter
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private var allGroups: List<GroupOption> = emptyList()
    private var directoryFetchStarted = false
    private var directoryFetchFinished = false
    private var directoryLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        allGroups = GroupDirectory.cached(this).distinctBy { it.path }
        filter("")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.background)
        }
        AppUi.installSystemBars(this, root)

        val toolbar = MaterialToolbar(this).apply {
            title = "Выбор группы"
            setTitleTextColor(AppUi.ink)
            setBackgroundColor(AppUi.background)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationIconTint(AppUi.ink)
            setNavigationOnClickListener { finish() }
            elevation = 0f
            titleCentered = false
            contentInsetStartWithNavigation = AppUi.dp(this@GroupSelectionActivity, 8)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, AppUi.dp(this, 58)))

        val searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this@GroupSelectionActivity, 20), AppUi.dp(this@GroupSelectionActivity, 8), AppUi.dp(this@GroupSelectionActivity, 20), AppUi.dp(this@GroupSelectionActivity, 4))
        }
        val searchBox = TextInputLayout(this).apply {
            hint = "Номер группы или факультет"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxStrokeColor(AppUi.outline)
            setHintTextColor(android.content.res.ColorStateList.valueOf(AppUi.muted))
        }
        search = com.google.android.material.textfield.TextInputEditText(searchBox.context).apply {
            textSize = 15f
            setSingleLine(true)
            setTextColor(AppUi.ink)
            setHintTextColor(AppUi.muted)
        }
        searchBox.addView(search)
        searchContainer.addView(searchBox, LinearLayout.LayoutParams(-1, -2))
        root.addView(searchContainer)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(AppUi.dp(this@GroupSelectionActivity, 20), AppUi.dp(this@GroupSelectionActivity, 7), AppUi.dp(this@GroupSelectionActivity, 20), AppUi.dp(this@GroupSelectionActivity, 7))
        }
        status = AppUi.text(this, "Начните вводить номер группы или название факультета — здесь появятся совпадения.", 12f, AppUi.muted)
        statusRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        statusRow.addView(progress, LinearLayout.LayoutParams(AppUi.dp(this, 20), AppUi.dp(this, 20)))
        root.addView(statusRow)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@GroupSelectionActivity)
            clipToPadding = false
            setBackgroundColor(AppUi.background)
            setPadding(AppUi.dp(this@GroupSelectionActivity, 16), AppUi.dp(this@GroupSelectionActivity, 5), AppUi.dp(this@GroupSelectionActivity, 16), AppUi.dp(this@GroupSelectionActivity, 20))
        }
        adapter = GroupAdapter { selected -> chooseGroup(selected) }
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        setContentView(root)
    }

    private fun filter(query: String) {
        if (!::adapter.isInitialized) return
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            adapter.submit(emptyList())
            progress.visibility = View.GONE
            status.text = "Начните вводить номер группы или название факультета — здесь появятся совпадения."
            return
        }

        val filtered = allGroups.filter { group ->
            group.name.lowercase().contains(needle) || group.faculty.lowercase().contains(needle)
        }
        adapter.submit(filtered)
        when {
            filtered.isNotEmpty() -> {
                status.text = "Найдено групп: ${filtered.size}"
                progress.visibility = View.GONE
            }
            directoryLoading -> {
                status.text = "Ищем группы в каталоге ТУСУРа…"
                progress.visibility = View.VISIBLE
            }
            !directoryFetchStarted && needle.length >= 2 -> loadDirectory()
            directoryFetchFinished -> {
                status.text = "Совпадений не найдено. Проверьте номер или попробуйте название факультета."
                progress.visibility = View.GONE
            }
            else -> {
                status.text = "Совпадений пока нет"
                progress.visibility = View.GONE
            }
        }
    }

    private fun loadDirectory() {
        if (directoryFetchStarted) return
        directoryFetchStarted = true
        directoryLoading = true
        progress.visibility = View.VISIBLE
        status.text = "Ищем группы в каталоге ТУСУРа…"
        executor.execute {
            val result = runCatching { GroupDirectory.fetch(applicationContext) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                directoryLoading = false
                directoryFetchFinished = true
                progress.visibility = View.GONE
                result.onSuccess { groups -> allGroups = groups.distinctBy { it.path } }
                val currentQuery = search.text?.toString().orEmpty()
                val needle = currentQuery.trim().lowercase()
                if (needle.isBlank()) {
                    adapter.submit(emptyList())
                    status.text = "Начните вводить номер группы или название факультета — здесь появятся совпадения."
                    return@runOnUiThread
                }
                val filtered = allGroups.filter {
                    it.name.lowercase().contains(needle) || it.faculty.lowercase().contains(needle)
                }
                adapter.submit(filtered)
                status.text = when {
                    result.isFailure && allGroups.size <= 1 -> "Каталог недоступен. Проверьте подключение и повторите поиск."
                    filtered.isNotEmpty() -> "Найдено групп: ${filtered.size}"
                    else -> "Совпадений не найдено. Проверьте номер или название факультета."
                }
            }
        }
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
            setPadding(AppUi.dp(context, 16), AppUi.dp(context, 15), AppUi.dp(context, 16), AppUi.dp(context, 15))
        }
        val title = AppUi.text(context, "576-1", 16f, AppUi.ink, true)
        val subtitle = AppUi.text(context, "Факультет", 12f, AppUi.muted)
        textColumn.addView(title)
        textColumn.addView(AppUi.spacer(context, 4))
        textColumn.addView(subtitle)
        val card = AppUi.card(context, textColumn, 16).apply {
            isClickable = true
            isFocusable = true
            rippleColor = android.content.res.ColorStateList.valueOf(0x337FA4FF)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = AppUi.dp(context, 8)
            }
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
