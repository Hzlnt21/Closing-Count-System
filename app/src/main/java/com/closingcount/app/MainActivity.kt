package com.closingcount.app

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.closingcount.app.ui.ClosingCountApp
import com.closingcount.app.ui.theme.ClosingCountTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        val locale = Locale.forLanguageTag("id-ID")
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClosingCountTheme {
                ClosingCountApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A recreated window can return from DocumentsUI with stale system-bar
        // appearance. Reapply the current light/dark style when it is resumed.
        enableEdgeToEdge()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.post {
            if (window.decorView.hasWindowFocus()) enableEdgeToEdge()
        }
    }
}
