package com.patgrady64.sincewhen.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

object ThemeManager {
    private const val PREFS = "Prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_MODE = "theme_mode"
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    //--------------------------------------------------
    // Theme
    //--------------------------------------------------

    fun saveTheme(context: Context, theme: AppTheme) {

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_THEME, theme.name)
            }

    }

    fun getTheme(context: Context): AppTheme {

        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, PresetThemes.Orange.name)

        return PresetThemes.all.firstOrNull {

            it.name == name

        } ?: PresetThemes.Orange

    }

    //--------------------------------------------------
    // Theme Mode
    //--------------------------------------------------

    fun saveThemeMode(context: Context, mode: Int) {

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_MODE, mode)
            }

    }

    fun applySavedThemeMode(context: Context) {

        when (
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, MODE_SYSTEM)
        ) {

            MODE_LIGHT ->
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO
                )

            MODE_DARK ->
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES
                )
            else ->
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                )
        }

    }

}