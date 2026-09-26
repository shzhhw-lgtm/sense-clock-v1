package com.example.senseclockv1

import android.content.Context

private const val DEFAULT_LOCATION_HOURS = 24

data class WeatherPreferences(
    val automaticLocation: Boolean = true,
    val province: String = "",
    val city: String = "",
    val locationHours: Int = DEFAULT_LOCATION_HOURS,
    val weatherMinutes: Int = 15
) {
    val locationKey: String get() = if (automaticLocation) "auto" else "fixed:$province/$city"
    val locationIntervalMillis: Long get() = locationHours * 60 * 60_000L
    val weatherIntervalMillis: Long get() = weatherMinutes * 60_000L
    // A location check can be due before the next weather request (e.g. 2h location / 24h weather).
    val jobIntervalMillis: Long get() = if (automaticLocation)
        minOf(locationIntervalMillis, weatherIntervalMillis) else weatherIntervalMillis

    companion object {
        val locationHourOptions = listOf(2, 6, 12, 24)
        val weatherMinuteOptions = listOf(15, 30, 60, 120, 360, 720)
        const val FOREGROUND_MAX_AGE = 15 * 60_000L

        fun load(context: Context): WeatherPreferences {
            val prefs = context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
            return WeatherPreferences(
                prefs.getBoolean("automatic_location", true),
                prefs.getString("location_province", "").orEmpty(),
                prefs.getString("location_city", "").orEmpty(),
                prefs.getInt("location_hours", DEFAULT_LOCATION_HOURS)
                    .takeIf { it in locationHourOptions } ?: DEFAULT_LOCATION_HOURS,
                prefs.getInt("weather_minutes", 15).takeIf { it in weatherMinuteOptions } ?: 15
            )
        }
    }
}
