package com.kelleyparker.multistream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Status and dial colours that stay readable in both themes. */
@Immutable
data class StatusColors(
  val idle: Color,
  val connecting: Color,
  val live: Color,
  val failed: Color,
  val good: Color,
  val low: Color,
)

private val LightStatus = StatusColors(
  idle = Color(0xFF8A8A99),
  connecting = Color(0xFFB26A00),
  live = Color(0xFF1E8E3E),
  failed = Color(0xFFC5221F),
  good = Color(0xFF1E8E3E),
  low = Color(0xFFE09400),
)

private val DarkStatus = StatusColors(
  idle = Color(0xFF9A9AAB),
  connecting = Color(0xFFFFB74D),
  live = Color(0xFF5BD47A),
  failed = Color(0xFFFF6B63),
  good = Color(0xFF5BD47A),
  low = Color(0xFFFFC14D),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val LightScheme = lightColorScheme(
  primary = Color(0xFFD7263D),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFFDAD9),
  onPrimaryContainer = Color(0xFF410007),
  secondary = Color(0xFF5A4FCF),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE4DFFF),
  onSecondaryContainer = Color(0xFF16004F),
  background = Color(0xFFFBF8FC),
  surface = Color(0xFFFBF8FC),
  surfaceVariant = Color(0xFFE7E1EA),
  surfaceContainer = Color(0xFFF1ECF4),
  surfaceContainerHigh = Color(0xFFEBE6EE),
)

private val DarkScheme = darkColorScheme(
  primary = Color(0xFFFF6B6B),
  onPrimary = Color(0xFF4A0010),
  primaryContainer = Color(0xFF7A1424),
  onPrimaryContainer = Color(0xFFFFDAD9),
  secondary = Color(0xFFC6BFFF),
  onSecondary = Color(0xFF2B1A8F),
  secondaryContainer = Color(0xFF4335B5),
  onSecondaryContainer = Color(0xFFE4DFFF),
  background = Color(0xFF121218),
  surface = Color(0xFF121218),
  surfaceVariant = Color(0xFF2C2A33),
  surfaceContainer = Color(0xFF1D1C24),
  surfaceContainerHigh = Color(0xFF27262F),
)

@Composable
fun MultistreamTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
  }
}
