package com.example.senseclockv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.example.senseclockv1.ui.theme.SenseClockV1Theme

class MainActivity : ComponentActivity() {
    private var exactUpdatesAllowed by mutableStateOf(true)

    override fun onResume() {
        super.onResume()
        exactUpdatesAllowed = ClockWidgetProvider.exactUpdatesAllowed(this)
        ViewModelProvider(this)[SettingsViewModel::class.java].onAppOpened()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = ViewModelProvider(this)[SettingsViewModel::class.java]
        setContent {
            SenseClockV1Theme { MainScreen(settings, exactUpdatesAllowed) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settings: SettingsViewModel, exactUpdatesAllowed: Boolean) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmRefresh by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    if (showSettings) {
        SettingsScreen(settings, exactUpdatesAllowed) { showSettings = false }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.tab_weather)) },
                    actions = {
                        IconButton(onClick = { confirmRefresh = true }, enabled = settings.canRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_weather))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.open_settings))
                        }
                    }
                )
            }
        ) { insets ->
            WeatherScreen(settings, contentPadding = insets, onOpenSettings = { showSettings = true })
        }
        if (confirmRefresh) {
            AlertDialog(
                onDismissRequest = { confirmRefresh = false },
                title = { Text(stringResource(R.string.refresh_dialog_title)) },
                text = { Text(stringResource(R.string.refresh_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRefresh = false
                        settings.forceRefresh()
                    }) {
                        Text(stringResource(R.string.force_refresh))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRefresh = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
