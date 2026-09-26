package com.example.senseclockv1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ClockSettingsTest {
    @Test
    fun temperaturesMustBeFiniteBeforeCreatingANewSnapshot() {
        listOf("--", "", "null", "NaN", "Infinity", "-Infinity", "1e999", "晴").forEach {
            assertThrows(IllegalStateException::class.java) { validatedTemperature(it) }
        }
        listOf("0", "-12", "28", "-2.5", "30.5").forEach {
            assertEquals(it, validatedTemperature(it))
        }
    }

    @Test
    fun apiConfigNormalizesHostAndAllowsDisablingWeather() {
        assertEquals(ApiConfig("test.qweatherapi.com", "key123"),
            ApiConfig.parse(" HTTPS://TEST.qweatherapi.com/ ", " key123 "))
        assertFalse(ApiConfig.parse("", "").isConfigured)
        assertEquals("test.qweatherapi.com", ApiConfig.parse("test.qweatherapi.com", "key").host)
    }

    @Test
    fun apiConfigRejectsPartialCredentialsAndNonHostUrls() {
        listOf("", "http://test.com", "https://test.com/v7/weather/now", "test.com?key=secret",
            "user@test.com", "test.com:443", "test.com#fragment", "-bad.com", "test..com").forEach {
            assertThrows("Should reject $it", IllegalArgumentException::class.java) {
                ApiConfig.parse(it, "key")
            }
        }
        assertThrows(IllegalArgumentException::class.java) { ApiConfig.parse("test.com", "") }
        assertThrows(IllegalArgumentException::class.java) { ApiConfig.parse("test.com", "a\nb") }
    }

    @Test
    fun ipLocationRequiresChineseCityAndNormalizesAdministrativeNames() {
        assertEquals(IpCity("广东省", "深圳市"),
            parseIpCity("当前 IP：1.2.3.4  来自于：中国 广东省 深圳市 电信"))
        assertEquals("广西", normalizeRegion("广西壮族自治区"))
        assertEquals("深圳", normalizeRegion("深圳市"))
        assertThrows(IllegalStateException::class.java) { parseIpCity("返回数据异常") }
        assertThrows(IllegalStateException::class.java) {
            parseIpCity("当前 IP：1.2.3.4 来自于：美国 加利福尼亚 洛杉矶")
        }
    }
}
