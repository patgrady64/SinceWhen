package com.patgrady64.sincewhen.theme

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton

object ThemeApplier {

    fun apply(activity: Activity) {
        if (activity is ThemeActivity) return

        val theme = ThemeManager.getTheme(activity)

        activity.window.statusBarColor = theme.surface
        activity.window.navigationBarColor = theme.background

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        applyRecursive(root, theme)
    }

    private fun applyRecursive(parent: ViewGroup, theme: AppTheme) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)

            when (child) {

                is MaterialCardView -> {
                    child.setCardBackgroundColor(theme.card)
                    child.strokeColor = theme.cardStroke
                }

                is FloatingActionButton -> {
                    child.backgroundTintList =
                        ColorStateList.valueOf(theme.accent)
                }

                is MaterialButton -> {
                    child.backgroundTintList =
                        ColorStateList.valueOf(theme.accent)
                    child.setTextColor(theme.textPrimary)
                }

                is ImageButton -> {
                    child.imageTintList =
                        ColorStateList.valueOf(theme.accent)
                }

                is TextView -> {
                    child.setTextColor(theme.textPrimary)
                }
            }

            // 🔥 SAFE GUARD (IMPORTANT FIX)
            if (child is ViewGroup && child !is MaterialCardView) {
                applyRecursive(child, theme)
            }
        }
    }
}