package com.example.senseclockv1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WeatherSnapshot(
    val city: String,
    val temperature: String,
    val description: String,
    val high: String,
    val low: String,
    val forecastDate: String,
    val updatedAt: Long,
    val icon: String = "999",
    val cloud: Int? = null,
    val locationId: String = "",
    val locationKey: String = "",
    val details: Map<String, String> = emptyMap(),
    val forecast: List<DailyForecast> = emptyList()
) {
    fun toJson(): String = JSONObject().apply {
        put("city", city)
        put("temperature", temperature)
        put("description", description)
        put("high", high)
        put("low", low)
        put("forecastDate", forecastDate)
        put("updatedAt", updatedAt)
        put("icon", icon)
        put("cloud", cloud)
        put("locationId", locationId)
        put("locationKey", locationKey)
        put("details", JSONObject(details))
        put("forecast", JSONArray().apply {
            forecast.forEach { day ->
                put(JSONObject().apply {
                    put("date", day.date)
                    put("dayText", day.dayText)
                    put("nightText", day.nightText)
                    put("low", day.low)
                    put("high", day.high)
                    put("iconDay", day.iconDay)
                    put("iconNight", day.iconNight)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): WeatherSnapshot? = runCatching {
            val data = JSONObject(json)
            val details = data.optJSONObject("details")
            val forecast = data.optJSONArray("forecast")
            WeatherSnapshot(
                data.getString("city"), data.getString("temperature"),
                data.getString("description"), data.getString("high"), data.getString("low"),
                data.getString("forecastDate"), data.getLong("updatedAt"),
                data.optString("icon", "999"), data.optString("cloud").toIntOrNull(),
                data.optString("locationId"), data.optString("locationKey"),
                details?.let { fields ->
                    fields.keys().asSequence().associateWith { fields.getString(it) }
                }.orEmpty(),
                if (forecast == null) emptyList() else (0 until forecast.length()).map {
                    val day = forecast.getJSONObject(it)
                    DailyForecast(day.getString("date"), day.getString("dayText"),
                        day.getString("nightText"), day.getString("low"), day.getString("high"),
                        day.optString("iconDay", "999"), day.optString("iconNight", "999"))
                }
            )
        }.getOrNull()
    }
}

data class DailyForecast(
    val date: String,
    val dayText: String,
    val nightText: String,
    val low: String,
    val high: String,
    val iconDay: String = "999",
    val iconNight: String = "999"
)

internal fun beijingDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
}.format(Date())

data class WeatherLocation(val id: String, val name: String, val source: String, val updatedAt: Long) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("source", source)
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        fun fromJson(json: String): WeatherLocation? = runCatching {
            val data = JSONObject(json)
            WeatherLocation(data.getString("id"), data.getString("name"),
                data.getString("source"), data.getLong("updatedAt"))
                .takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrNull()
    }
}

object WeatherClient {
    private const val MAX_RESPONSE_CHARS = 256 * 1024

    suspend fun resolveLocation(config: ApiConfig, settings: WeatherPreferences): WeatherLocation = withContext(Dispatchers.IO) {
        check(config.isConfigured) { "请先保存天气 API 配置。" }
        val place = if (!settings.automaticLocation) {
            check(settings.province.isNotBlank() && settings.city.isNotBlank()) { "请填写并保存省和市。" }
            lookupCity(config, IpCity(settings.province, settings.city))
        } else try {
            lookupCity(config, parseIpCity(request("https://myip.ipip.net")))
        } catch (_: IOException) {
            lookupFallback(config)
        } catch (_: IllegalStateException) {
            lookupFallback(config)
        }
        WeatherLocation(place.required("id"), place.required("name"), settings.locationKey, System.currentTimeMillis())
    }

    suspend fun refresh(config: ApiConfig, place: WeatherLocation): WeatherSnapshot = withContext(Dispatchers.IO) {
        check(config.isConfigured) { "请先保存天气 API 配置。" }
        val id = encode(place.id)
        val now = qweather(config, "/v7/weather/now?location=$id&lang=zh&unit=m").getJSONObject("now")
        val daily = qweather(config, "/v7/weather/3d?location=$id&lang=zh&unit=m").getJSONArray("daily")
        val date = beijingDate()
        val forecast = (0 until daily.length()).map { daily.getJSONObject(it) }
            .sortedBy { it.getString("fxDate") }.take(3).map {
                DailyForecast(it.required("fxDate"), it.required("textDay"), it.required("textNight"),
                    validatedTemperature(it.required("tempMin")), validatedTemperature(it.required("tempMax")),
                    it.optString("iconDay", "999"), it.optString("iconNight", "999"))
            }
        val today = forecast.firstOrNull { it.date == date }
            ?: error("天气服务未返回今天的预报，请稍后重试。")
        WeatherSnapshot(
            place.name, validatedTemperature(now.required("temp")), now.required("text"),
            today.high, today.low, date, System.currentTimeMillis(),
            now.optString("icon", "999"), now.optString("cloud").toIntOrNull(), place.id, place.source,
            now.keys().asSequence().filter { !now.isNull(it) }.associateWith { now.getString(it) },
            forecast
        )
    }

    private fun lookupFallback(config: ApiConfig): JSONObject {
        val data = JSONObject(request("http://ip-api.com/json/?lang=zh-CN"))
        check(data.optString("status") == "success" && data.optString("countryCode") == "CN") {
            "IP 定位失败或不属于中国城市，请检查网络后重试。"
        }
        return lookupCity(config, IpCity(data.required("regionName"), data.required("city")))
    }

    private fun lookupCity(config: ApiConfig, ip: IpCity): JSONObject {
        val results = qweather(config, "/geo/v2/city/lookup?location=${encode(ip.city)}" +
            "&adm=${encode(ip.province)}&range=cn&lang=zh&number=20").getJSONArray("location")
        var matches = (0 until results.length()).map { results.getJSONObject(it) }.filter {
            it.optString("id").isNotBlank() &&
                normalizeRegion(it.optString("adm1")) == normalizeRegion(ip.province) &&
                normalizeRegion(it.optString("name")) == normalizeRegion(ip.city)
        }
        if (matches.size > 1) matches = matches.filter {
            normalizeRegion(it.optString("adm2")) == normalizeRegion(ip.city)
        }
        check(matches.size == 1) { "无法唯一匹配当前城市：${ip.province} / ${ip.city}。" }
        return matches.single()
    }

    private fun qweather(config: ApiConfig, path: String): JSONObject {
        val data = JSONObject(request("https://${config.host}$path", config.key))
        check(data.optString("code") == "200") {
            "和风天气返回错误（${data.optString("code", "未知")}），请检查 API 配置和接口权限。"
        }
        return data
    }

    private fun request(url: String, apiKey: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.useCaches = false
            // Do not forward credentials to a redirected host.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            apiKey?.let { connection.setRequestProperty("X-QW-Api-Key", it) }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("服务请求失败（HTTP $status）。")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8 * 1024)
                val result = StringBuilder()
                var total = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_CHARS) {
                        throw IOException("服务响应过大。")
                    }
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.required(name: String): String = getString(name).also {
        check(it.isNotBlank() && it != "null") { "天气服务返回的数据不完整。" }
    }
}

internal fun validatedTemperature(value: String): String {
    check(value.toDoubleOrNull()?.isFinite() == true) { "天气服务返回的温度无效。" }
    return value
}
