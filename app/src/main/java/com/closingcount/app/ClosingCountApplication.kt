package com.closingcount.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.online.OnlineRepository
import com.closingcount.app.data.online.OnlineSessionStore
import com.closingcount.app.data.online.OnlineSettingsStore
import com.closingcount.app.data.online.OnlineTeamStore
import java.util.Locale

class ClosingCountApplication : Application() {
    val database: ClosingCountDatabase by lazy {
        ClosingCountDatabase.getInstance(this)
    }
    val onlineRepository: OnlineRepository by lazy {
        OnlineRepository(database.onlineSyncDao(), OnlineSessionStore(this), OnlineSettingsStore(this), OnlineTeamStore(this))
    }

    override fun attachBaseContext(base: Context) {
        val locale = Locale.forLanguageTag("id-ID")
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate() {
        super.onCreate()
        Thread {
            database.openHelper.writableDatabase
        }.start()
    }
}
