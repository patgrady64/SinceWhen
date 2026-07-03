package com.patgrady64.sincewhen

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.core.content.edit
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.patgrady64.sincewhen.theme.ThemeActivity
import com.patgrady64.sincewhen.theme.ThemeApplier
import com.patgrady64.sincewhen.theme.ThemeManager

class SettingsActivity : AppCompatActivity() {

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



    private fun themeManager(){
        startActivity(Intent(this, ThemeActivity::class.java))
    }

    private fun refreshWidget() {
        UpcomingWidgetProvider.refreshWidget(this)
    }

    private fun showComingSoon() {
        Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun exportBackup() {
        createBackupFileLauncher.launch("SinceWhen_Backup.json")
    }

    private fun importBackup() {
        importBackupFileLauncher.launch(arrayOf("*/*"))
    }


    private fun showClearDialog() {
        val prefs = getSharedPreferences("Prefs", MODE_PRIVATE)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear all moments?")
            .setMessage("This will permanently delete everything stored in your app.")
            .setPositiveButton("Yes, delete everything") { _, _ ->

                prefs.edit {
                    remove("list")
                }

                UpcomingWidgetProvider.refreshWidget(this)

                setResult(RESULT_OK)
                finish()

                // refresh widget
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

                    // Basic syntax integrity validation check before overriding database cache
                    if (importedJson.trim().startsWith("[")) {
                        getSharedPreferences("Prefs", Context.MODE_PRIVATE)
                            .edit {
                                putString("list", importedJson)
                            }

                        // Tell the system widget engine to update calculations immediately
                        UpcomingWidgetProvider.refreshWidget(this)

                        Toast.makeText(
                            this,
                            "Import complete! Restarting data view.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Notify MainActivity to reconstruct lists by setting a result code
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