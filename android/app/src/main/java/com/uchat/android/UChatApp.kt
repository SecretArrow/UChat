package com.uchat.android

import android.app.Application
import com.uchat.android.core.BuildInfo
import com.uchat.android.di.AppContainer
import com.uchat.android.service.NotificationHelper

class UChatApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BuildInfo.VERSION_NAME =
            try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "dev"
            } catch (_: Exception) {
                "dev"
            }
        BuildInfo.VERSION_CODE =
            try {
                packageManager.getPackageInfo(packageName, 0).let {
                    if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt()
                    else it.versionCode
                }
            } catch (_: Exception) {
                0
            }
        NotificationHelper.createChannels(this)
    }
}
