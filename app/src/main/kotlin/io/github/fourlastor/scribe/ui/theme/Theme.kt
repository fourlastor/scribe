package io.github.fourlastor.scribe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun ScribeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        // Scribe is a utility that gets out of the way, so its motion settles without overshoot.
        // Every animation in the app takes its spec from this scheme.
        motionScheme = MotionScheme.standard(),
        content = content,
    )
}
