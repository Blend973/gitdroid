package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyanAmoledColorScheme = darkColorScheme(
  primary = Color(0xFF00E5FF), // Vibrant Cyan Accent
  onPrimary = Color(0xFF000000), // High Contrast black text on cyan
  primaryContainer = Color(0xFF003D45),
  onPrimaryContainer = Color(0xFFB3F5FF),
  
  secondary = Color(0xFF00E5FF),
  onSecondary = Color(0xFF000000),
  secondaryContainer = Color(0xFF003238),
  onSecondaryContainer = Color(0xFFB3F5FF),
  
  background = Color(0xFF000000), // Pure Black Background
  onBackground = Color(0xFFFFFFFF), // Crisp White Text
  
  surface = Color(0xFF000000), // Pure Black Surfaces for AMOLED look
  onSurface = Color(0xFFFFFFFF),
  
  surfaceVariant = Color(0xFF101010), // Ultra-dark gray to separate items subtly
  onSurfaceVariant = Color(0xFFCCCCCC),
  
  outline = Color(0xFF00E5FF), // Cyan outlines where appropriate
  error = Color(0xFFFF5252),
  onError = Color(0xFF000000)
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = CyanAmoledColorScheme,
    typography = Typography,
    content = content
  )
}
