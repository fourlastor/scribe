package io.github.fourlastor.scribe.ui.theme

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Timings the Material motion scheme has no token for. Springs are not defined here: they come
 * from `MaterialTheme.motionScheme`.
 */
object ScribeMotion {
    /** Work that finishes within this never shows a loading indicator at all. */
    val IndicatorDelay = 150.milliseconds

    /** Once shown, a loading indicator stays this long, so it reads as progress and not a flicker. */
    val IndicatorMinimum = 500.milliseconds
}

/**
 * Whether to show a loading indicator for work that is [loading]: not immediately, and once shown
 * not for too short a time, so that fast work never makes it flash.
 */
@Composable
fun rememberIndicatorVisible(loading: Boolean): Boolean {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading && !visible) {
            delay(ScribeMotion.IndicatorDelay)
            shownAt = SystemClock.uptimeMillis()
            visible = true
        } else if (!loading && visible) {
            val shownFor = (SystemClock.uptimeMillis() - shownAt).milliseconds
            delay(ScribeMotion.IndicatorMinimum - shownFor)
            visible = false
        }
    }
    return visible
}
