package com.example.senseclockv1

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: SettingsViewModel,
    exactUpdatesAllowed: Boolean,
    onBack: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SettingsSection(stringResource(R.string.clock_settings)) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        WidgetPreview(settings.theme, settings.is24Hour)
                        Text(stringResource(R.string.widget_preview_hint),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.use_24_hour_format)) },
                        supportingContent = {
                            Text(stringResource(if (settings.is24Hour) R.string.format_24h_hint else R.string.format_12h_hint),
                                style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                        trailingContent = { Switch(settings.is24Hour, onCheckedChange = null) },
                        modifier = Modifier.toggleable(value = settings.is24Hour,
                            role = Role.Switch, onValueChange = settings::changeTimeFormat)
                    )
                    ExposedDropdownMenuBox(themeExpanded, { themeExpanded = it },
                        modifier = Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(settings.theme.label, {}, readOnly = true,
                            label = { Text(stringResource(R.string.clock_theme)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                        ExposedDropdownMenu(themeExpanded, { themeExpanded = false }) {
                            ClockTheme.entries.forEach { theme -> DropdownMenuItem(text = { Text(theme.label) }, onClick = {
                                settings.selectTheme(theme); themeExpanded = false
                            }) }
                        }
                    }
                }
                HorizontalDivider()
                WeatherSettingsSection(settings)
                HorizontalDivider()
                SettingsSection(stringResource(R.string.widget_settings)) {
                    if (AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.add_widget)) },
                            supportingContent = { Text(stringResource(R.string.widget_help)) },
                            leadingContent = { Icon(Icons.Outlined.Widgets, contentDescription = null) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                AppWidgetManager.getInstance(context).requestPinAppWidget(
                                    ComponentName(context, ClockWidgetProvider::class.java), null, null)
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.add_widget)) },
                            supportingContent = { Text(stringResource(R.string.widget_add_manually)) },
                            leadingContent = { Icon(Icons.Outlined.Widgets, contentDescription = null) }
                        )
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.enable_exact_updates)) },
                        supportingContent = { Text(stringResource(
                            if (exactUpdatesAllowed) R.string.exact_updates_enabled else R.string.widget_timing_hint)) },
                        leadingContent = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"))
                            try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")))
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.open_battery_policy)) },
                        supportingContent = { Text(stringResource(R.string.battery_policy_hint)) },
                        leadingContent = { Icon(Icons.Outlined.BatterySaver, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, R.string.battery_settings_unavailable, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
                HorizontalDivider()
                SettingsSection(stringResource(R.string.weather_api)) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.api_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = settings.host,
                            onValueChange = settings::changeHost,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !settings.refreshing,
                            label = { Text(stringResource(R.string.api_host)) },
                            placeholder = { Text("xxxxxxxx.qweatherapi.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            isError = settings.apiError != null
                        )
                        OutlinedTextField(
                            value = settings.apiKey,
                            onValueChange = settings::changeKey,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !settings.refreshing,
                            label = { Text(stringResource(R.string.api_key)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = stringResource(if (showKey) R.string.hide_key else R.string.show_key))
                                }
                            },
                            isError = settings.apiError != null
                        )
                        settings.apiError?.let { StatusText(it) }
                        Button(onClick = { focus.clearFocus(); settings.saveApi() },
                            enabled = !settings.refreshing && settings.hasUnsavedApi,
                            modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.save_api))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(theme: ClockTheme, is24Hour: Boolean) {
    val resources = LocalResources.current
    val resource = theme.previewResource(is24Hour)
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(resource) { bitmap = withContext(Dispatchers.IO) {
        PreviewBitmapCache.getOrLoad(resource) { BitmapFactory.decodeResource(resources, resource) }
    } }
    Box(Modifier.fillMaxWidth().height(220.dp).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.widget_preview_hint), Modifier.fillMaxSize()) }
    }
}

