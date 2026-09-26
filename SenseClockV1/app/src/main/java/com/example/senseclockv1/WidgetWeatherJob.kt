package com.example.senseclockv1

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WidgetWeatherJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            try {
                if (ClockWidgetProvider.ids(this@WidgetWeatherJob).isNotEmpty() &&
                    WeatherStore.config(this@WidgetWeatherJob).isConfigured) {
                    WeatherStore.refresh(this@WidgetWeatherJob)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep usable cached weather; retry at the next scheduled job or manual refresh.
                Log.w("SenseClockWidget", "Weather refresh unavailable; retaining valid cache")
            } finally {
                if (isActive) {
                    // WeatherStore notifies widgets when the snapshot or refresh error changes.
                    running.remove(params.jobId)
                    jobFinished(params, false)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PERIODIC_ID = 4101
        private const val INITIAL_ID = 4102

        fun schedule(context: Context, immediate: Boolean = false) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (ClockWidgetProvider.ids(context).isEmpty() || !WeatherStore.config(context).isConfigured) {
                cancel(context)
                return
            }
            val service = ComponentName(context, WidgetWeatherJob::class.java)
            val interval = WeatherPreferences.load(context).jobIntervalMillis
            if (scheduler.getPendingJob(PERIODIC_ID)?.intervalMillis != interval) {
                scheduler.schedule(JobInfo.Builder(PERIODIC_ID, service)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(interval).setPersisted(true).build())
            }
            if (immediate && WeatherStore.needsRefresh(context) &&
                scheduler.getPendingJob(INITIAL_ID) == null) {
                scheduler.schedule(JobInfo.Builder(INITIAL_ID, service)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())
            }
        }

        fun cancel(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            scheduler.cancel(PERIODIC_ID)
            scheduler.cancel(INITIAL_ID)
        }
    }
}
