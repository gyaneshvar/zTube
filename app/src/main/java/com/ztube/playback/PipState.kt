package com.ztube.playback

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipState @Inject constructor() {
    val canEnter = MutableStateFlow(false)
    val inPip = MutableStateFlow(false)
}
