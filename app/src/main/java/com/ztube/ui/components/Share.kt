package com.ztube.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

fun shareVideo(context: Context, url: String, title: String, isShort: Boolean) {
    val id = videoId(url)
    val link = when {
        id == null -> url
        isShort -> "https://www.youtube.com/shorts/$id"
        else -> "https://www.youtube.com/watch?v=$id"
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$link")
    context.startActivity(Intent.createChooser(send, "Share"))
}

private fun videoId(url: String): String? {
    val uri = Uri.parse(url)
    val segments = uri.pathSegments
    val id = when {
        uri.host == "youtu.be" -> segments.firstOrNull()
        segments.firstOrNull() == "shorts" -> segments.getOrNull(1)
        else -> uri.getQueryParameter("v")
    }
    return id?.takeIf { it.length == 11 }
}
