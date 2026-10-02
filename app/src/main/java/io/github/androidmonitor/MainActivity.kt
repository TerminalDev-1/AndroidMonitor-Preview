package io.github.androidmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.androidmonitor.ui.AppRoot
import io.github.androidmonitor.ui.theme.AndroidMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AndroidMonitorTheme {
                AppRoot()
            }
        }
    }
}
