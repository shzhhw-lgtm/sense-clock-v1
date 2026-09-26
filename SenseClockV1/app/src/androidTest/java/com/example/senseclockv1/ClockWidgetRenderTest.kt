package com.example.senseclockv1

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar

@RunWith(AndroidJUnit4::class)
class ClockWidgetRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun weatherDetailsAndForecastSurviveCacheAndOldCacheRemainsReadable() {
        val snapshot = WeatherSnapshot("深圳", "28", "晴", "31", "25", "2026-09-26", 123L,
            details = mapOf("humidity" to "80", "feelsLike" to "30", "obsTime" to "2026-09-26T10:00+08:00"),
            forecast = listOf(
                DailyForecast("2026-09-26", "晴", "多云", "25", "31", "100", "151"),
                DailyForecast("2026-09-27", "小雨", "小雨", "24", "29"),
                DailyForecast("2026-09-28", "阴", "晴", "23", "30")
            ))
        assertEquals(snapshot, WeatherSnapshot.fromJson(snapshot.toJson()))
        val withoutIcons = org.json.JSONObject(snapshot.toJson()).apply {
            getJSONArray("forecast").getJSONObject(0).apply {
                remove("iconDay")
                remove("iconNight")
            }
        }
        assertEquals(snapshot.forecast.first().copy(iconDay = "999", iconNight = "999"),
            WeatherSnapshot.fromJson(withoutIcons.toString())?.forecast?.first())
        val legacy = org.json.JSONObject(snapshot.toJson()).apply {
            remove("details")
            remove("forecast")
        }
        assertEquals(snapshot.copy(details = emptyMap(), forecast = emptyList()),
            WeatherSnapshot.fromJson(legacy.toString()))
    }

    @Test
    fun staticArtworkRendersAllThemesAndRemoteViewsAcceptTheBitmap() {
        val painter = ClockWidgetRenderer(context)
        val now = GregorianCalendar(2026, Calendar.SEPTEMBER, 24, 10, 8)
        val weather = WeatherSnapshot("深圳", "28", "晴", "31", "25", beijingDate(),
            System.currentTimeMillis(), "100", 0)
        for (theme in ClockTheme.entries) {
            val bitmap = painter.render(1200, theme, false, weather, now)
            assertTrue("Transparent horizontal margins should be trimmed", bitmap.width in 1 until 1200)
            assertTrue("Transparent vertical margins should be trimmed", bitmap.height in 1 until 800)
            assertEquals("Widget background must remain transparent", 0, Color.alpha(bitmap.getPixel(0, 0)))
            assertTrue("Clock panel missing for $theme", Color.alpha(bitmap.getPixel(180, 120)) > 0)
            assertTrue("Weather artwork missing",
                Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height * 2 / 3)) > 0)
            File(context.getExternalFilesDir(null), "widget-${theme.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.runOnMainSync {
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                views.setImageViewBitmap(R.id.widget_clock_image, bitmap)
                val view = views.apply(context, null) as ImageView
                view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 600, 400)
                assertNotNull(view.drawable)
            }
            bitmap.recycle()
        }
        val midnight = painter.render(600, ClockTheme.Default, true, null,
            GregorianCalendar(2026, Calendar.SEPTEMBER, 25, 0, 0))
        assertTrue(midnight.width in 1 until 600)
        assertTrue(midnight.height in 1 until 400)
        midnight.recycle()
    }

    @Test
    fun everyWeatherMappingHasStaticArtworkAndCachePreservesIconAndCloud() {
        val sample = WeatherSnapshot("深圳", "28", "晴间多云", "31", "25", beijingDate(),
            System.currentTimeMillis(), "153", 49)
        assertEquals(sample, WeatherSnapshot.fromJson(sample.toJson()))
        val stale = sample.copy(updatedAt = System.currentTimeMillis() - 25 * 60 * 60_000L)
        assertEquals("Expired weather must remain available for offline display", stale,
            WeatherSnapshot.fromJson(stale.toJson()))
        val assets = (0..1000).flatMap { code ->
            listOf(WidgetLayout.weatherImage(code.toString(), null), WidgetLayout.weatherImage(code.toString(), 0))
        }.toSet() + "none"
        for (name in assets) {
            context.assets.open("weather/$name.png").use {
                val bitmap = BitmapFactory.decodeStream(it)
                assertNotNull("Missing image $name", bitmap)
                bitmap.recycle()
            }
        }
    }
}
