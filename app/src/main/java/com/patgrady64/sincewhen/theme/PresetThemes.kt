package com.patgrady64.sincewhen.theme

import android.graphics.Color

object PresetThemes {

    val Orange = AppTheme(
        name = "Material Orange",
        accent = Color.parseColor("#FF6F00"),
        background = Color.parseColor("#121212"),
        surface = Color.parseColor("#1E1E1E"),
        card = Color.parseColor("#222222"),
        cardStroke = Color.parseColor("#343434"),
        textPrimary = Color.WHITE,
        textSecondary = Color.GRAY,

        success = Color.parseColor("#00C853"), // green contrast (keeps clarity)
        warning = Color.parseColor("#FF6F00"), // strong orange identity
        info    = Color.parseColor("#8D6E63") // warm brown (past / muted memory)
    )

    val Blue = AppTheme(
        name = "Ocean Blue",
        accent = Color.parseColor("#1E88E5"),
        background = Color.parseColor("#101820"),
        surface = Color.parseColor("#1A2634"),
        card = Color.parseColor("#223246"),
        cardStroke = Color.parseColor("#35506B"),
        textPrimary = Color.WHITE,
        textSecondary = Color.parseColor("#B0BEC5"),

        success = Color.parseColor("#00E676"), // green-cyan (clearly not blue)
        warning = Color.parseColor("#FFB300"), // amber (warm contrast, stands out hard)
        info    = Color.parseColor("#2979FF") // clean bright blue (true ocean identity)// deep ocean (past)
    )

    val Green = AppTheme(
        name = "Forest",
        accent = Color.parseColor("#43A047"),
        background = Color.parseColor("#101610"),
        surface = Color.parseColor("#182118"),
        card = Color.parseColor("#223022"),
        cardStroke = Color.parseColor("#355535"),
        textPrimary = Color.WHITE,
        textSecondary = Color.parseColor("#C8E6C9"),

        success = Color.parseColor("#43A047"), // forest green (alive)
        warning = Color.parseColor("#A1887F"), // bark brown (future/earth)
        info    = Color.parseColor("#2E7D32") // deep moss (past)    // cool contrast (this fixes your “blue everywhere” feeling)
    )

    val Purple = AppTheme(
        name = "Purple Night",
        accent = Color.parseColor("#8E24AA"),
        background = Color.parseColor("#150F1B"),
        surface = Color.parseColor("#22172A"),
        card = Color.parseColor("#2F203A"),
        cardStroke = Color.parseColor("#513666"),
        textPrimary = Color.WHITE,
        textSecondary = Color.parseColor("#D1C4E9"),

        success = Color.parseColor("#69F0AE"), // neon green (life)
        warning = Color.parseColor("#BA68C8"), // purple mid-tone (future)
        info    = Color.parseColor("#4A148C") // deep violet (past)
    )

    val Red = AppTheme(
        name = "Crimson",
        accent = Color.parseColor("#E53935"),
        background = Color.parseColor("#160F0F"),
        surface = Color.parseColor("#241616"),
        card = Color.parseColor("#301E1E"),
        cardStroke = Color.parseColor("#553333"),
        textPrimary = Color.WHITE,
        textSecondary = Color.parseColor("#FFCDD2"),

        success = Color.parseColor("#00C853"),
        warning = Color.parseColor("#FF8A80"),
        info = Color.parseColor("#E53935")
    )

    val Amoled = AppTheme(
        name = "AMOLED",
        accent = Color.parseColor("#00BCD4"),
        background = Color.BLACK,
        surface = Color.BLACK,
        card = Color.parseColor("#111111"),
        cardStroke = Color.parseColor("#222222"),
        textPrimary = Color.WHITE,
        textSecondary = Color.parseColor("#9E9E9E"),

        success = Color.parseColor("#00E676"), // neon green
        warning = Color.parseColor("#00BCD4"), // cyan
        info    = Color.parseColor("#90A4AE") // muted gray-blue (past)
    )

    val Sunset = AppTheme(
        name = "Sunset",
        accent = Color.parseColor("#FB8C00"),
        background = Color.parseColor("#1B120C"),
        surface = Color.parseColor("#2A1A11"),
        card = Color.parseColor("#3A2418"),
        cardStroke = Color.parseColor("#5C3824"),
        textPrimary = Color.WHITE,
        textSecondary = Color.parseColor("#FFE0B2"),

        success = Color.parseColor("#66BB6A"),
        warning = Color.parseColor("#FB8C00"),
        info = Color.parseColor("#FF7043")
    )

    val all = listOf(
        Orange,
        Blue,
        Green,
        Purple,
        Red,
        Sunset,
        Amoled
    )

}