package com.example.senseclockv1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draw the web layout at rest. The widget host receives one transparent, static bitmap. */
internal class ClockWidgetRenderer(context: Context) {
    private val assets = context.applicationContext.assets
    private val images = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val infoTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.createFromAsset(assets, "fonts/DroidSansFallback.ttf")
    }
    private val temperatureTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.createFromAsset(assets, "fonts/Roboto-Regular.ttf")
    }

    fun render(
        width: Int, theme: ClockTheme, is24Hour: Boolean,
        weather: WeatherSnapshot?, now: Calendar = Calendar.getInstance(), weatherStatus: String? = null
    ): Bitmap {
        val height = (width * WidgetLayout.HEIGHT / WidgetLayout.WIDTH).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / WidgetLayout.WIDTH, height / WidgetLayout.HEIGHT)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        tile(canvas, theme, true, WidgetLayout.hour(hour, is24Hour),
            if (is24Hour) null else if (hour < 12) "am" else "pm")
        tile(canvas, theme, false, now.get(Calendar.MINUTE), null)

        // .text_info_area: left=100, top=575. Its image is centered independently.
        image(canvas, "themes/${theme.name}/bg_text_info.png", 120f, 577.5f, 960f, 175f)
        text(canvas, WidgetLayout.date(now), 151f, 587f, 38f, maxWidth = 510f)
        text(canvas, weather?.city ?: "我的位置", 151f, 629f, 52f, maxWidth = 460f)
        text(canvas, weatherStatus ?: weather?.description ?: "无可用天气数据", 151f, 689f, 39f, maxWidth = 460f)
        val temperatureReady = weather != null && listOf(weather.temperature, weather.high, weather.low)
            .all { it.toDoubleOrNull()?.isFinite() == true }
        if (temperatureReady) {
            text(canvas, weather.temperature, 980f, 572f, 100f, rightAligned = true,
                textPaint = temperatureTextPaint)
            text(canvas, weather.high, 897f, 685f, 45f, rightAligned = true,
                textPaint = temperatureTextPaint)
            text(canvas, "/", 960f, 681f, 45f, rightAligned = true,
                textPaint = temperatureTextPaint)
            text(canvas, weather.low, 1020f, 685f, 45f, rightAligned = true,
                textPaint = temperatureTextPaint)
            image(canvas, "themes/degree_c_l.png", 987f, 594f, 59f, 100f)
            image(canvas, "themes/degree_c_m.png", 899f, 693f, 30f, 48f)
            image(canvas, "themes/degree_c_m.png", 1022f, 693f, 30f, 48f)
        }
        // z-index:20; PNG already includes the original transparent 720x720 stage.
        image(canvas, "weather/${WidgetLayout.weatherImage(weather?.icon ?: "none", weather?.cloud)}.png",
            235f, 190f, 720f, 720f)
        return trimTransparentBorder(bitmap)
    }

    /** Keep the original coordinates, but do not send the web page's empty margins to the launcher. */
    private fun trimTransparentBorder(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        for (y in 0 until source.height) {
            val row = y * source.width
            for (x in 0 until source.width) {
                // Include even faint shadows and weather glows; only fully transparent pixels are empty.
                if (pixels[row + x] ushr 24 != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < left) return source

        // A small, resolution-independent safety margin (12px in the original 1200px canvas).
        val padding = (12f * source.width / WidgetLayout.WIDTH).roundToInt().coerceAtLeast(1)
        left = (left - padding).coerceAtLeast(0)
        top = (top - padding).coerceAtLeast(0)
        right = (right + padding + 1).coerceAtMost(source.width)
        bottom = (bottom + padding + 1).coerceAtMost(source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        if (cropped !== source) source.recycle()
        return cropped
    }

    private fun tile(canvas: Canvas, theme: ClockTheme, hour: Boolean, value: Int, period: String?) {
        val x = if (hour) 110f else 600f
        val prefix = "themes/${theme.name}/"
        val name = if (hour) "hour" else "minute"
        val imageX = if (hour) -6f else 16f

        fun half(top: Boolean, angle: Double, scaleY: Float, alpha: Int, digits: Boolean) {
            val h = if (top) 234f else 240f
            canvas.save()
            canvas.translate(x, if (top) 65f else 299f)
            if (angle != 0.0 || scaleY != 1f) {
                canvas.concat(perspective(h, if (top) h else 0f, angle, scaleY))
            }
            canvas.clipRect(0f, 0f, 490f, h)
            val suffix = if (top) "t" else "b"
            image(canvas, "$prefix${name}_$suffix.png", imageX, if (top) 3f else -3f, 480f, h, alpha)
            if (digits) {
                val offset = if (hour) -10f else 10f
                val single = hour && value < 10
                val digitY = if (top) 98f else 0f
                val digitHeight = if (top) 136f else 164f
                if (!single) image(canvas, "$prefix${value / 10}_$suffix.png",
                    35f + offset, digitY, 210f, digitHeight)
                // .num_ones clips its image's -3px margin; single hours translate the container -50%.
                val onesX = 245f + offset - if (single) 105f else 0f
                canvas.save()
                canvas.clipRect(onesX, digitY, onesX + 210f, digitY + digitHeight)
                image(canvas, "$prefix${value % 10}_$suffix.png", onesX - 3f, digitY, 210f, digitHeight)
                canvas.restore()
                if (!top && period != null) image(canvas, "${prefix}icon_$period.png", 26f, 88f, 110f, 60f)
            }
            canvas.restore()
        }

        // Keep the CSS resting stack and perspective, without creating animation frames.
        half(true, 16.0, 1.18f, 153, false) // base_flip_t
        half(false, -3.0, 1.08f, 255, false) // base_flip_b
        half(false, 0.0, 1f, 255, true) // old_flip_b
        half(true, 3.0, 1.08f, 255, true) // new_flip_t
        half(true, 0.0, 1f, 255, true) // old_flip_t
    }

    private fun perspective(height: Float, originY: Float, degrees: Double, scaleY: Float): Matrix {
        val angle = Math.toRadians(degrees)
        val source = floatArrayOf(0f, 0f, 490f, 0f, 490f, height, 0f, height)
        val target = FloatArray(8)
        for (i in 0..3) {
            val dx = source[i * 2] - 245f
            val dy = source[i * 2 + 1] - originY
            val depth = 1f - (dy * sin(angle) / 1000).toFloat()
            target[i * 2] = 245f + dx / depth
            target[i * 2 + 1] = originY + (dy * cos(angle) * scaleY / depth).toFloat()
        }
        return Matrix().apply { setPolyToPoly(source, 0, target, 0, 4) }
    }

    private fun image(canvas: Canvas, path: String, x: Float, y: Float, w: Float, h: Float, alpha: Int = 255) {
        val bitmap = images[path] ?: assets.open(path).use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "Cannot decode widget asset: $path" }
        }.also { images.put(path, it) }
        paint.alpha = alpha
        canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), paint)
        paint.alpha = 255
    }

    private fun text(canvas: Canvas, value: String, x: Float, top: Float, size: Float,
        rightAligned: Boolean = false, maxWidth: Float = 500f,
        textPaint: TextPaint = infoTextPaint) {
        textPaint.textSize = size
        textPaint.color = if (rightAligned) Color.rgb(235, 233, 233) else Color.WHITE
        textPaint.textAlign = if (rightAligned) Paint.Align.RIGHT else Paint.Align.LEFT
        val metrics = textPaint.fontMetrics
        val baseline = top + (size * 1.2f - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        canvas.drawText(TextUtils.ellipsize(value, textPaint, maxWidth, TextUtils.TruncateAt.END).toString(),
            x, baseline, textPaint)
    }
}
