package com.ztube

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ztube.data.feed.FeedRefreshWorker
import com.ztube.data.account.YoutubeAccountManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import com.ztube.data.extractor.OkHttpDownloader
import java.util.concurrent.TimeUnit
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
        DebugLog.start(this)
        DebugLog.log("App", "onCreate")
        NewPipe.init(OkHttpDownloader())
        scheduleFeedRefresh()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch { accountManager.scheduleIfSignedIn() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch { accountManager.syncIfStale() }
            }
        })
    }

    private fun scheduleFeedRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(8, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(FeedRefreshWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        DebugLog.persist()
    }
}
