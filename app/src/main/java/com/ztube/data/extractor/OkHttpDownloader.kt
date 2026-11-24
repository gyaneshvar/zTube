package com.ztube.data.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class OkHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : Downloader() {

    override fun execute(request: Request): Response {
        val url = request.url()
        val body = request.dataToSend()?.toRequestBody()

        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    }
}
