package com.ztube

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.ztube.data.account.YoutubeAccountManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import com.ztube.data.extractor.OkHttpDownloader
import javax.inject.Inject

@HiltAndroidApp
class ZTubeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var accountManager: YoutubeAccountManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        NewPipe.init(OkHttpDownloader())
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch { accountManager.scheduleIfSignedIn() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch { accountManager.syncIfStale() }
            }
        })
    }
}
