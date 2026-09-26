package com.example.senseclockv1

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
    private val credentials = application.getSharedPreferences("weather_credentials", Context.MODE_PRIVATE)
    private val cache = application.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private var savedApi by mutableStateOf(ApiConfig(
        credentials.getString("host", "").orEmpty(), credentials.getString("key", "").orEmpty()
    ))
    private var savedWeatherSettings by mutableStateOf(WeatherPreferences.load(application))
    var weatherSettings by mutableStateOf(savedWeatherSettings)
        private set
    var weatherSettingsError by mutableStateOf<String?>(null)
        private set

    var host by mutableStateOf(savedApi.host)
        private set
    var apiKey by mutableStateOf(savedApi.key)
        private set
    var is24Hour by mutableStateOf(preferences.getBoolean("is_24_hour", true))
        private set
    var theme by mutableStateOf(ClockTheme.entries.firstOrNull {
        it.name == preferences.getString("theme", "Default")
    } ?: ClockTheme.Default)
        private set
    var apiError by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var refreshError by mutableStateOf(WeatherStore.lastError(application))
        private set
    var refreshing by mutableStateOf(false)
        private set
    var weather by mutableStateOf(
        if (savedApi.isConfigured) WeatherSnapshot.fromJson(cache.getString("snapshot", "").orEmpty()) else null
    )
        private set
    var cacheMessage by mutableStateOf(WeatherStore.cacheNotice(application))
        private set

    val hasUnsavedApi: Boolean get() = host != savedApi.host || apiKey != savedApi.key
    val hasUnsavedWeatherSettings: Boolean get() = weatherSettings != savedWeatherSettings
    val canRefresh: Boolean get() = savedApi.isConfigured && !hasUnsavedApi &&
        !hasUnsavedWeatherSettings && !refreshing

    private val cacheListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        syncWeather()
    }

    init { cache.registerOnSharedPreferenceChangeListener(cacheListener) }

    override fun onCleared() {
        cache.unregisterOnSharedPreferenceChangeListener(cacheListener)
    }

    fun changeHost(value: String) { host = value; apiError = null; message = null }
    fun changeKey(value: String) { apiKey = value; apiError = null; message = null }

    private fun syncWeather() {
        weather = WeatherStore.cached(getApplication())
        refreshError = if (savedApi.isConfigured) WeatherStore.lastError(getApplication()) else null
        if (refreshError != null) message = null
        cacheMessage = WeatherStore.cacheNotice(getApplication())
    }

    fun changeWeatherSettings(value: WeatherPreferences) {
        weatherSettings = value
        weatherSettingsError = null
        message = null
    }

    fun saveWeatherSettings() {
        if (refreshing) return
        val value = weatherSettings.copy(province = weatherSettings.province.trim(), city = weatherSettings.city.trim())
        if (!value.automaticLocation && listOf(value.province, value.city).any {
                normalizeRegion(it).isBlank() || it.length > 80 || it.any(Char::isISOControl)
            }) {
            weatherSettingsError = "请填写有效的省和市，例如：广东省 / 深圳市；直辖市可填写北京市 / 北京市。"
            return
        }
        if (value.locationHours !in WeatherPreferences.locationHourOptions ||
            value.weatherMinutes !in WeatherPreferences.weatherMinuteOptions) return
        val locationChanged = value.locationKey != savedWeatherSettings.locationKey
        if (value != savedWeatherSettings) {
            val edit = cache.edit().putLong("revision", cache.getLong("revision", 0) + 1)
            if (locationChanged) edit.remove("location")
            edit.apply()
        }
        preferences.edit()
            .putBoolean("automatic_location", value.automaticLocation)
            .putString("location_province", value.province).putString("location_city", value.city)
            .putInt("location_hours", value.locationHours).putInt("weather_minutes", value.weatherMinutes).apply()
        savedWeatherSettings = value
        weatherSettings = value
        weatherSettingsError = null
        message = "定位和刷新设置已保存。"
        syncWeather()
        WidgetWeatherJob.schedule(getApplication())
        ClockWidgetProvider.requestUpdate(getApplication())
        refreshWeather(force = locationChanged, maxAgeMillis = value.weatherIntervalMillis)
    }

    fun onAppOpened() {
        syncWeather()
        WidgetWeatherJob.schedule(getApplication())
        refreshWeather(maxAgeMillis = WeatherPreferences.FOREGROUND_MAX_AGE)
    }

    fun changeTimeFormat(value: Boolean) {
        is24Hour = value
        preferences.edit().putBoolean("is_24_hour", value).apply()
        ClockWidgetProvider.requestUpdate(getApplication())
    }

    fun selectTheme(value: ClockTheme) {
        theme = value
        preferences.edit().putString("theme", value.name).apply()
        ClockWidgetProvider.requestUpdate(getApplication())
    }

    fun saveApi() {
        if (refreshing) return
        val config = try {
            ApiConfig.parse(host, apiKey)
        } catch (error: IllegalArgumentException) {
            apiError = error.message
            return
        }
        val changed = config != savedApi
        if (changed) {
            cache.edit().remove("location").remove("refresh_error")
                .putLong("revision", cache.getLong("revision", 0) + 1).apply()
        }
        credentials.edit().putString("host", config.host).putString("key", config.key).apply()
        savedApi = config
        host = config.host
        apiKey = config.key
        apiError = null
        message = if (config.isConfigured) "API 配置已保存。" else "已停用天气。"
        syncWeather()
        ClockWidgetProvider.requestUpdate(getApplication())
        WidgetWeatherJob.schedule(getApplication())
        refreshWeather(force = changed)
    }

    fun forceRefresh() {
        refreshWeather(force = true)
    }

    private fun refreshWeather(force: Boolean = false, maxAgeMillis: Long = WeatherPreferences.FOREGROUND_MAX_AGE) {
        if (!canRefresh) return
        if (!force && !WeatherStore.needsRefresh(getApplication(), maxAgeMillis)) return
        refreshing = true
        message = null
        viewModelScope.launch {
            try {
                val result = WeatherStore.refresh(getApplication(), force = force, maxAgeMillis = maxAgeMillis)
                weather = result
                syncWeather()
                if (refreshError == null) message = "天气已更新。"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                syncWeather()
                refreshError = WeatherStore.errorMessage(error)
            } finally {
                refreshing = false
            }
        }
    }
}
