package com.example.senseclockv1

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Shared by the settings screen and scheduled jobs; only one API refresh runs at a time. */
internal object WeatherStore {
    private val refreshLock = Mutex()

    fun config(context: Context): ApiConfig {
        val prefs = context.getSharedPreferences("weather_credentials", Context.MODE_PRIVATE)
        return ApiConfig(prefs.getString("host", "").orEmpty(), prefs.getString("key", "").orEmpty())
    }

    fun cached(context: Context): WeatherSnapshot? {
        if (!config(context).isConfigured) return null
        return WeatherSnapshot.fromJson(context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            .getString("snapshot", "").orEmpty())
    }

    fun lastError(context: Context): String? = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
        .getString("refresh_error", null)

    private fun location(context: Context): WeatherLocation? = WeatherLocation.fromJson(
        context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE).getString("location", "").orEmpty())

    private fun locationDue(place: WeatherLocation?, settings: WeatherPreferences, now: Long): Boolean =
        place == null || place.source != settings.locationKey ||
            (settings.automaticLocation && now - place.updatedAt !in 0 until settings.locationIntervalMillis)

    fun needsRefresh(context: Context, maxAgeMillis: Long = WeatherPreferences.load(context).weatherIntervalMillis): Boolean {
        val settings = WeatherPreferences.load(context)
        val now = System.currentTimeMillis()
        val weather = cached(context)
        val place = location(context)
        return locationDue(place, settings, now) || weather == null || weather.locationId != place?.id ||
            weather.locationKey != settings.locationKey ||
            now - weather.updatedAt !in 0 until maxAgeMillis
    }

    fun cacheNotice(context: Context): String? {
        val weather = cached(context) ?: return null
        val settings = WeatherPreferences.load(context)
        val place = location(context)
        return when {
            weather.locationKey != settings.locationKey || (place != null && weather.locationId != place.id) ->
                "位置已改变，正在展示上次位置的缓存，城市和更新时间见上方。"
            lastError(context) != null -> "刷新失败，正在展示缓存天气，请留意上次更新时间。"
            System.currentTimeMillis() - weather.updatedAt !in 0 until settings.weatherIntervalMillis ||
                weather.forecastDate != beijingDate() -> "正在展示上次缓存天气，请留意上次更新时间。"
            else -> null
        }
    }

    suspend fun refresh(
        context: Context,
        force: Boolean = false,
        maxAgeMillis: Long = WeatherPreferences.load(context).weatherIntervalMillis
    ): WeatherSnapshot = refreshLock.withLock {
        val config = config(context)
        check(config.isConfigured) { "请先保存天气 API 配置。" }
        val settings = WeatherPreferences.load(context)
        val cache = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
        val revision = cache.getLong("revision", 0)
        fun configurationUnchanged(): Boolean = config == config(context) &&
            revision == cache.getLong("revision", 0)
        fun checkConfiguration() {
            if (!configurationUnchanged()) throw CancellationException("Weather settings changed")
        }
        try {
            var place = location(context)
            if (locationDue(place, settings, System.currentTimeMillis())) {
                place = WeatherClient.resolveLocation(config, settings)
                currentCoroutineContext().ensureActive()
                checkConfiguration()
                cache.edit().putString("location", place.toJson()).apply()
            }
            val resolved = checkNotNull(place)
            if (!force) cached(context)?.let {
                if (it.locationId == resolved.id && it.locationKey == settings.locationKey &&
                    System.currentTimeMillis() - it.updatedAt in 0 until maxAgeMillis) return@withLock it
            }
            val result = WeatherClient.refresh(config, resolved)
            currentCoroutineContext().ensureActive()
            checkConfiguration()
            // Only a complete successful response replaces the previous weather snapshot.
            cache.edit().putString("snapshot", result.toJson()).remove("refresh_error").apply()
            ClockWidgetProvider.requestUpdate(context)
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            checkConfiguration()
            cache.edit().putString("refresh_error", errorMessage(error)).apply()
            ClockWidgetProvider.requestUpdate(context)
            throw error
        }
    }

    fun errorMessage(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException -> "请求超时，请检查网络后重试。"
        is java.net.UnknownHostException -> "无法连接服务器，请检查网络及 API HOST。"
        is IOException -> if (error.message?.startsWith("服务请求失败") == true) error.message.orEmpty()
            else "网络请求失败，请检查网络和 API HOST 后重试。"
        is IllegalStateException -> error.message ?: "天气刷新失败，请稍后重试。"
        else -> "天气服务返回的数据无法解析，请检查配置或稍后重试。"
    }
}
