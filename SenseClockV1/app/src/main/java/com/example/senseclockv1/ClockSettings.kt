package com.example.senseclockv1

import java.net.URI
import java.util.Locale

enum class ClockTheme(val label: String) {
    Default("Default · 默认"),
    Aluminum("Aluminum · 铝质"),
    Concrete("Concrete · 混凝土"),
    Graphite("Graphite · 铅笔画"),
    Matte("Matte · 哑光黑"),
    Steel("Steel · 钢铁")
}

data class ApiConfig(val host: String, val key: String) {
    val isConfigured: Boolean get() = host.isNotEmpty() && key.isNotEmpty()

    companion object {
        fun parse(hostInput: String, keyInput: String): ApiConfig {
            val host = hostInput.trim()
            val key = keyInput.trim()
            if (host.isEmpty() && key.isEmpty()) return ApiConfig("", "")
            require(host.isNotEmpty() && key.isNotEmpty()) {
                "请同时填写 API HOST 和 API KEY，或同时清空以停用天气。"
            }
            val uri = try {
                URI(if (host.contains("://")) host else "https://$host")
            } catch (_: Exception) {
                throw IllegalArgumentException("API HOST 格式不正确，请填写和风天气控制台提供的域名。")
            }
            val domain = uri.host.orEmpty().lowercase(Locale.ROOT)
            require(uri.scheme.equals("https", ignoreCase = true) &&
                uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null &&
                uri.rawFragment == null && uri.path.orEmpty() in listOf("", "/") &&
                domain.length <= 253 && domain.split('.').size >= 2 &&
                domain.split('.').all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) }
            ) { "API HOST 只需填写域名，可带 https://，不要填写接口路径、端口或查询参数。" }
            require(key.all { it.code in 33..126 }) { "API KEY 不能包含空格、换行或中文字符。" }
            return ApiConfig(domain, key)
        }
    }
}

internal data class IpCity(val province: String, val city: String)

internal fun parseIpCity(text: String): IpCity {
    val match = Regex("当前\\s*IP\\s*[:：]\\s*[\\da-fA-F:.]+\\s+来自于\\s*[:：]\\s*(.+)").find(text)
        ?: error("IP 定位返回格式无法识别。")
    val parts = match.groupValues[1].trim().split(Regex("\\s+"))
    check(parts.size >= 3 && parts[0] == "中国") { "当前仅支持中国城市的 IP 定位。" }
    return IpCity(parts[1], parts[2])
}

internal fun normalizeRegion(name: String): String = name.trim().replace(
    Regex("(?:壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市|地区)$"), ""
)
