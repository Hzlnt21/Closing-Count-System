package com.closingcount.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.closingcount.app.ui.ClosingCountApp
import com.closingcount.app.ui.theme.ClosingCountTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClosingCountTheme {
                ClosingCountApp()
            }
        }
    }
}

