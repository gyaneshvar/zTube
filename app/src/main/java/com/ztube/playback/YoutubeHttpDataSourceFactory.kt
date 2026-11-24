package com.ztube.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicLong

// Mirrors NewPipe's YoutubeHttpDataSource: googlevideo throttles plain GET requests.
@OptIn(UnstableApi::class)
class YoutubeHttpDataSourceFactory : DataSource.Factory {

    private val requestNumber = AtomicLong()

    private val delegate = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true),
    ) { dataSpec -> resolve(dataSpec) }

    override fun createDataSource(): DataSource = delegate.createDataSource()

    private fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") return dataSpec
        val url = uri.toString()
        val userAgent = youtubeUserAgent(url)
        if (uri.path?.startsWith("/videoplayback") != true) {
            return dataSpec.withAdditionalHeaders(mapOf("User-Agent" to userAgent))
        }

        val builder = uri.buildUpon()
        if (uri.getQueryParameter("rn") == null) {
            builder.appendQueryParameter("rn", requestNumber.getAndIncrement().toString())
        }
        val headers = mutableMapOf("TE" to "trailers", "User-Agent" to userAgent)
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            headers["Origin"] = YOUTUBE_URL
            headers["Referer"] = YOUTUBE_URL
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
        }
        return dataSpec.buildUpon()
            .setUri(builder.build())
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)
            .build()
            .withAdditionalHeaders(headers)
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com"
        val POST_BODY = byteArrayOf(0x78, 0)
    }
}
