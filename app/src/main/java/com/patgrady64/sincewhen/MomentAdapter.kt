package com.patgrady64.sincewhen

import android.graphics.Color
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class MomentAdapter(private val moments: MutableList<Moment>) : RecyclerView.Adapter<MomentAdapter.MomentViewHolder>() {

    class MomentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtDuration: TextView = view.findViewById(R.id.txtDuration)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MomentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_moment, parent, false)
        return MomentViewHolder(view)
    }

    override fun onBindViewHolder(holder: MomentViewHolder, position: Int) {
        val moment = moments[position]
        holder.txtTitle.text = moment.title

        // 1. Interpret the database timestamp strictly as UTC midnight
        val utcZone = ZoneId.of("UTC")
        val eventDate = Instant.ofEpochMilli(moment.timestamp).atZone(utcZone).toLocalDate()

        // 2. Get the current calendar date based on the device's local time zone
        val targetZone = ZoneId.systemDefault()
        val todayDate = LocalDate.now(targetZone)

        // 3. Calculate exact calendar days between today and the event
        // Positive = Future, Zero = Today, Negative = Past
        val daysBetween = ChronoUnit.DAYS.between(todayDate, eventDate)

        val now = System.currentTimeMillis()

        // Setup the text display dynamically using the safe day calculations
        when {
            daysBetween == 0L -> {
                holder.txtDuration.text = "Today"
                holder.cardView.setCardBackgroundColor(Color.parseColor("#2E7D32")) // Green
            }
            daysBetween > 0L -> {
                holder.txtDuration.text = "Countdown: ${getCountdownString(moment.timestamp)}"
                holder.cardView.setCardBackgroundColor(Color.parseColor("#FF6F00")) // Orange
            }
            else -> {
                holder.txtDuration.text = "For ${getPastRelativeString(moment.timestamp, now)}"
                holder.cardView.setCardBackgroundColor(Color.parseColor("#1A237E")) // Blue
            }
        }

        // EDIT BUTTON
        holder.btnEdit.setOnClickListener {
            holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            (holder.itemView.context as? MainActivity)?.showMomentDialog(moment)
        }
    }

    override fun getItemCount(): Int {
        return moments.size
    }

    // Traditional mathematical calculation for past days
    private fun getPastRelativeString(startTimestamp: Long, endTimestamp: Long): String {
        val diff = endTimestamp - startTimestamp
        val totalDays = diff / (1000 * 60 * 60 * 24)

        val years = totalDays / 365
        val months = (totalDays % 365) / 30
        val days = (totalDays % 365) % 30

        val parts = mutableListOf<String>()
        if (years > 0) parts.add("${years}y")
        if (months > 0) parts.add("${months}m")
        if (days > 0) parts.add("${days}d")

        val brokenDownTime = if (parts.isEmpty()) "0d" else parts.joinToString(" ")

        // Split with a newline exactly before the total days count
        return "$brokenDownTime\n($totalDays days)"
    }

    // Standard countdown string formatting
    private fun getCountdownString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now

        val totalDays = diff / (1000 * 60 * 60 * 24)
        val hours = (diff / (1000 * 60 * 60)) % 24
        val minutes = (diff / (1000 * 60)) % 60
        val seconds = (diff / 1000) % 60

        return "${totalDays}d ${hours}h ${minutes}m ${seconds}s"
    }
}