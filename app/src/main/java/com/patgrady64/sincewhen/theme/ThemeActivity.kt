package com.patgrady64.sincewhen.theme

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.patgrady64.sincewhen.R

class ThemeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)

        val container = findViewById<LinearLayout>(R.id.themeContainer)

        val currentTheme = ThemeManager.getTheme(this)

        PresetThemes.all.forEach { theme ->

            val isSelected = theme.name == currentTheme.name

            val card = MaterialCardView(this).apply {
                radius = 32f
                setCardBackgroundColor(theme.surface)

                strokeWidth = 4
                strokeColor = if (isSelected) theme.accent else theme.cardStroke

                setContentPadding(32, 32, 32, 32)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 24
                }

                setOnClickListener {
                    ThemeManager.saveTheme(this@ThemeActivity, theme)

                    setResult(RESULT_OK)
                    finish()
                }
            }

            val title = TextView(this).apply {
                text = theme.name
                textSize = 18f
                setTextColor(theme.textPrimary)
            }

            val swatchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 20, 0, 20)
            }

            fun swatch(color: Int): View {
                return View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(50, 50).apply {
                        marginEnd = 16
                    }
                    setBackgroundColor(color)
                }
            }

            swatchRow.addView(swatch(theme.background))
            swatchRow.addView(swatch(theme.surface))
            swatchRow.addView(swatch(theme.card))
            swatchRow.addView(swatch(theme.accent))

            val previewButton = TextView(this).apply {
                text = "Button"
                setPadding(40, 20, 40, 20)
                setBackgroundColor(theme.accent)
                setTextColor(theme.textPrimary)
            }

            val previewRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(previewButton)
            }

            val subtitle = TextView(this).apply {
                text = if (isSelected) "Selected" else "Tap to apply"
                setTextColor(theme.textSecondary)
                setPadding(0, 16, 0, 0)
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(swatchRow)
                addView(previewRow)
                addView(subtitle)
            }

            card.addView(layout)
            container.addView(card)
        }
    }
}