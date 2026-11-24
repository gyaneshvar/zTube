package com.ztube.playback

import com.ztube.data.extractor.OkHttpDownloader
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

fun youtubeUserAgent(url: String): String =
    if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
        YoutubeParsingHelper.getVisionOsUserAgent(null)
    } else {
        OkHttpDownloader.USER_AGENT
    }
