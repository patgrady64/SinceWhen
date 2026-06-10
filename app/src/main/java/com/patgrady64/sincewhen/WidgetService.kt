package com.patgrady64.sincewhen

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return UpcomingRemoteViewsFactory(this.applicationContext)
    }
}

class UpcomingRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var upcomingMoments: List<Moment> = listOf()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Read current tracking data from shared preferences
        val json = context.getSharedPreferences("Prefs", Context.MODE_PRIVATE).getString("list", null)
        if (json != null) {
            val allMoments: List<Moment> = Gson().fromJson(json, object : TypeToken<MutableList<Moment>>() {}.type)
            val now = System.currentTimeMillis()

            // Filter for future dates, ignoring events happening today
            upcomingMoments = allMoments.filter { it.timestamp > now && !isSameDay(it.timestamp, now) }
                .sortedBy { it.timestamp } // Show closest events first
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = upcomingMoments.size

    // Inside WidgetService.kt -> override fun getViewAt(position: Int): RemoteViews
    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item_row)
        val moment = upcomingMoments[position]

        // Format Date string: MM/dd/yy (e.g., 6/11/26)
        val cal = Calendar.getInstance().apply { timeInMillis = moment.timestamp }
        val dateStr = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.YEAR).toString().takeLast(2)}"

        val now = System.currentTimeMillis()
        val diff = moment.timestamp - now

        // 1. Calculate whole days using simple truncation
        var totalDays = diff / (1000 * 60 * 60 * 24)

        // 2. Check if there's any remaining fractional time left over
        // (e.g., hours, minutes, or seconds remaining in that partial day)
        val remainder = diff % (1000 * 60 * 60 * 24)

        // 3. If there is ANY time remaining, round it up to the next full day
        if (remainder > 0) {
            totalDays += 1
        }

        // Double check: If it calculates to 0 but it's technically a future date, force it to 1 day
        if (totalDays == 0L && diff > 0) {
            totalDays = 1
        }

        // Text composition: 6/11/26 Food Stamps (1 days)
        views.setTextViewText(R.id.txtWidgetItem, "$dateStr ${moment.title} ($totalDays days)")

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}