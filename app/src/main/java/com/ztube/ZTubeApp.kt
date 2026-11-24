package com.ztube

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import com.ztube.data.extractor.OkHttpDownloader
import javax.inject.Inject

@HiltAndroidApp
class ZTubeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloader())
    }
}
