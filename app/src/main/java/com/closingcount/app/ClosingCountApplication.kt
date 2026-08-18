package com.closingcount.app

import android.app.Application
import com.closingcount.app.data.local.ClosingCountDatabase

class ClosingCountApplication : Application() {
    val database: ClosingCountDatabase by lazy {
        ClosingCountDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        Thread {
            database.openHelper.writableDatabase
        }.start()
    }
}

