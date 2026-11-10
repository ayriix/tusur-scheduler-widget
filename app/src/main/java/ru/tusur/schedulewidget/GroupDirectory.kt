package ru.tusur.schedulewidget

import android.content.Context
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

object GroupDirectory {
    private const val PREFS = "group_directory"
    private const val KEY_GROUPS = "groups_json"
    private const val BASE = "https://timetable.tusur.ru"

    fun cached(context: Context): List<GroupOption> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_GROUPS, null)
            ?: return listOf(defaultGroup())
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val path = item.optString("path")
                if (!path.startsWith("/")) null else GroupOption(
                    item.optString("name"), item.optString("faculty"), path
                )
            }.distinctBy { it.path }.ifEmpty { listOf(defaultGroup()) }
        } catch (_: Exception) {
            listOf(defaultGroup())
        }
    }

    fun fetch(context: Context): List<GroupOption> {
        val home = Jsoup.connect(BASE).userAgent(USER_AGENT).timeout(20_000).get()
        val facultyLinks = home.select("a[href*=\"/faculties/\"]")
            .mapNotNull { link ->
                val path = runCatching { java.net.URI(link.absUrl("href")).path }.getOrNull().orEmpty()
                val title = link.text().trim()
                if (path.startsWith("/faculties/") && title.isNotBlank()) path to title else null
            }
            .distinctBy { it.first }

        val result = java.util.Collections.synchronizedList(mutableListOf<GroupOption>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val tasks = facultyLinks.map { (facultyPath, facultyTitle) ->
                pool.submit(java.util.concurrent.Callable {
                    val facultyGroups = mutableListOf<GroupOption>()
                    try {
                        val page = Jsoup.connect(BASE + facultyPath).userAgent(USER_AGENT).timeout(12_000).get()
                        page.select("a[href*=\"/groups/\"]").forEach { link ->
                            val path = runCatching { java.net.URI(link.absUrl("href")).path }.getOrNull().orEmpty()
                            val name = link.text().trim()
                            if (path.startsWith("/faculties/") && path.contains("/groups/") && name.isNotBlank()) {
                                facultyGroups += GroupOption(name = name, faculty = facultyTitle, path = path)
                            }
                        }
                    } catch (_: Exception) {
                        // Keep groups discovered on the other faculty pages.
                    }
                    facultyGroups
                })
            }
            tasks.forEach { task ->
                try {
                    result.addAll(task.get(18, java.util.concurrent.TimeUnit.SECONDS))
                } catch (_: Exception) {
                    task.cancel(true)
                }
            }
        } finally {
            pool.shutdownNow()
        }

        val groups = result.distinctBy { it.path }.sortedWith(compareBy<GroupOption>({ it.faculty }, { it.name }))
        if (groups.isNotEmpty()) save(context, groups)
        return groups.ifEmpty { cached(context) }
    }

    private fun save(context: Context, groups: List<GroupOption>) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("name", group.name)
                put("faculty", group.faculty)
                put("path", group.path)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_GROUPS, array.toString()).apply()
    }

    fun defaultGroup() = GroupOption(
        ScheduleStore.DEFAULT_GROUP_NAME,
        ScheduleStore.DEFAULT_GROUP_FACULTY,
        ScheduleStore.DEFAULT_GROUP_PATH
    )

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122.0 Mobile Safari/537.36"
}
