package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val customColors = if (darkTheme) DarkColors else LightColors

  val colorScheme = if (darkTheme) {
    darkColorScheme(
      primary = customColors.primary,
      secondary = customColors.accent,
      tertiary = customColors.primary,
      background = customColors.bg,
      surface = customColors.cardBg,
      onPrimary = Color.Black,
      onSecondary = customColors.textPrimary,
      onTertiary = customColors.textPrimary,
      onBackground = customColors.textPrimary,
      onSurface = customColors.textPrimary,
      surfaceVariant = customColors.mutedBg,
      onSurfaceVariant = customColors.textSecondary
    )
  } else {
    lightColorScheme(
      primary = customColors.primary,
      secondary = customColors.accent,
      tertiary = customColors.primary,
      background = customColors.bg,
      surface = customColors.cardBg,
      onPrimary = Color.White,
      onSecondary = customColors.textPrimary,
      onTertiary = customColors.textPrimary,
      onBackground = customColors.textPrimary,
      onSurface = customColors.textPrimary,
      surfaceVariant = customColors.mutedBg,
      onSurfaceVariant = customColors.textSecondary
    )
  }

  CompositionLocalProvider(LocalAppColors provides customColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
