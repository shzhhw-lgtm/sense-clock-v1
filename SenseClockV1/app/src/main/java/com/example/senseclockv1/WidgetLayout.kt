package com.example.senseclockv1

import java.util.Calendar

/** Coordinates are CSS pixels from clock_demo_v16_1080p.html, not Android dp. */
internal object WidgetLayout {
    const val WIDTH = 1200f
    const val HEIGHT = 800f

    fun hour(hourOfDay: Int, is24Hour: Boolean): Int =
        if (is24Hour) hourOfDay else (hourOfDay % 12).let { if (it == 0) 12 else it }

    fun nextMinute(now: Long): Long = now - now % 60_000L + 60_000L

    fun date(calendar: Calendar): String {
        val week = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return "${calendar.get(Calendar.MONTH) + 1} 月 ${calendar.get(Calendar.DAY_OF_MONTH)} 日, " +
            week[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun weatherImage(code: String, cloud: Int?): String = when (code) {
        "100" -> "weather_00"
        "101" -> "weather_01"
        "102" -> "weather_02"
        "103" -> if (cloud != null && cloud < 50) "weather_03" else "weather_04"
        "104" -> "weather_06"
        "150" -> "weather_60"
        "151" -> "weather_61"
        "152" -> "weather_62"
        "153" -> if (cloud != null && cloud < 50) "weather_63" else "weather_64"
        "300" -> "weather_07"
        "301" -> "weather_08"
        "302", "303" -> "weather_09"
        "304" -> "weather_10"
        "305", "309", "314", "399" -> "weather_11"
        "306", "307", "308", "310", "311", "312", "315", "316", "317", "318" -> "weather_12"
        "313" -> "weather_13"
        "350" -> "weather_66"
        "351" -> "weather_67"
        "400", "401", "402", "403", "408", "409", "410", "499" -> "weather_14"
        "404", "405", "406", "456" -> "weather_15"
        "407" -> "weather_16"
        "457" -> "weather_68"
        "500", "501", "509", "510", "514", "515" -> "weather_17"
        "502", "511", "512", "513" -> "weather_18"
        "503" -> "weather_19"
        "504", "507", "508" -> "weather_20"
        "900" -> "weather_21"
        "901" -> "weather_22"
        "none" -> "none"
        else -> "unknown"
    }
}
