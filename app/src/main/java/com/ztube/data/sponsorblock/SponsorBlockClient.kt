package com.ztube.data.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class SkipSegment(val category: String, val start: Double, val end: Double)

@Singleton
class SponsorBlockClient @Inject constructor() {

    private val client = OkHttpClient()

    suspend fun segments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        val prefix = MessageDigest.getInstance("SHA-256")
            .digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(4)
        val url = "https://sponsor.ajay.app/api/skipSegments/$prefix".toHttpUrl().newBuilder()
            .addQueryParameter("categories", CATEGORIES)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val videos = JSONArray(response.body.string())
            val result = mutableListOf<SkipSegment>()
            for (i in 0 until videos.length()) {
                val video = videos.getJSONObject(i)
                if (video.getString("videoID") != videoId) continue
                val segments = video.getJSONArray("segments")
                for (j in 0 until segments.length()) {
                    val segment = segments.getJSONObject(j)
                    val range = segment.getJSONArray("segment")
                    result += SkipSegment(
                        segment.getString("category"),
                        range.getDouble(0),
                        range.getDouble(1),
                    )
                }
            }
            result
        }
    }

    private companion object {
        const val CATEGORIES =
            "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\"]"
    }
}
