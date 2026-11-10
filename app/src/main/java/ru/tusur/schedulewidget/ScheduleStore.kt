package ru.tusur.schedulewidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ScheduleStore {
    private const val PREFS = "schedule_cache"
    private const val KEY_ITEMS = "items"
    private const val KEY_UPDATED = "updated"
    private const val KEY_ERROR = "error"

    fun save(context: Context, lessons: List<Lesson>) {
        val array = JSONArray()
        lessons.forEach {
            array.put(JSONObject().apply {
                put("start", it.start); put("end", it.end); put("title", it.title)
                put("room", it.room); put("teacher", it.teacher); put("note", it.note)
            })
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString())
            .putLong(KEY_UPDATED, System.currentTimeMillis()).remove(KEY_ERROR).apply()
    }

    fun load(context: Context): List<Lesson> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Lesson(o.getLong("start"), o.getLong("end"), o.optString("title"),
                    o.optString("room"), o.optString("teacher"), o.optString("note"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun updatedAt(context: Context) = prefs(context).getLong(KEY_UPDATED, 0L)
    fun setError(context: Context, value: String) = prefs(context).edit().putString(KEY_ERROR, value).apply()
    fun error(context: Context) = prefs(context).getString(KEY_ERROR, null)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
