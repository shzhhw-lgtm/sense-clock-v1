package com.example.senseclockv1

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAsync(context)
        WidgetWeatherJob.schedule(context, immediate = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateAsync(context)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
        WidgetWeatherJob.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                // Re-arm before rendering. A slow/failing bitmap render must not stop the clock chain.
                scheduleNextTick(context)
                updateAsync(context, scheduleAfterRender = false)
            }
            ACTION_REDRAW -> requestRedraw(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                // Wall-clock changes invalidate the previously calculated RTC trigger time.
                scheduleNextTick(context)
                updateAsync(context)
                WidgetWeatherJob.schedule(context, immediate = true)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun updateAsync(context: Context, scheduleAfterRender: Boolean = true) {
        val pending = goAsync()
        val app = context.applicationContext
        executor.execute {
            try {
                updateAll(app)
            } catch (error: Exception) {
                Log.e("SenseClockWidget", "Unable to render widget", error)
            } finally {
                try {
                    if (scheduleAfterRender) scheduleNextTick(app)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun requestRedraw(context: Context) {
        redrawRequested.set(true)
        if (!redrawRunning.compareAndSet(false, true)) return

        val pending = goAsync()
        executor.execute {
            try {
                do {
                    redrawRequested.set(false)
                    updateAll(context.applicationContext)
                } while (redrawRequested.get())
            } catch (error: Exception) {
                Log.e("SenseClockWidget", "Unable to render widget", error)
            } finally {
                redrawRunning.set(false)
                pending.finish()
                if (redrawRequested.get()) requestRedraw(context)
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "com.example.senseclockv1.WIDGET_MINUTE"
        private const val ACTION_REDRAW = "com.example.senseclockv1.WIDGET_REDRAW"
        private val executor = Executors.newSingleThreadExecutor()
        private val redrawRequested = AtomicBoolean(false)
        private val redrawRunning = AtomicBoolean(false)
        private var renderer: ClockWidgetRenderer? = null

        fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))

        fun requestUpdate(context: Context) {
            if (ids(context).isNotEmpty()) context.sendBroadcast(
                Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_REDRAW)
            )
        }

        fun exactUpdatesAllowed(context: Context): Boolean =
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        private fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = ids(context)
            if (ids.isEmpty()) return
            val prefs = context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
            val theme = ClockTheme.entries.firstOrNull { it.name == prefs.getString("theme", "Default") }
                ?: ClockTheme.Default
            val is24Hour = prefs.getBoolean("is_24_hour", true)
            val weather = WeatherStore.cached(context)
            val weatherStatus = if (weather != null && WeatherStore.cacheNotice(context) != null) {
                "缓存 · " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(weather.updatedAt))
            } else null
            val now = Calendar.getInstance()
            val painter = renderer ?: ClockWidgetRenderer(context).also { renderer = it }
            val metrics = context.resources.displayMetrics
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val dpWidth = maxOf(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300))
                // Stay below RemoteViews' screen-dependent bitmap memory cap, including small devices.
                val memoryWidth = sqrt(metrics.widthPixels.toDouble() * metrics.heightPixels * 1.5).toInt()
                val width = (dpWidth * metrics.density).roundToInt().coerceIn(1, minOf(1200, memoryWidth))
                val bitmap = painter.render(width, theme, is24Hour, weather, now, weatherStatus)
                val hour = WidgetLayout.hour(now.get(Calendar.HOUR_OF_DAY), is24Hour)
                val period = if (is24Hour) "" else if (now.get(Calendar.HOUR_OF_DAY) < 12) " AM" else " PM"
                val description = "$hour:${now.get(Calendar.MINUTE).toString().padStart(2, '0')}$period，" +
                    WidgetLayout.date(now) + (weather?.let { "，${it.city}，${it.description}，${it.temperature}摄氏度" } ?: "") +
                    (weatherStatus?.let { "，$it" } ?: "")
                val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
                    setImageViewBitmap(R.id.widget_clock_image, bitmap)
                    setContentDescription(R.id.widget_clock_image, description)
                    setOnClickPendingIntent(R.id.widget_clock_image, openApp)
                }
                try {
                    manager.updateAppWidget(id, views)
                } finally {
                    bitmap.recycle()
                }
            }
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1001, Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun scheduleNextTick(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java)
            val pending = tickIntent(context)
            if (ids(context).isEmpty()) { alarm.cancel(pending); return }
            val time = WidgetLayout.nextMinute(System.currentTimeMillis())
            if (exactUpdatesAllowed(context)) {
                try {
                    // The widget must be redrawn at the minute boundary, including after idle/doze.
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                    return
                } catch (_: SecurityException) {
                    // Permission can be revoked between checking and scheduling.
                }
            }
            // Without exact-alarm access Android may defer this. This is the closest permitted
            // fallback and is re-armed on every tick/time change.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
        }
    }
}
