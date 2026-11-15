package ru.tusur.schedulewidget

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object GroupDirectory {
    private const val TAG = "GroupDirectory"
    private const val PREFS = "group_directory"
    private const val KEY_GROUPS = "groups_json"
    private const val KEY_GROUPS_UPDATED_AT = "groups_updated_at"
    private const val KEY_LAST_FETCH_ATTEMPT_AT = "last_fetch_attempt_at"
    private const val BASE = "https://timetable.tusur.ru"
    private const val HOST = "timetable.tusur.ru"

    // A group directory changes much less often than a timetable. Keep a successful
    // catalogue locally for 30 days instead of downloading every faculty page again.
    private const val CATALOG_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    private const val FAILED_FETCH_COOLDOWN_MS = 10L * 60L * 1000L

    // The catalogue is a collection of HTML pages. Limit connection time and use a
    // small parallel pool so a weak mobile connection is not flooded with requests.
    private const val HOME_TIMEOUT_MS = 25_000
    private const val FACULTY_TIMEOUT_MS = 20_000
    private const val TOTAL_FACULTY_TIMEOUT_SECONDS = 45L
    private const val FACULTY_THREADS = 3

    private data class FacultyResult(
        val groups: List<GroupOption>,
        val succeeded: Boolean
    )

    fun cached(context: Context): List<GroupOption> {
        val raw = prefs(context).getString(KEY_GROUPS, null) ?: return listOf(defaultGroup())
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val path = item.optString("path")
                if (!path.startsWith("/faculties/") || !path.contains("/groups/")) null else GroupOption(
                    item.optString("name"), item.optString("faculty"), path
                )
            }.distinctBy { it.path }.ifEmpty { listOf(defaultGroup()) }
        } catch (_: Exception) {
            listOf(defaultGroup())
        }
    }

    /**
     * Called only after the current local search has no match. A fresh cached list
     * avoids a network request; users can still tap the status message to force refresh.
     */
    fun hasCachedDirectory(context: Context): Boolean {
        val raw = prefs(context).getString(KEY_GROUPS, null)
        return !raw.isNullOrBlank() && cached(context).size > 1
    }

    fun shouldFetch(context: Context): Boolean {
        val preferences = prefs(context)
        val raw = preferences.getString(KEY_GROUPS, null)
        val now = System.currentTimeMillis()

        if (!raw.isNullOrBlank() && cached(context).size > 1) {
            var updatedAt = preferences.getLong(KEY_GROUPS_UPDATED_AT, 0L)
            if (!preferences.contains(KEY_GROUPS_UPDATED_AT)) {
                // Migration: complete catalogues saved by versions <= 2.5.12 had no timestamp.
                // A partial catalogue created by this version explicitly stores -1 and is
                // therefore not mistaken for a complete 30-day cache.
                preferences.edit().putLong(KEY_GROUPS_UPDATED_AT, now).apply()
                updatedAt = now
            }
            if (updatedAt > 0L && now - updatedAt < CATALOG_TTL_MS) return false
        }

        val lastAttempt = preferences.getLong(KEY_LAST_FETCH_ATTEMPT_AT, 0L)
        return lastAttempt <= 0L || now - lastAttempt >= FAILED_FETCH_COOLDOWN_MS
    }

    fun fetch(context: Context): List<GroupOption> {
        val appContext = context.applicationContext
        val preferences = prefs(appContext)
        preferences.edit().putLong(KEY_LAST_FETCH_ATTEMPT_AT, System.currentTimeMillis()).apply()

        val home = connect(appContext, BASE, HOME_TIMEOUT_MS).get()
        val facultyLinks = home.select("a[href*=\"/faculties/\"]")
            .mapNotNull { link ->
                val path = runCatching { java.net.URI(link.absUrl("href")).path }.getOrNull().orEmpty()
                val title = link.text().trim()
                if (path.startsWith("/faculties/") && title.isNotBlank()) path to title else null
            }
            .distinctBy { it.first }

        if (facultyLinks.isEmpty()) {
            throw IOException("На сайте расписания не найден список факультетов")
        }

        val pool = Executors.newFixedThreadPool(FACULTY_THREADS)
        val completion = ExecutorCompletionService<FacultyResult>(pool)
        val tasks = facultyLinks.map { (facultyPath, facultyTitle) ->
            completion.submit(Callable {
                val groups = mutableListOf<GroupOption>()
                try {
                    val page = connect(appContext, BASE + facultyPath, FACULTY_TIMEOUT_MS).get()
                    page.select("a[href*=\"/groups/\"]").forEach { link ->
                        val path = runCatching { java.net.URI(link.absUrl("href")).path }.getOrNull().orEmpty()
                        val name = link.text().trim()
                        if (path.startsWith("/faculties/") && path.contains("/groups/") && name.isNotBlank()) {
                            groups += GroupOption(name = name, faculty = facultyTitle, path = path)
                        }
                    }
                    FacultyResult(groups, succeeded = true)
                } catch (error: Exception) {
                    Log.w(TAG, "Не удалось загрузить факультет $facultyPath", error)
                    FacultyResult(groups, succeeded = false)
                }
            })
        }

        val collected = mutableListOf<GroupOption>()
        var completedCount = 0
        var successfulCount = 0
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TOTAL_FACULTY_TIMEOUT_SECONDS)
            while (completedCount < tasks.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completedCount++
                try {
                    val result = future.get()
                    if (result.succeeded) successfulCount++
                    collected.addAll(result.groups)
                } catch (error: Exception) {
                    Log.w(TAG, "Ошибка при обработке страницы факультета", error)
                }
            }
        } finally {
            tasks.forEach { if (!it.isDone) it.cancel(true) }
            pool.shutdownNow()
        }

        val groups = collected.distinctBy { it.path }
            .sortedWith(compareBy<GroupOption>({ it.faculty }, { it.name }))
        val completeCatalogue = completedCount == facultyLinks.size && successfulCount == facultyLinks.size

        if (completeCatalogue && groups.isNotEmpty()) {
            save(appContext, groups)
            return groups
        }

        // A partial fetch is not a successful catalogue refresh: retain the previous
        // expiry timestamp so the app keeps trying until it gets a complete directory.
        val existing = cached(appContext)
        if (groups.isNotEmpty()) {
            // Save successfully fetched faculty pages even when another page timed out.
            // Repeated attempts on a restricted mobile connection can then accumulate
            // the catalogue instead of discarding every partial result.
            val merged = (existing + groups).distinctBy { it.path }
                .sortedWith(compareBy<GroupOption>({ it.faculty }, { it.name }))
            savePartial(appContext, merged)
            return merged
        }
        if (existing.size > 1) return existing

        throw IOException("Не удалось загрузить каталог групп. Проверьте интернет и повторите попытку.")
    }

    private fun connect(context: Context, url: String, timeoutMs: Int): Connection {
        val request = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(timeoutMs)
        // Android 7+ reads the host-scoped trust anchors from Network Security Config.
        // Android 6 has no Network Security Config, so use the narrowly scoped fallback.
        if (TusurTls.needsLegacyFallback()) {
            request.sslSocketFactory(TusurTls.legacySocketFactory(context, HOST))
        }
        return request
    }

    private fun savePartial(context: Context, groups: List<GroupOption>) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("name", group.name)
                put("faculty", group.faculty)
                put("path", group.path)
            })
        }
        val preferences = prefs(context)
        val editor = preferences.edit().putString(KEY_GROUPS, array.toString())
        if (!preferences.contains(KEY_GROUPS_UPDATED_AT)) {
            editor.putLong(KEY_GROUPS_UPDATED_AT, -1L)
        }
        // Do not change KEY_GROUPS_UPDATED_AT for an already timestamped complete
        // catalogue. Its original expiry remains authoritative.
        editor.apply()
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
        prefs(context).edit()
            .putString(KEY_GROUPS, array.toString())
            .putLong(KEY_GROUPS_UPDATED_AT, System.currentTimeMillis())
            .remove(KEY_LAST_FETCH_ATTEMPT_AT)
            .apply()
    }

    fun defaultGroup() = GroupOption(
        ScheduleStore.DEFAULT_GROUP_NAME,
        ScheduleStore.DEFAULT_GROUP_FACULTY,
        ScheduleStore.DEFAULT_GROUP_PATH
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122.0 Mobile Safari/537.36"
}
