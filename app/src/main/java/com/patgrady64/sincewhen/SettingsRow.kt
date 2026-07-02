package com.patgrady64.sincewhen

import android.view.View
import android.widget.TextView

class SettingsRow(private val root: View) {

    private val title: TextView = root.findViewById(R.id.txtTitle)
    private val subtitle: TextView = root.findViewById(R.id.txtSubtitle)

    fun setTitle(text: String): SettingsRow {
        title.text = text
        return this
    }

    fun setSubtitle(text: String): SettingsRow {
        subtitle.text = text
        return this
    }

    fun onClick(action: () -> Unit): SettingsRow {
        root.setOnClickListener { action() }
        return this
    }
}