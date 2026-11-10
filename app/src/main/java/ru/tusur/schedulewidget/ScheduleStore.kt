package ru.tusur.schedulewidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object ScheduleStore {
    private const val CACHE_PREFS = "schedule_cache"
    private const val SETTINGS_PREFS = "app_settings"
    private const val KEY_ITEMS = "items"
    private const val KEY_UPDATED = "updated"
    private const val KEY_ERROR = "error"
    private const val KEY_GROUP_NAME = "group_name"
    private const val KEY_GROUP_FACULTY = "group_faculty"
    private const val KEY_GROUP_PATH = "group_path"
    private const val KEY_DATE_PREFIX = "widget_date_"

    const val DEFAULT_GROUP_NAME = "576-1"
    const val DEFAULT_GROUP_FACULTY = "Факультет вычислительных систем"
    const val DEFAULT_GROUP_PATH = "/faculties/fvs/groups/576-1"

    fun save(context: Context, lessons: List<Lesson>) {
        val array = JSONArray()
        lessons.forEach { lesson ->
            array.put(JSONObject().apply {
                put("start", lesson.start)
                put("end", lesson.end)
                put("title", lesson.title)
                put("room", lesson.room)
                put("teacher", lesson.teacher)
                put("note", lesson.note)
            })
        }
        cache(context).edit()
            .putString(KEY_ITEMS, array.toString())
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .remove(KEY_ERROR)
            .apply()
    }

    fun load(context: Context): List<Lesson> {
        val raw = cache(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Lesson(
                    start = item.optLong("start"),
                    end = item.optLong("end"),
                    title = item.optString("title"),
                    room = item.optString("room"),
                    teacher = item.optString("teacher"),
                    note = item.optString("note")
                )
            }.sortedBy { it.start }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSchedule(context: Context) {
        cache(context).edit().remove(KEY_ITEMS).remove(KEY_UPDATED).remove(KEY_ERROR).apply()
    }

    fun updatedAt(context: Context): Long = cache(context).getLong(KEY_UPDATED, 0L)
    fun setError(context: Context, message: String) = cache(context).edit().putString(KEY_ERROR, message).apply()
    fun error(context: Context): String? = cache(context).getString(KEY_ERROR, null)?.takeIf { it.isNotBlank() }

    fun selectedGroup(context: Context): GroupOption = GroupOption(
        name = settings(context).getString(KEY_GROUP_NAME, DEFAULT_GROUP_NAME) ?: DEFAULT_GROUP_NAME,
        faculty = settings(context).getString(KEY_GROUP_FACULTY, DEFAULT_GROUP_FACULTY) ?: DEFAULT_GROUP_FACULTY,
        path = settings(context).getString(KEY_GROUP_PATH, DEFAULT_GROUP_PATH) ?: DEFAULT_GROUP_PATH
    )

    fun setSelectedGroup(context: Context, group: GroupOption) {
        settings(context).edit()
            .putString(KEY_GROUP_NAME, group.name)
            .putString(KEY_GROUP_FACULTY, group.faculty)
            .putString(KEY_GROUP_PATH, group.path)
            .apply()
    }

    fun selectedDate(context: Context, widgetId: Int): LocalDate {
        val storedDay = settings(context).getLong(KEY_DATE_PREFIX + widgetId, Long.MIN_VALUE)
        return if (storedDay == Long.MIN_VALUE) LocalDate.now(java.time.ZoneId.of("Asia/Tomsk"))
        else runCatching { LocalDate.ofEpochDay(storedDay) }
            .getOrElse { LocalDate.now(java.time.ZoneId.of("Asia/Tomsk")) }
    }

    fun moveSelectedDate(context: Context, widgetId: Int, days: Int) {
        val next = selectedDate(context, widgetId).plusDays(days.toLong())
            .coerceIn(LocalDate.now(java.time.ZoneId.of("Asia/Tomsk")).minusDays(30), LocalDate.now(java.time.ZoneId.of("Asia/Tomsk")).plusDays(180))
        settings(context).edit().putLong(KEY_DATE_PREFIX + widgetId, next.toEpochDay()).apply()
    }

    fun resetDayOffsets(context: Context) {
        val editor = settings(context).edit()
        settings(context).all.keys.filter { it.startsWith(KEY_DATE_PREFIX) }.forEach(editor::remove)
        editor.apply()
    }

    private fun cache(context: Context) = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    private fun settings(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
