package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val HighContrastColorScheme = darkColorScheme(
  primary = VibrantYellow,
  onPrimary = NightBlack,
  secondary = BrightAmber,
  onSecondary = NightBlack,
  background = NightBlack,
  onBackground = PureWhite,
  surface = NightSurface,
  onSurface = PureWhite,
  surfaceVariant = NightSurfaceVariant,
  onSurfaceVariant = PureWhite,
  outline = BorderGray,
  error = DarkRed,
  onError = PureWhite
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable system dynamic tint to preserve high contrast layout
  content: @Composable () -> Unit,
) {
  val colorScheme = HighContrastColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
