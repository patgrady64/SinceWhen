package com.patgrady64.sincewhen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnSettingsBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnExportBackup).setOnClickListener {
            // Prompts file picker to name a new backup file
            createBackupFileLauncher.launch("SinceWhen_Backup.json")
        }

        findViewById<Button>(R.id.btnImportBackup).setOnClickListener {
            // Prompts file picker to look up an existing JSON file
            importBackupFileLauncher.launch(arrayOf("*/*"))
        }
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
                            .edit()
                            .putString("list", importedJson)
                            .apply()

                        // Tell the system widget engine to update calculations immediately
                        UpcomingWidgetProvider.refreshWidget(this)

                        Toast.makeText(this, "Import complete! Restarting data view.", Toast.LENGTH_SHORT).show()

                        // Notify MainActivity to reconstruct lists by setting a result code
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this, "Invalid backup file structure.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}