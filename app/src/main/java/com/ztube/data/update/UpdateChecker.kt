package com.ztube.data.update

import android.content.Context
import com.ztube.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class Release(val version: String, val notes: String, val downloadUrl: String)

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val client = OkHttpClient()

    val currentVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "zTube")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parse(JSONObject(response.body.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun latestIfDue(): Release? {
        val now = System.currentTimeMillis()
        if (now - settings.updateLastCheckAt() < CHECK_INTERVAL_MS) return null
        val release = latest() ?: return null
        settings.setUpdateLastCheckAt(now)
        if (!isNewer(release)) return null
        if (settings.updateSnoozedVersion() == release.version && now < settings.updateSnoozedUntil()) return null
        return release
    }

    suspend fun snooze(release: Release) {
        settings.setUpdateSnoozed(release.version, System.currentTimeMillis() + SNOOZE_MS)
    }

    fun isNewer(release: Release): Boolean {
        val latest = parts(release.version) ?: return false
        val current = parts(currentVersion) ?: return false
        for (i in 0 until maxOf(latest.size, current.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int>? {
        val numbers = version.removePrefix("v").split('.').map { it.toIntOrNull() }
        return if (numbers.any { it == null }) null else numbers.filterNotNull()
    }

    private fun parse(json: JSONObject): Release? {
        val tag = json.optString("tag_name").removePrefix("v")
        if (tag.isEmpty()) return null
        val htmlUrl = json.optString("html_url")
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url").ifEmpty { null }
                    break
                }
            }
        }
        return Release(tag, notes(json.optString("body")), apkUrl ?: htmlUrl)
    }

    private fun notes(body: String): String =
        body.lines()
            .takeWhile { !it.startsWith("## Install") }
            .joinToString("\n") { it.trimStart('#').trimStart() }
            .replace("**", "")
            .trim()

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/gyaneshvar/zTube/releases/latest"
        val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(12)
        val SNOOZE_MS = TimeUnit.DAYS.toMillis(3)
    }
}
