package com.nekosu.shared

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object SharedTheme {
    private val seedColors = listOf(
        Color(0xFF6750A4),
        Color(0xFF0061A4),
        Color(0xFF2E6B3F),
        Color(0xFF8B5000),
    )

    fun colorScheme(dark: Boolean, seedIndex: Int): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val seed = seedColors.getOrElse(seedIndex) { seedColors[0] }
        return base.copy(
            primary = if (dark) lighten(seed, 0.35f) else seed,
            onPrimary = if (dark) Color.Black else Color.White,
            primaryContainer = if (dark) darken(seed, 0.45f) else lighten(seed, 0.84f),
            onPrimaryContainer = if (dark) lighten(seed, 0.75f) else darken(seed, 0.65f),
            secondary = if (dark) lighten(seed, 0.55f) else darken(seed, 0.25f),
            tertiary = if (dark) lighten(seed, 0.7f) else darken(seed, 0.4f),
            surfaceTint = seed,
        )
    }

    fun typography(font: FontFamily? = null): Typography {
        val base = Typography()
        if (font == null) return base
        return base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = font),
            displayMedium = base.displayMedium.copy(fontFamily = font),
            displaySmall = base.displaySmall.copy(fontFamily = font),
            headlineLarge = base.headlineLarge.copy(fontFamily = font),
            headlineMedium = base.headlineMedium.copy(fontFamily = font),
            headlineSmall = base.headlineSmall.copy(fontFamily = font),
            titleLarge = base.titleLarge.copy(fontFamily = font),
            titleMedium = base.titleMedium.copy(fontFamily = font),
            titleSmall = base.titleSmall.copy(fontFamily = font),
            bodyLarge = base.bodyLarge.copy(fontFamily = font),
            bodyMedium = base.bodyMedium.copy(fontFamily = font),
            bodySmall = base.bodySmall.copy(fontFamily = font),
            labelLarge = base.labelLarge.copy(fontFamily = font),
            labelMedium = base.labelMedium.copy(fontFamily = font),
            labelSmall = base.labelSmall.copy(fontFamily = font),
        )
    }

    @Composable
    fun Theme(
        dark: Boolean,
        seedIndex: Int = 0,
        font: FontFamily? = null,
        content: @Composable () -> Unit,
    ) {
        MaterialTheme(
            colorScheme = colorScheme(dark, seedIndex),
            typography = typography(font),
            content = content,
        )
    }

    private fun lighten(color: Color, fraction: Float): Color =
        Color(
            red = color.red + (1f - color.red) * fraction,
            green = color.green + (1f - color.green) * fraction,
            blue = color.blue + (1f - color.blue) * fraction,
            alpha = color.alpha,
        )

    private fun darken(color: Color, fraction: Float): Color =
        Color(
            red = color.red * (1f - fraction),
            green = color.green * (1f - fraction),
            blue = color.blue * (1f - fraction),
            alpha = color.alpha,
        )
}
