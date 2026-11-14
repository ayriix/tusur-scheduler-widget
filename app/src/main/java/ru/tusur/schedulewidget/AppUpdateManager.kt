package ru.tusur.schedulewidget

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * GitHub Releases updater. It only downloads a release APK and hands it to Android's
 * package installer; installation always requires the user's explicit confirmation.
 */
object AppUpdateManager {
    private const val API_URL =
        "https://api.github.com/repos/ayriix/tusur-scheduler-widget/releases/latest"
    private const val RELEASES_URL =
        "https://github.com/ayriix/tusur-scheduler-widget/releases"
    private const val PREFS_NAME = "app_update_preferences"
    private const val PREF_DISMISSED_TAG = "dismissed_release_tag"
    private const val PREF_PENDING_APK = "pending_installer_apk"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tusur-update-check").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    data class AvailableUpdate(
        val tagName: String,
        val releaseName: String,
        val releaseNotes: String,
        val apkUrl: String,
        val releasePageUrl: String,
        val apkSize: Long,
        val sha256: String?
    )

    fun checkLatest(
        context: Context,
        callback: (Result<AvailableUpdate?>) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { fetchLatest(appContext) }
            mainHandler.post { callback(result) }
        }
    }

    private fun fetchLatest(context: Context): AvailableUpdate? {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            setRequestProperty("User-Agent", "Raspisanie-TUSUR-Android")
            setRequestProperty("Cache-Control", "no-cache")
        }

        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw IOException("В GitHub пока нет опубликованного релиза с APK. Сначала опубликуй Release через тег версии и дождись завершения GitHub Actions.")
                }
                throw IOException("GitHub вернул HTTP $status")
            }

            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = JSONObject(response)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                return null
            }

            val tag = release.optString("tag_name").trim()
            if (tag.isBlank()) throw IOException("У релиза отсутствует tag_name")

            val assets = release.optJSONArray("assets")
                ?: throw IOException("В последнем GitHub Release пока нет APK")
            var apk: JSONObject? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true) &&
                    asset.optString("state", "uploaded") == "uploaded") {
                    apk = asset
                    // Prefer the single universal APK produced by this repository's workflow.
                    if (name.equals("app-debug.apk", ignoreCase = true) ||
                        name.equals("raspisanie-tusur.apk", ignoreCase = true)) break
                }
            }
            val asset = apk ?: throw IOException("В последнем GitHub Release не найден APK-файл")
            val downloadUrl = asset.optString("browser_download_url")
            if (!downloadUrl.startsWith("https://github.com/ayriix/tusur-scheduler-widget/releases/download/")) {
                throw IOException("Ссылка на APK ведёт не в Releases этого проекта")
            }

            val packageInfo = installedPackageInfo(context)
            val currentName = packageInfo.versionName ?: "0.0.0"
            val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val notes = release.optString("body", "")
            val publishedCode = Regex("(?im)^\\s*versionCode\\s*[:=]\\s*(\\d+)")
                .find(notes)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val isNewer = if (publishedCode != null) {
                publishedCode > currentCode
            } else {
                compareVersionTags(tag, currentName) > 0
            }
            if (!isNewer) return null

            val digest = asset.optString("digest", "")
                .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.lowercase()

            return AvailableUpdate(
                tagName = tag,
                releaseName = release.optString("name").ifBlank { "Расписание ТУСУР $tag" },
                releaseNotes = notes,
                apkUrl = downloadUrl,
                releasePageUrl = release.optString("html_url").ifBlank { RELEASES_URL },
                apkSize = asset.optLong("size", 0L),
                sha256 = digest
            )
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(
        context: Context,
        update: AvailableUpdate,
        callback: (Result<File>) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching {
                val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val safeTag = update.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = File(updatesDir, "tusur-schedule-$safeTag.apk")
                val temporary = File(updatesDir, "tusur-schedule-$safeTag.apk.part")
                temporary.delete()

                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Raspisanie-TUSUR-Android")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                try {
                    val status = connection.responseCode
                    if (status !in 200..299) throw IOException("Не удалось скачать APK: HTTP $status")
                    connection.inputStream.use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (!temporary.isFile || temporary.length() < 4L) {
                    temporary.delete()
                    throw IOException("Скачанный файл пустой или повреждён")
                }
                if (update.apkSize > 0L && temporary.length() != update.apkSize) {
                    temporary.delete()
                    throw IOException("Размер APK не совпадает с размером файла в GitHub Release")
                }
                temporary.inputStream().use { input ->
                    val signature = ByteArray(2)
                    if (input.read(signature) != 2 || signature[0] != 'P'.code.toByte() || signature[1] != 'K'.code.toByte()) {
                        temporary.delete()
                        throw IOException("Файл из GitHub не распознан как APK/ZIP")
                    }
                }
                update.sha256?.let { expected ->
                    val actual = sha256(temporary)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        temporary.delete()
                        throw IOException("Контрольная сумма APK не совпадает")
                    }
                }
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target
            }
            mainHandler.post { callback(result) }
        }
    }

    /**
     * Verifies package identity, versionCode and signing certificate before opening Android Installer.
     * This catches the common case where a locally installed Android Studio debug APK and a
     * GitHub Actions APK were signed with different debug.keystore files.
     */
    fun validateDownloadedApk(context: Context, file: File): String? {
        if (!file.isFile || file.length() <= 0L) return "Скачанный APK не найден или пустой."

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageManager = context.packageManager
        val downloaded = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "Android не смог прочитать скачанный APK. Файл повреждён или не является установщиком приложения."

        if (downloaded.packageName != context.packageName) {
            return "Этот APK относится к другому приложению (${downloaded.packageName}). Установка отменена."
        }

        @Suppress("DEPRECATION")
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val installedCode = packageVersionCode(installed)
        val downloadedCode = packageVersionCode(downloaded)
        if (downloadedCode <= installedCode) {
            return "В APK версия не новее установленной (versionCode $downloadedCode ≤ $installedCode). Установка отменена."
        }

        val installedSigners = signingCertificates(installed)
        val downloadedSigners = signingCertificates(downloaded)
        if (installedSigners.isEmpty() || downloadedSigners.isEmpty() || installedSigners != downloadedSigners) {
            return "Подпись APK отличается от установленной версии. Android не сможет обновить приложение поверх неё. " +
                "Настрой GitHub Actions на тот же debug.keystore, который использовался для текущей установки."
        }
        return null
    }

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signingCertificates(info: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            signingInfo.apkContentsSigners ?: return emptySet()
        } else {
            @Suppress("DEPRECATION")
            (info.signatures ?: return emptySet())
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.toSet()
    }

    fun rememberPendingInstall(context: Context, file: File) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_PENDING_APK, file.absolutePath).apply()
    }

    fun takePendingInstall(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(PREF_PENDING_APK, null) ?: return null
        prefs.edit().remove(PREF_PENDING_APK).apply()
        return File(path).takeIf { it.isFile && it.length() > 0L }
    }

    fun markDismissed(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_DISMISSED_TAG, tag).apply()
    }

    fun wasDismissed(context: Context, tag: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_DISMISSED_TAG, null) == tag

    fun openReleasePage(context: Context, releaseUrl: String = RELEASES_URL) {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(releaseUrl))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun installedPackageInfo(context: Context): PackageInfo {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun compareVersionTags(remoteTag: String, currentVersion: String): Int {
        fun parts(value: String): List<Int> {
            val match = Regex("(?i)^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(value.trim())
                ?: return listOf(0, 0, 0)
            return listOf(1, 2, 3).map { index ->
                match.groupValues.getOrNull(index)?.toIntOrNull() ?: 0
            }
        }
        val remote = parts(remoteTag)
        val current = parts(currentVersion)
        for (index in 0..2) {
            val comparison = remote[index].compareTo(current[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
