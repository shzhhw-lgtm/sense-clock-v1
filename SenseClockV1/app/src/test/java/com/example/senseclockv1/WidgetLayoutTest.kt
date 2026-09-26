package com.example.senseclockv1

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class WidgetLayoutTest {
    @Test
    fun timeFormattingMatchesWebIncludingSingleDigitHoursAndMidnight() {
        assertEquals(0, WidgetLayout.hour(0, true))
        assertEquals(12, WidgetLayout.hour(0, false))
        assertEquals(12, WidgetLayout.hour(12, false))
        assertEquals(1, WidgetLayout.hour(13, false))
        assertEquals(23, WidgetLayout.hour(23, true))
        assertEquals(60_000L, WidgetLayout.nextMinute(59_999L))
        assertEquals(120_000L, WidgetLayout.nextMinute(60_000L))
        assertEquals(86_400_000L, WidgetLayout.nextMinute(86_399_999L))
        assertEquals("9 月 24 日, 周四", WidgetLayout.date(GregorianCalendar(2026, Calendar.SEPTEMBER, 24)))
    }

    @Test
    fun weatherMappingPreservesDayNightCloudThresholdAndFallback() {
        assertEquals("weather_00", WidgetLayout.weatherImage("100", null))
        assertEquals("weather_60", WidgetLayout.weatherImage("150", null))
        assertEquals("weather_03", WidgetLayout.weatherImage("103", 49))
        assertEquals("weather_04", WidgetLayout.weatherImage("103", 50))
        assertEquals("weather_64", WidgetLayout.weatherImage("153", null))
        assertEquals("weather_63", WidgetLayout.weatherImage("153", 0))
        assertEquals("weather_68", WidgetLayout.weatherImage("457", null))
        assertEquals("unknown", WidgetLayout.weatherImage("new-code", null))
        assertEquals("none", WidgetLayout.weatherImage("none", null))
    }
}