private object PreviewBitmapCache {
    private val bitmaps = object : LruCache<Int, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }
    @Synchronized fun getOrLoad(resource: Int, load: () -> Bitmap?): Bitmap? =
        bitmaps.get(resource) ?: load()?.also { bitmaps.put(resource, it) }
}

private fun ClockTheme.previewResource(is24Hour: Boolean): Int = when (this) {
    ClockTheme.Default -> if (is24Hour) R.drawable.preview_default_24 else R.drawable.preview_default_12
    ClockTheme.Aluminum -> if (is24Hour) R.drawable.preview_aluminum_24 else R.drawable.preview_aluminum_12
    ClockTheme.Concrete -> if (is24Hour) R.drawable.preview_concrete_24 else R.drawable.preview_concrete_12
    ClockTheme.Graphite -> if (is24Hour) R.drawable.preview_graphite_24 else R.drawable.preview_graphite_12
    ClockTheme.Matte -> if (is24Hour) R.drawable.preview_matte_24 else R.drawable.preview_matte_12
    ClockTheme.Steel -> if (is24Hour) R.drawable.preview_steel_24 else R.drawable.preview_steel_12
}

@Composable
private fun WeatherSettingsSection(settings: SettingsViewModel) {
    val value = settings.weatherSettings
    val enabled = !settings.refreshing
    val focus = LocalFocusManager.current
    SettingsSection(stringResource(R.string.weather_settings)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.automatic_location)) },
            supportingContent = { Text(stringResource(if (value.automaticLocation) R.string.automatic_location_hint else R.string.fixed_location_hint)) },
            leadingContent = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
            trailingContent = { Switch(value.automaticLocation, onCheckedChange = null, enabled = enabled) },
            modifier = Modifier.toggleable(value = value.automaticLocation, enabled = enabled,
                role = Role.Switch, onValueChange = { settings.changeWeatherSettings(value.copy(automaticLocation = it)) })
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (value.automaticLocation) {
                RefreshIntervalDropdown(stringResource(R.string.location_refresh_interval), value.locationHours * 60,
                    WeatherPreferences.locationHourOptions.map { it * 60 }, enabled) {
                    settings.changeWeatherSettings(value.copy(locationHours = it / 60))
                }
            } else {
                OutlinedTextField(
                    value = value.province,
                    onValueChange = { settings.changeWeatherSettings(value.copy(province = it)) },
                    label = { Text(stringResource(R.string.location_province)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    isError = settings.weatherSettingsError != null
                )
                OutlinedTextField(
                    value = value.city,
                    onValueChange = { settings.changeWeatherSettings(value.copy(city = it)) },
                    label = { Text(stringResource(R.string.location_city)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    isError = settings.weatherSettingsError != null
                )
                Text(stringResource(R.string.fixed_location_input_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RefreshIntervalDropdown(stringResource(R.string.weather_refresh_interval), value.weatherMinutes,
                WeatherPreferences.weatherMinuteOptions, enabled) {
                settings.changeWeatherSettings(value.copy(weatherMinutes = it))
            }
            Text(stringResource(R.string.refresh_policy_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            settings.weatherSettingsError?.let { StatusText(it) }
            Button(onClick = { focus.clearFocus(); settings.saveWeatherSettings() },
                enabled = enabled && settings.hasUnsavedWeatherSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.save_weather_settings))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIntervalDropdown(label: String, minutes: Int, options: List<Int>, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded && enabled, { if (enabled) expanded = it }) {
        OutlinedTextField(intervalLabel(minutes), {}, readOnly = true, enabled = enabled, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled))
        ExposedDropdownMenu(expanded && enabled, { expanded = false }) { options.forEach { value ->
            DropdownMenuItem({ Text(intervalLabel(value)) }, { expanded = false; onSelect(value) })
        } }
    }
}

@Composable
private fun intervalLabel(value: Int): String = if (value < 60) {
    stringResource(R.string.interval_minutes, value)
} else {
    stringResource(R.string.interval_hours, value / 60)
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
        content()
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
