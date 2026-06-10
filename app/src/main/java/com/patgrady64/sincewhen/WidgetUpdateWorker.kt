package com.patgrady64.sincewhen

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // 1. Force the widget views to refresh their data list
        UpcomingWidgetProvider.refreshWidget(applicationContext)

        // 2. Schedule the next update so it loops precisely every midnight
        scheduleMidnightUpdate(applicationContext)

        return Result.success()
    }

    companion object {
        fun scheduleMidnightUpdate(context: Context) {
            // Calculate how many milliseconds remain until the upcoming midnight
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1) // Set target to tomorrow at 00:00
            }

            val initialDelay = dueDate.timeInMillis - currentDate.timeInMillis

            // Define the single execution request with the delay constraint
            val midnightWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("MIDNIGHT_WIDGET_UPDATE")
                .build()

            // Enqueue uniquely so it doesn't duplicate if called multiple times
            WorkManager.getInstance(context).enqueueUniqueWork(
                "MidnightWidgetUpdate",
                ExistingWorkPolicy.REPLACE, // Replace old timer with the fresh calculated one
                midnightWorkRequest
            )
        }
    }
}