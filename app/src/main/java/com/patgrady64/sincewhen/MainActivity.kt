package com.patgrady64.sincewhen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var adapter: MomentAdapter
    var moments = mutableListOf<Moment>()
    lateinit var header: TextView
    private val countdownHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
            countdownHandler.postDelayed(this, 1000)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Backup was restored! Reload memory arrays, adapt views, and redraw interfaces
            loadMoments()
            adapter.notifyDataSetChanged()
            updateHeader()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        header = findViewById(R.id.txtHeaderSummary)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        loadMoments()
        updateHeader()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null

        // 1. Updated Adapter Initialization: Passes the drag command up to our touch helper
        adapter = MomentAdapter(moments) { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }
        recyclerView.adapter = adapter

        // 2. Updated Touch Helper: Rejects automatic tracking to stop glitchy scrolling interferences
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            // TURN THIS OFF: Stops the layout from picking up items on random long-presses
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    countdownHandler.removeCallbacks(countdownRunnable)
                }
            }

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {

                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition

                if (from < to) {
                    for (i in from until to) {
                        Collections.swap(moments, i, i + 1)
                    }
                } else {
                    for (i in from downTo to + 1) {
                        Collections.swap(moments, i, i - 1)
                    }
                }

                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            // Saves the list order to disk ONLY when the user physically lets go of the item
            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                saveMoments()

                countdownHandler.post(countdownRunnable)
            }
        })

        // Attach the optimized handle-driven config to your layout
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Wire up your Floating Action Button for adding new moments
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showMomentDialog(null)
        }

        // Wire up your settings gear icon to launch the backup system panel
        findViewById<android.widget.ImageButton>(R.id.btnSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        // Kickstart the precise background midnight widget updating routine
        WidgetUpdateWorker.scheduleMidnightUpdate(this)
    }

    override fun onResume() {
        super.onResume()
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    fun updateHeader() {
        header.text = if (moments.isEmpty()) "Start tracking your moments!"
        else "Tracking ${moments.size} moments"
    }

    fun showMomentDialog(momentToEdit: Moment?) {
        val view = layoutInflater.inflate(R.layout.dialog_new_moment, null)

        val etName = view.findViewById<TextInputEditText>(R.id.etMomentName)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnPickDate)
        val btnToday = view.findViewById<MaterialButton>(R.id.btnToday)
        val btnCalculate = view.findViewById<MaterialButton>(R.id.btnCalculate)

        val layoutCalculator = view.findViewById<android.widget.LinearLayout>(R.id.layoutCalculator)
        val etCalcYears = view.findViewById<TextInputEditText>(R.id.etCalcYears)
        val etCalcMonths = view.findViewById<TextInputEditText>(R.id.etCalcMonths)
        val etCalcDays = view.findViewById<TextInputEditText>(R.id.etCalcDays)
        val txtCalcResult = view.findViewById<TextView>(R.id.txtCalcResult)

        // Tracks the base start date (cleared of time drift)
        var baseTimestamp = momentToEdit?.timestamp ?: System.currentTimeMillis()
        // Tracks the final output timestamp modified by inputs
        var finalTimestamp = baseTimestamp

        if (momentToEdit != null) {
            etName.setText(momentToEdit.title)
            val cal = Calendar.getInstance().apply { timeInMillis = baseTimestamp }
            btnDate.text = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.YEAR)}"
        }

        // Calculation processing engine
        val applyCalculationMath = {
            val yearsOffset = etCalcYears.text.toString().toIntOrNull() ?: 0
            val monthsOffset = etCalcMonths.text.toString().toIntOrNull() ?: 0
            val daysOffset = etCalcDays.text.toString().toIntOrNull() ?: 0

            val calcCalendar = Calendar.getInstance().apply {
                timeInMillis = baseTimestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            calcCalendar.add(Calendar.YEAR, yearsOffset)
            calcCalendar.add(Calendar.MONTH, monthsOffset)
            calcCalendar.add(Calendar.DAY_OF_MONTH, daysOffset)

            finalTimestamp = calcCalendar.timeInMillis

            val displayMonth = calcCalendar.get(Calendar.MONTH) + 1
            val displayDay = calcCalendar.get(Calendar.DAY_OF_MONTH)
            val displayYear = calcCalendar.get(Calendar.YEAR)
            txtCalcResult.text = "Target Date: $displayMonth/$displayDay/$displayYear"
            btnDate.text = "$displayMonth/$displayDay/$displayYear"
        }

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { applyCalculationMath() }
        }

        etCalcYears.addTextChangedListener(textWatcher)
        etCalcMonths.addTextChangedListener(textWatcher)
        etCalcDays.addTextChangedListener(textWatcher)

        btnDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(baseTimestamp)
                .build()

            picker.addOnPositiveButtonClickListener { selectedTime ->
                val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selectedTime }
                val localCal = Calendar.getInstance().apply {
                    set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                baseTimestamp = localCal.timeInMillis
                finalTimestamp = baseTimestamp

                btnDate.text = "${localCal.get(Calendar.MONTH) + 1}/${localCal.get(Calendar.DAY_OF_MONTH)}/${localCal.get(Calendar.YEAR)}"

                if (layoutCalculator.visibility == android.view.View.VISIBLE) {
                    applyCalculationMath()
                }
            }
            picker.show(supportFragmentManager, "DATE_PICKER")
        }

        btnToday.setOnClickListener {
            val localCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            baseTimestamp = localCal.timeInMillis
            finalTimestamp = baseTimestamp
            btnDate.text = "Today"

            etCalcYears.setText("0")
            etCalcMonths.setText("0")
            etCalcDays.setText("0")

            if (layoutCalculator.visibility == android.view.View.VISIBLE) {
                applyCalculationMath()
            }
        }

        btnCalculate.setOnClickListener {
            if (layoutCalculator.visibility == android.view.View.GONE) {
                layoutCalculator.visibility = android.view.View.VISIBLE
                applyCalculationMath()
            } else {
                layoutCalculator.visibility = android.view.View.GONE
            }
        }

        val builder = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val title = etName.text.toString().trim()
                if (title.isNotEmpty()) {
                    if (momentToEdit == null) {
                        moments.add(Moment(title = title, timestamp = finalTimestamp))
                    } else {
                        momentToEdit.title = title
                        momentToEdit.timestamp = finalTimestamp
                    }
                    adapter.notifyDataSetChanged()
                    saveMoments()
                    updateHeader()
                }
            }
            .setNegativeButton("Cancel", null)

        if (momentToEdit != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Tracker")
                    .setMessage("Are you sure you want to delete '${momentToEdit.title}'?")
                    .setPositiveButton("Yes") { _, _ ->
                        moments.remove(momentToEdit)
                        adapter.notifyDataSetChanged()
                        saveMoments()
                        updateHeader()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }
        builder.show()
    }

    private fun saveMoments() {
        getSharedPreferences("Prefs", MODE_PRIVATE).edit {
            putString("list", Gson().toJson(moments))
        }

        // Sync local adjustments out to the widget interface view provider
        UpcomingWidgetProvider.refreshWidget(this)
    }

    private fun loadMoments() {
        val json = getSharedPreferences("Prefs", MODE_PRIVATE)
            .getString("list", "[]") ?: "[]"

        val type = object : TypeToken<MutableList<Moment>>() {}.type

        moments.clear()
        moments.addAll(
            Gson().fromJson(json, type)
        )
    }

}