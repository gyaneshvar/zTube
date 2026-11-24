package com.ztube.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

class YoutubeUnauthorizedException : IOException("Unauthorized")
class YoutubeQuotaException : IOException("Quota exceeded")
class YoutubeApiException(val code: Int) : IOException("YouTube API error $code")

data class YoutubeChannel(val id: String, val title: String, val avatarUrl: String?)
data class YoutubeSubscription(val channelId: String, val title: String, val avatarUrl: String?)
data class YoutubeSubscriptions(val items: List<YoutubeSubscription>, val totalResults: Int)
data class YoutubePlaylist(val id: String, val title: String)
data class YoutubePlaylistItem(val videoId: String)
data class YoutubeVideo(
    val id: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
)

@Singleton
class YoutubeDataApi @Inject constructor() {

    private val client = OkHttpClient()

    suspend fun userEmail(token: String): String =
        get(token, "https://www.googleapis.com/oauth2/v3/userinfo".toHttpUrl()).getString("email")

    suspend fun channel(token: String): YoutubeChannel? {
        val url = api("channels").addQueryParameter("part", "id,snippet").addQueryParameter("mine", "true").build()
        val item = get(token, url).optJSONArray("items")?.optJSONObject(0) ?: return null
        val snippet = item.getJSONObject("snippet")
        return YoutubeChannel(item.getString("id"), snippet.optString("title"), thumbnail(snippet))
    }

    suspend fun subscriptions(token: String): YoutubeSubscriptions {
        val items = mutableListOf<YoutubeSubscription>()
        var total = 0
        pages(token, api("subscriptions").addQueryParameter("part", "snippet").addQueryParameter("mine", "true")) { page ->
            total = page.optJSONObject("pageInfo")?.optInt("totalResults") ?: total
            forEach(page) { item ->
                val snippet = item.getJSONObject("snippet")
                items += YoutubeSubscription(
                    snippet.getJSONObject("resourceId").getString("channelId"),
                    snippet.optString("title"),
                    thumbnail(snippet),
                )
            }
        }
        return YoutubeSubscriptions(items, total)
    }

    suspend fun playlists(token: String): List<YoutubePlaylist> {
        val result = mutableListOf<YoutubePlaylist>()
        pages(token, api("playlists").addQueryParameter("part", "snippet").addQueryParameter("mine", "true")) { page ->
            forEach(page) { item ->
                result += YoutubePlaylist(item.getString("id"), item.getJSONObject("snippet").optString("title"))
            }
        }
        return result
    }

    suspend fun playlistItems(token: String, playlistId: String): List<YoutubePlaylistItem>? {
        val result = mutableListOf<YoutubePlaylistItem>()
        try {
            pages(token, api("playlistItems").addQueryParameter("part", "contentDetails").addQueryParameter("playlistId", playlistId)) { page ->
                forEach(page) { item ->
                    val videoId = item.getJSONObject("contentDetails").optString("videoId")
                    if (videoId.isNotEmpty()) result += YoutubePlaylistItem(videoId)
                }
            }
        } catch (e: YoutubeApiException) {
            if (e.code == 404 || e.code == 403) return null
            throw e
        }
        return result
    }

    suspend fun videos(token: String, ids: List<String>): List<YoutubeVideo> {
        val result = mutableListOf<YoutubeVideo>()
        ids.chunked(BATCH).forEach { batch ->
            val url = api("videos")
                .addQueryParameter("part", "snippet,contentDetails")
                .addQueryParameter("id", batch.joinToString(","))
                .addQueryParameter("maxResults", BATCH.toString())
                .build()
            forEach(get(token, url)) { item ->
                val snippet = item.getJSONObject("snippet")
                result += YoutubeVideo(
                    item.getString("id"),
                    snippet.optString("title"),
                    snippet.optString("channelTitle"),
                    thumbnail(snippet),
                    seconds(item.getJSONObject("contentDetails").optString("duration")),
                )
            }
        }
        return result
    }

    private suspend fun pages(token: String, builder: HttpUrl.Builder, onPage: (JSONObject) -> Unit) {
        builder.addQueryParameter("maxResults", BATCH.toString())
        var pageToken: String? = null
        do {
            val url = builder.build().newBuilder()
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
            val page = get(token, url)
            onPage(page)
            pageToken = page.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
    }

    private suspend fun get(token: String, url: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            when {
                response.isSuccessful -> JSONObject(body)
                response.code == 401 -> throw YoutubeUnauthorizedException()
                response.code == 403 && isQuota(body) -> throw YoutubeQuotaException()
                else -> throw YoutubeApiException(response.code)
            }
        }
    }

    private fun isQuota(body: String): Boolean {
        val errors = try {
            JSONObject(body).getJSONObject("error").getJSONArray("errors")
        } catch (e: Exception) {
            return false
        }
        return (0 until errors.length()).any { errors.getJSONObject(it).optString("reason") == "quotaExceeded" }
    }

    private fun forEach(page: JSONObject, action: (JSONObject) -> Unit) {
        val items = page.optJSONArray("items") ?: return
        for (i in 0 until items.length()) action(items.getJSONObject(i))
    }

    private fun thumbnail(snippet: JSONObject): String? {
        val thumbnails = snippet.optJSONObject("thumbnails") ?: return null
        return listOf("high", "medium", "default")
            .firstNotNullOfOrNull { thumbnails.optJSONObject(it)?.optString("url")?.ifEmpty { null } }
    }

    private fun seconds(iso: String): Long =
        try {
            Duration.parse(iso).seconds
        } catch (e: Exception) {
            0L
        }

    private fun api(resource: String) = "https://www.googleapis.com/youtube/v3/$resource".toHttpUrl().newBuilder()

    private companion object {
        const val BATCH = 50
    }
}
