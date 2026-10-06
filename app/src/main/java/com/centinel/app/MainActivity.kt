package com.centinel.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.centinel.app.data.local.SettingsStore
import com.centinel.app.ui.navigation.CentinelNavGraph
import com.centinel.app.ui.theme.CentinelTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val settingsStore = remember { SettingsStore(applicationContext) }
            CentinelTheme(settingsStore = settingsStore) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CentinelNavGraph(settingsStore = settingsStore)
                }
            }
        }
    }
}
