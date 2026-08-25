package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RedEyeDarkColorScheme = darkColorScheme(
  primary = SignalRed,
  onPrimary = DarkBackground,
  primaryContainer = SignalRedContainer,
  onPrimaryContainer = SignalRed,
  secondary = RadarCyan,
  onSecondary = DarkBackground,
  secondaryContainer = RadarCyanContainer,
  onSecondaryContainer = RadarCyan,
  tertiary = AlertAmber,
  onTertiary = DarkBackground,
  tertiaryContainer = AlertAmberContainer,
  onTertiaryContainer = AlertAmber,
  background = DarkBackground,
  onBackground = TextPrimary,
  surface = DarkSurface,
  onSurface = TextPrimary,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = DarkCardBorder,
  error = ThreatCritical,
  onError = DarkBackground
)

@Composable
fun RedEyeTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = RedEyeDarkColorScheme,
    typography = Typography,
    content = content
  )
}
