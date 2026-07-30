package com.patgrady64.sincewhen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.patgrady64.sincewhen.theme.ThemeActivity
import com.patgrady64.sincewhen.theme.ThemeApplier
import com.patgrady64.sincewhen.theme.ThemeManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    private val calendarExecutor = Executors.newSingleThreadExecutor()
    private val calendarSyncManager by lazy { GoogleCalendarSyncManager(this) }

    // Document creation engine (Export Backup File)
    private val createBackupFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { writeBackupData(it) }
    }

    // Document selection engine (Import Backup File)
    private val importBackupFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { readAndRestoreBackup(it) }
    }

    private val themeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            ThemeApplier.apply(this)
        }

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasCalendarPermissions()) {
            chooseGoogleCalendar()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Calendar permission needed")
                .setMessage(
                    "SinceWhen needs Calendar access to find existing events and add or " +
                        "replace upcoming moments. No calendar changes were made."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val theme = findViewById<View>(R.id.rowTheme)

        ThemeApplier.apply(this)

        SettingsRow(findViewById(R.id.rowClearAll))
            .setTitle("Clear All Moments")
            .setSubtitle("Delete everything stored")
            .onClick { showClearDialog() }

        SettingsRow(findViewById(R.id.rowWidget))
            .setTitle("Widget")
            .setSubtitle("Refresh widgets")
            .onClick { refreshWidget() }

        SettingsRow(findViewById(R.id.rowTheme))
            .setTitle("Theme")
            .setSubtitle("Dark mode and colors")
            .onClick { themeManager() }

        SettingsRow(findViewById(R.id.rowGoogleCalendar))
            .setTitle("Add to Google Calendar")
            .setSubtitle("Review and select future moments")
            .onClick { startGoogleCalendarSync() }

        styleGoogleCalendarSettingsRow()

        SettingsRow(findViewById(R.id.rowExport))
            .setTitle("Export Backup")
            .setSubtitle("Save your moments to a file")
            .onClick { exportBackup() }

        SettingsRow(findViewById(R.id.rowImport))
            .setTitle("Import Backup")
            .setSubtitle("Restore from file")
            .onClick { importBackup() }

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener {
            finish()
        }

        theme.setOnClickListener {
            themeLauncher.launch(Intent(this, ThemeActivity::class.java))
        }
    }

    override fun onDestroy() {
        calendarExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun styleGoogleCalendarSettingsRow() {
        val currentTheme = ThemeManager.getTheme(this)
        findViewById<MaterialCardView>(R.id.calendarSettingsIconCard).apply {
            setCardBackgroundColor(ColorUtils.setAlphaComponent(currentTheme.accent, 35))
            strokeWidth = 0
        }
        findViewById<ImageView>(R.id.imgCalendarSetting).imageTintList =
            ColorStateList.valueOf(currentTheme.accent)
    }

    private fun themeManager() {
        startActivity(Intent(this, ThemeActivity::class.java))
    }

    private fun refreshWidget() {
        UpcomingWidgetProvider.refreshWidget(this)
    }

    private fun exportBackup() {
        createBackupFileLauncher.launch("SinceWhen_Backup.json")
    }

    private fun importBackup() {
        importBackupFileLauncher.launch(arrayOf("*/*"))
    }

    // -------------------------------------------------------------------------
    // Google Calendar sync
    // -------------------------------------------------------------------------

    private fun startGoogleCalendarSync() {
        if (hasCalendarPermissions()) {
            chooseGoogleCalendar()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Allow Calendar access?")
            .setMessage(
                "SinceWhen will read your calendars only to find duplicates, then add or " +
                    "update the upcoming moments you approve."
            )
            .setPositiveButton("Continue") { _, _ ->
                calendarPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasCalendarPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun chooseGoogleCalendar() {
        Toast.makeText(this, "Looking for Google calendars…", Toast.LENGTH_SHORT).show()

        calendarExecutor.execute {
            try {
                val calendars = calendarSyncManager.getWritableGoogleCalendars()

                runOnUiThread {
                    when {
                        calendars.isEmpty() -> showNoGoogleCalendarDialog()
                        calendars.size == 1 -> scanSelectedCalendar(calendars.first())
                        else -> showGoogleCalendarChoiceDialog(calendars)
                    }
                }
            } catch (e: Exception) {
                showCalendarError("Could not read calendars", e)
            }
        }
    }

    private fun showGoogleCalendarChoiceDialog(
        calendars: List<GoogleCalendarSyncManager.GoogleCalendar>
    ) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_google_calendar_choice, null, false)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val currentTheme = ThemeManager.getTheme(this)
        val root = dialogView.findViewById<View>(R.id.calendarChoiceSheetRoot)
        val container = dialogView.findViewById<LinearLayout>(R.id.calendarChoiceContainer)
        val countText = dialogView.findViewById<TextView>(R.id.txtCalendarChoiceCount)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.btnCancelCalendarChoice)

        countText.text =
            "${calendars.size} writable Google calendars available"

        applyGoogleCalendarChoiceTheme(dialogView)

        calendars.forEach { calendar ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_google_calendar_choice, container, false)
            val card = row.findViewById<MaterialCardView>(R.id.cardGoogleCalendarChoice)
            val iconCard = row.findViewById<MaterialCardView>(R.id.googleCalendarChoiceIconCard)
            val icon = row.findViewById<ImageView>(R.id.imgGoogleCalendarChoice)
            val title = row.findViewById<TextView>(R.id.txtGoogleCalendarChoiceName)
            val account = row.findViewById<TextView>(R.id.txtGoogleCalendarChoiceAccount)
            val primaryBadge = row.findViewById<TextView>(R.id.txtGoogleCalendarPrimaryBadge)
            val chevron = row.findViewById<ImageView>(R.id.imgGoogleCalendarChoiceChevron)

            title.text = calendar.displayName.ifBlank { "Google Calendar" }
            account.text = calendar.accountName
            primaryBadge.visibility = if (calendar.isPrimary) View.VISIBLE else View.GONE

            card.setCardBackgroundColor(currentTheme.card)
            card.strokeColor = currentTheme.cardStroke
            iconCard.setCardBackgroundColor(
                ColorUtils.setAlphaComponent(currentTheme.accent, 35)
            )
            icon.imageTintList = ColorStateList.valueOf(currentTheme.accent)
            chevron.imageTintList = ColorStateList.valueOf(currentTheme.textSecondary)
            title.setTextColor(currentTheme.textPrimary)
            account.setTextColor(currentTheme.textSecondary)
            primaryBadge.setTextColor(currentTheme.accent)
            primaryBadge.background = roundedBackground(
                color = ColorUtils.setAlphaComponent(currentTheme.accent, 32),
                radiusDp = 999f
            )

            card.setOnClickListener {
                dialog.dismiss()
                scanSelectedCalendar(calendar)
            }

            container.addView(row)
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val screenHeight = resources.displayMetrics.heightPixels
                val density = resources.displayMetrics.density
                val maxHeight = (screenHeight * 0.78f).toInt()
                val minHeight = (screenHeight * 0.48f).toInt()
                val estimatedHeight = ((235 + calendars.size * 102) * density).toInt()
                val targetHeight = estimatedHeight.coerceIn(minHeight, maxHeight)
                root.layoutParams = root.layoutParams.apply { height = targetHeight }
                root.requestLayout()
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
            dialog.window?.setDimAmount(0.6f)
        }

        dialog.show()
    }

    private fun applyGoogleCalendarChoiceTheme(dialogView: View) {
        val currentTheme = ThemeManager.getTheme(this)

        dialogView.findViewById<View>(R.id.calendarChoiceSheetRoot).background =
            roundedTopBackground(currentTheme.surface, 28f)
        dialogView.findViewById<View>(R.id.calendarChoiceDragHandle).background =
            roundedBackground(
                ColorUtils.setAlphaComponent(currentTheme.textSecondary, 120),
                999f
            )
        dialogView.findViewById<View>(R.id.calendarChoiceHeaderDivider)
            .setBackgroundColor(currentTheme.cardStroke)
        dialogView.findViewById<View>(R.id.calendarChoiceFooterDivider)
            .setBackgroundColor(currentTheme.cardStroke)

        dialogView.findViewById<MaterialCardView>(R.id.calendarChoiceHeaderIconCard).apply {
            setCardBackgroundColor(ColorUtils.setAlphaComponent(currentTheme.accent, 35))
            strokeWidth = 0
        }
        dialogView.findViewById<ImageView>(R.id.calendarChoiceHeaderIcon)
            .imageTintList = ColorStateList.valueOf(currentTheme.accent)

        dialogView.findViewById<TextView>(R.id.txtCalendarChoiceTitle)
            .setTextColor(currentTheme.textPrimary)
        dialogView.findViewById<TextView>(R.id.txtCalendarChoiceSubtitle)
            .setTextColor(currentTheme.textSecondary)
        dialogView.findViewById<TextView>(R.id.txtCalendarChoiceCount)
            .setTextColor(currentTheme.textSecondary)

        dialogView.findViewById<MaterialButton>(R.id.btnCancelCalendarChoice).apply {
            setTextColor(currentTheme.textPrimary)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeColor = ColorStateList.valueOf(currentTheme.cardStroke)
        }
    }

    private fun showNoGoogleCalendarDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("No writable Google calendar found")
            .setMessage(
                "Make sure a Google account is added to this phone and Calendar sync is enabled, " +
                    "then try again."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun scanSelectedCalendar(calendar: GoogleCalendarSyncManager.GoogleCalendar) {
        val allMoments = loadAllMoments()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val upcomingMoments = allMoments
            .filter { it.timestamp >= startOfToday }
            .sortedBy { it.timestamp }

        if (upcomingMoments.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Nothing to add")
                .setMessage("SinceWhen does not currently have any upcoming moments.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        Toast.makeText(this, "Checking for existing events…", Toast.LENGTH_SHORT).show()

        calendarExecutor.execute {
            try {
                val candidates = calendarSyncManager.scan(calendar.id, upcomingMoments)

                runOnUiThread {
                    showMomentSelectionDialog(
                        calendar = calendar,
                        allMoments = allMoments,
                        candidates = candidates
                    )
                }
            } catch (e: Exception) {
                showCalendarError("Could not check for existing events", e)
            }
        }
    }

    private fun showMomentSelectionDialog(
        calendar: GoogleCalendarSyncManager.GoogleCalendar,
        allMoments: MutableList<Moment>,
        candidates: List<GoogleCalendarSyncManager.SyncCandidate>
    ) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_calendar_moment_selection, null, false)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val currentTheme = ThemeManager.getTheme(this)
        val checkedItems = BooleanArray(candidates.size)
        val checkBoxes = mutableListOf<MaterialCheckBox>()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

        val root = dialogView.findViewById<View>(R.id.calendarSheetRoot)
        val container = dialogView.findViewById<LinearLayout>(R.id.momentSelectionContainer)
        val calendarName = dialogView.findViewById<TextView>(R.id.txtCalendarName)
        val selectionSummary = dialogView.findViewById<TextView>(R.id.txtSelectionSummary)
        val toggleAllButton = dialogView.findViewById<MaterialButton>(R.id.btnToggleAllMoments)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.btnCancelCalendarSelection)
        val addButton = dialogView.findViewById<MaterialButton>(R.id.btnAddSelectedMoments)

        calendarName.text = calendar.displayLabel
        dialogView.findViewById<TextView>(R.id.txtCalendarSheetSubtitle).text =
            "Select the future moments you want to add to this calendar."

        applyCalendarSelectionTheme(dialogView)

        fun updateSelectionUi() {
            val selectedCount = checkedItems.count { it }
            selectionSummary.text =
                "$selectedCount selected • ${candidates.size} upcoming"
            toggleAllButton.text =
                if (selectedCount == candidates.size) "Clear all" else "Select all"
            addButton.text =
                if (selectedCount == 0) "Add Selected" else "Add Selected ($selectedCount)"
            addButton.isEnabled = selectedCount > 0
            addButton.alpha = if (selectedCount > 0) 1f else 0.45f
        }

        candidates.forEachIndexed { index, candidate ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_calendar_moment_selection, container, false)
            val card = row.findViewById<MaterialCardView>(R.id.cardCalendarMoment)
            val checkBox = row.findViewById<MaterialCheckBox>(R.id.checkCalendarMoment)
            val title = row.findViewById<TextView>(R.id.txtCalendarMomentTitle)
            val date = row.findViewById<TextView>(R.id.txtCalendarMomentDate)
            val status = row.findViewById<TextView>(R.id.txtCalendarMomentStatus)

            title.text = candidate.moment.title
            date.text = dateFormat.format(Date(candidate.moment.timestamp))

            val alreadyExists = candidate.existingEvent != null
            status.text = if (alreadyExists) "Already in calendar" else "Ready to add"
            val statusColor = if (alreadyExists) currentTheme.warning else currentTheme.success
            status.setTextColor(statusColor)
            status.background = roundedBackground(
                color = ColorUtils.setAlphaComponent(statusColor, 38),
                radiusDp = 999f
            )

            card.setCardBackgroundColor(currentTheme.card)
            card.strokeColor = currentTheme.cardStroke
            title.setTextColor(currentTheme.textPrimary)
            date.setTextColor(currentTheme.textSecondary)
            checkBox.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(currentTheme.accent, currentTheme.textSecondary)
            )

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                checkedItems[index] = isChecked
                updateSelectionUi()
            }
            card.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }

            checkBoxes += checkBox
            container.addView(row)
        }

        toggleAllButton.setOnClickListener {
            val selectEverything = checkedItems.any { !it }
            checkBoxes.forEach { it.isChecked = selectEverything }
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        addButton.setOnClickListener {
            val selectedCandidates = candidates.filterIndexed { index, _ ->
                checkedItems[index]
            }
            if (selectedCandidates.isEmpty()) return@setOnClickListener

            dialog.dismiss()
            confirmExistingEvents(
                calendar = calendar,
                allMoments = allMoments,
                candidates = selectedCandidates
            )
        }

        updateSelectionUi()

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val targetHeight = (resources.displayMetrics.heightPixels * 0.88f).toInt()
                root.layoutParams = root.layoutParams.apply { height = targetHeight }
                root.requestLayout()
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
            dialog.window?.setDimAmount(0.6f)
        }

        dialog.show()
    }

    private fun applyCalendarSelectionTheme(dialogView: View) {
        val currentTheme = ThemeManager.getTheme(this)
        val accentTextColor = if (ColorUtils.calculateLuminance(currentTheme.accent) > 0.55) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        dialogView.findViewById<View>(R.id.calendarSheetRoot).background =
            roundedTopBackground(currentTheme.surface, 28f)
        dialogView.findViewById<View>(R.id.dragHandle).background =
            roundedBackground(
                ColorUtils.setAlphaComponent(currentTheme.textSecondary, 120),
                999f
            )
        dialogView.findViewById<View>(R.id.headerDivider)
            .setBackgroundColor(currentTheme.cardStroke)
        dialogView.findViewById<View>(R.id.footerDivider)
            .setBackgroundColor(currentTheme.cardStroke)

        dialogView.findViewById<MaterialCardView>(R.id.calendarIconCard).apply {
            setCardBackgroundColor(ColorUtils.setAlphaComponent(currentTheme.accent, 35))
            strokeWidth = 0
        }
        dialogView.findViewById<android.widget.ImageView>(R.id.calendarSheetIcon)
            .imageTintList = ColorStateList.valueOf(currentTheme.accent)

        dialogView.findViewById<TextView>(R.id.txtCalendarSheetTitle)
            .setTextColor(currentTheme.textPrimary)
        dialogView.findViewById<TextView>(R.id.txtCalendarSheetSubtitle)
            .setTextColor(currentTheme.textSecondary)
        dialogView.findViewById<TextView>(R.id.txtCalendarName)
            .setTextColor(currentTheme.textPrimary)
        dialogView.findViewById<TextView>(R.id.txtSelectionSummary)
            .setTextColor(currentTheme.textSecondary)

        dialogView.findViewById<MaterialButton>(R.id.btnToggleAllMoments).apply {
            setTextColor(currentTheme.accent)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnCancelCalendarSelection).apply {
            setTextColor(currentTheme.textPrimary)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeColor = ColorStateList.valueOf(currentTheme.cardStroke)
        }
        dialogView.findViewById<MaterialButton>(R.id.btnAddSelectedMoments).apply {
            setTextColor(accentTextColor)
            iconTint = ColorStateList.valueOf(accentTextColor)
            backgroundTintList = ColorStateList.valueOf(currentTheme.accent)
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }
    }

    private fun roundedTopBackground(color: Int, radiusDp: Float): GradientDrawable {
        val radius = radiusDp * resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
            )
        }
    }

    private fun confirmExistingEvents(
        calendar: GoogleCalendarSyncManager.GoogleCalendar,
        allMoments: MutableList<Moment>,
        candidates: List<GoogleCalendarSyncManager.SyncCandidate>
    ) {
        val existingCount = candidates.count { it.existingEvent != null }
        val newCount = candidates.size - existingCount

        if (existingCount == 0) {
            performCalendarSync(
                calendar = calendar,
                allMoments = allMoments,
                candidates = candidates,
                replaceExisting = false
            )
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Existing events found")
            .setMessage(
                "$newCount selected moment${if (newCount == 1) " is" else "s are"} new.\n\n" +
                    "$existingCount selected moment${if (existingCount == 1) " already exists" else "s already exist"} " +
                    "in ${calendar.displayLabel}. Do you want SinceWhen to replace the existing " +
                    "calendar version with its current title and date?"
            )
            .setPositiveButton("Replace existing") { _, _ ->
                performCalendarSync(
                    calendar = calendar,
                    allMoments = allMoments,
                    candidates = candidates,
                    replaceExisting = true
                )
            }
            .setNeutralButton("Keep existing") { _, _ ->
                performCalendarSync(
                    calendar = calendar,
                    allMoments = allMoments,
                    candidates = candidates,
                    replaceExisting = false
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performCalendarSync(
        calendar: GoogleCalendarSyncManager.GoogleCalendar,
        allMoments: MutableList<Moment>,
        candidates: List<GoogleCalendarSyncManager.SyncCandidate>,
        replaceExisting: Boolean
    ) {
        Toast.makeText(this, "Updating Google Calendar…", Toast.LENGTH_SHORT).show()

        calendarExecutor.execute {
            try {
                val result = calendarSyncManager.applySync(
                    calendarId = calendar.id,
                    candidates = candidates,
                    replaceExisting = replaceExisting
                )

                saveAllMoments(allMoments)

                runOnUiThread {
                    // MainActivity must reload the Moments so later saves do not
                    // overwrite the calendar IDs added during this sync.
                    setResult(RESULT_OK)

                    val summary = buildString {
                        append("Added: ${result.added}")
                        append("\nReplaced: ${result.replaced}")
                        append("\nKept unchanged: ${result.kept}")
                        if (result.failed > 0) {
                            append("\nFailed: ${result.failed}")
                        }
                    }

                    MaterialAlertDialogBuilder(this)
                        .setTitle(
                            if (result.failed == 0) {
                                "Google Calendar updated"
                            } else {
                                "Calendar update partly completed"
                            }
                        )
                        .setMessage(summary)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                showCalendarError("Could not update Google Calendar", e)
            }
        }
    }

    private fun showCalendarError(title: String, error: Exception) {
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(error.message ?: "An unknown calendar error occurred.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun loadAllMoments(): MutableList<Moment> {
        val json = getSharedPreferences("Prefs", MODE_PRIVATE)
            .getString("list", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<Moment>>() {}.type
        return Gson().fromJson<MutableList<Moment>>(json, type) ?: mutableListOf()
    }

    private fun saveAllMoments(moments: List<Moment>) {
        getSharedPreferences("Prefs", MODE_PRIVATE).edit {
            putString("list", Gson().toJson(moments))
        }
        UpcomingWidgetProvider.refreshWidget(this)
    }

    // -------------------------------------------------------------------------
    // Existing settings features
    // -------------------------------------------------------------------------

    private fun showClearDialog() {
        val prefs = getSharedPreferences("Prefs", MODE_PRIVATE)

        AlertDialog.Builder(this)
            .setTitle("Clear all moments?")
            .setMessage("This will permanently delete everything stored in your app.")
            .setPositiveButton("Yes, delete everything") { _, _ ->
                prefs.edit {
                    remove("list")
                }

                UpcomingWidgetProvider.refreshWidget(this)

                setResult(RESULT_OK)
                finish()

                UpcomingWidgetProvider.refreshWidget(this)

                Toast.makeText(this, "All moments deleted", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Export backup instead") { _, _ ->
                createBackupFileLauncher.launch("SinceWhen_Backup.json")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun writeBackupData(uri: Uri) {
        try {
            val json = getSharedPreferences("Prefs", Context.MODE_PRIVATE).getString("list", "[]")
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json!!.toByteArray())
            }
            Toast.makeText(this, "Backup saved successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readAndRestoreBackup(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }

                    val importedJson = stringBuilder.toString()

                    if (importedJson.trim().startsWith("[")) {
                        getSharedPreferences("Prefs", Context.MODE_PRIVATE)
                            .edit {
                                putString("list", importedJson)
                            }

                        UpcomingWidgetProvider.refreshWidget(this)

                        Toast.makeText(
                            this,
                            "Import complete! Restarting data view.",
                            Toast.LENGTH_SHORT
                        ).show()

                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this, "Invalid backup file structure.", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
