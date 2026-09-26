package com.example.senseclockv1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WeatherScreen(
    settings: SettingsViewModel,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit
) {
    val weather = settings.weather
    val iconFont = rememberQWeatherIconFont()
    val layoutDirection = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (settings.refreshing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.refreshing),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            settings.refreshError?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )) {
                        Text(
                            stringResource(R.string.weather_refresh_failed, error),
                            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            settings.cacheMessage?.let { message ->
                item {
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!settings.canRefresh && !settings.refreshing && weather != null) {
                item {
                    Text(stringResource(when {
                        settings.hasUnsavedApi -> R.string.save_first
                        settings.hasUnsavedWeatherSettings -> R.string.save_weather_first
                        else -> R.string.configure_first
                    }), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (weather == null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.weather_empty), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.weather_empty_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.updated_at,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(weather.updatedAt))),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(weather.city, style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.semantics { heading() })                                             // 城市
                        Text(
                            stringResource(when {
                                weather.locationKey == "auto" -> R.string.automatic_location
                                weather.locationKey.startsWith("fixed:") -> R.string.fixed_location
                                else -> R.string.cached_location
                            }),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                        QWeatherIcon(iconFont, weather.icon, weather.description, 200.sp)                // 天气图标
                        Text(weather.description,                                                                     // 天气描述
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(stringResource(R.string.temperature_celsius, weather.temperature),                              // 当前温度
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(stringResource(R.string.high_low, weather.high, weather.low),           // 高低温
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                item {
                    WeatherSection(stringResource(R.string.weather_details)) {
                        if (weather.details.isEmpty()) {
                            Text(stringResource(R.string.weather_details_empty), Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        WeatherDetail(stringResource(R.string.feels_like), weather.detail("feelsLike", "°C"))
                        WeatherDetail(stringResource(R.string.humidity), weather.detail("humidity", "%"))
                        WeatherDetail(stringResource(R.string.wind_direction), weather.detail("windDir"))
                        WeatherDetail(stringResource(R.string.wind_scale), weather.detail("windScale", stringResource(R.string.wind_scale_unit)))
                        WeatherDetail(stringResource(R.string.precipitation), weather.detail("precip", " mm"))
                        WeatherDetail(stringResource(R.string.pressure), weather.detail("pressure", " hPa"))
                        WeatherDetail(stringResource(R.string.visibility), weather.detail("vis", " km"))
                    }
                }
                item {
                    WeatherSection(stringResource(R.string.weather_forecast)) {
                        if (weather.forecast.isEmpty()) {
                            Text(stringResource(R.string.weather_forecast_empty), Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        weather.forecast.forEachIndexed { index, day ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            ListItem(
                                headlineContent = { Text(day.date) },
                                supportingContent = {
                                    Column {
                                        Text(stringResource(R.string.forecast_day, day.dayText))
                                        Text(stringResource(R.string.forecast_night, day.nightText))
                                    }
                                },
                                leadingContent = { QWeatherIcon(iconFont, day.iconDay, day.dayText, 46.sp) },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(stringResource(R.string.temperature_celsius, day.high),
                                            style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.temperature_celsius, day.low),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }
                    }
                }
                item { Text(stringResource(R.string.weather_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun WeatherSnapshot.detail(key: String, unit: String = ""): String =
    details[key]?.takeIf { it.isNotBlank() && it != "null" }?.let { it + unit } ?: "—"

@Composable
private fun WeatherDetail(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyLarge) }
    )
}

@Composable
private fun WeatherSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
        OutlinedCard(Modifier.fillMaxWidth()) {
            content()
        }
    }
}
