package dev.goodwy.rphone

import android.app.Application
import dev.goodwy.rphone.controller.util.alias.CallerAliasManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RillApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallerAliasManager.init(this)
        startKoin {
            androidContext(this@RillApp)
            modules(appModule)
        }
    }
}
