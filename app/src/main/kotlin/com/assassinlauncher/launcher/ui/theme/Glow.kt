package com.assassinlauncher.launcher.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The one signature visual element, used sparingly and only where it
 * earns its place - the Play button on Home, nowhere else. A soft
 * radial falloff from a semi-transparent brand color to nothing, read
 * as "an ember catching light" rather than a generic drop shadow.
 *
 * Implemented as a plain radial gradient rather than a real blur
 * (Modifier.blur is API 31+ via RenderEffect, with an awkward fallback
 * below that) - this renders identically on every supported API level
 * without a version branch, and a soft gradient falloff reads as a glow
 * just as well as an actually-blurred shape would at this scale.
 */
fun Modifier.glow(color: Color, radiusFraction: Float = 1f): Modifier = drawBehind {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = (size.minDimension / 2f) * (1.6f * radiusFraction)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.35f),
                color.copy(alpha = 0.12f),
                color.copy(alpha = 0f)
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** A standalone glow behind arbitrary content, for cases (like the Play
 * button) where the glow needs to extend visibly beyond the element's
 * own bounds rather than clip to them. */
@Composable
fun GlowBox(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Box(modifier = Modifier.size(size).glow(color))
        content()
    }
}
