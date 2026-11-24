package com.ztube.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _currentItem = MutableStateFlow<MediaItem?>(null)
    val currentItem: StateFlow<MediaItem?> = _currentItem.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var pending: ListenableFuture<MediaController>? = null

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (_controller.value === controller) {
                _controller.value = null
                pending = null
            }
            controller.release()
            _currentItem.value = null
            _playing.value = false
        }

        override fun onError(controller: MediaController, sessionError: SessionError) {
            _errors.tryEmit(sessionError.message)
        }
    }

    fun connect() {
        if (_controller.value == null && pending == null) start()
    }

    suspend fun awaitController(): MediaController {
        _controller.value?.let { return it }
        val future = pending ?: start()
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    fun togglePlay() {
        val player = _controller.value ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause() else player.play()
    }

    fun close() {
        _controller.value?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun start(): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).setListener(controllerListener).buildAsync()
        pending = future
        future.addListener(
            {
                try {
                    val connected = future.get()
                    connected.addListener(listener)
                    sync(connected)
                    _controller.value = connected
                } catch (e: Exception) {
                    pending = null
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        return future
    }

    private fun sync(player: Player) {
        _currentItem.value = player.currentMediaItem
        _playing.value = player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE
    }
}
