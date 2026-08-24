package neth.iecal.questphone.app.theme.customThemes

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import kotlin.math.*
import neth.iecal.questphone.app.theme.data.CustomColor

// ═══════════════════════════════════════════════════════════════════════
// THEME 1 — CELESTIAL ✨
// Soft lavender white, purple+pink accents, floating sparkles
// ═══════════════════════════════════════════════════════════════════════
class CelestialTheme : BaseTheme {
    override val name = "Celestial"
    override val description = "Light beyond the stars ✨"
    override val expandQuestsText = "✦ ✦ ✦ ✦ ✦"
    override val price = 60

    override fun getRootColorScheme(): ColorScheme = lightColorScheme(
        primary        = Color(0xFF7C3AED),
        onPrimary      = Color.White,
        secondary      = Color(0xFFEC4899),
        onSecondary    = Color.White,
        tertiary       = Color(0xFFF59E0B),
        onTertiary     = Color.White,
        background     = Color(0xFFF8F4FF),
        onBackground   = Color(0xFF1A0A2E),
        surface        = Color(0xFFFFFFFF).copy(alpha = 0.75f),
        onSurface      = Color(0xFF1A0A2E),
        surfaceVariant = Color(0xFFEDE9FE),
        error          = Color(0xFFEF4444),
        onError        = Color.White
    )

    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFFEDE9FE).copy(alpha = 0.85f),
        heatMapCells     = Color(0xFF7C3AED),
        dialogText       = Color(0xFF1A0A2E)
    )

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        CelestialSparkles()
    }
}

@Composable
private fun CelestialSparkles() {
    val transition = rememberInfiniteTransition(label = "celestial")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "t"
    )
    val particles = remember {
        List(35) {
            floatArrayOf(
                (Math.random() * 1000).toFloat(),
                (Math.random() * 2000).toFloat(),
                (Math.random() * PI).toFloat(),
                (Math.random() * 3f + 1f).toFloat()
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Subtle gradient wash
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF7C3AED).copy(alpha = 0.06f),
                    Color(0xFFEC4899).copy(alpha = 0.04f),
                    Color.Transparent
                )
            )
        )
        particles.forEach { p ->
            val alpha = (sin(time + p[2]) * 0.3f + 0.4f).coerceIn(0.05f, 0.7f)
            val color = if ((p[0] + p[1]) % 2 < 1) Color(0xFF7C3AED) else Color(0xFFEC4899)
            drawCircle(color.copy(alpha = alpha * 0.35f), p[3], Offset(p[0] % size.width, p[1] % size.height))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// THEME 2 — SUNRISE 🌄
// Warm creamy white, orange+coral, rising light rays
// ═══════════════════════════════════════════════════════════════════════
class SunriseTheme : BaseTheme {
    override val name = "Sunrise"
    override val description = "Every morning is a new quest 🌄"
    override val expandQuestsText = "· · · · · · ·"
    override val price = 55

    override fun getRootColorScheme(): ColorScheme = lightColorScheme(
        primary        = Color(0xFFFF6B35),
        onPrimary      = Color.White,
        secondary      = Color(0xFFFF9F1C),
        onSecondary    = Color(0xFF1A0800),
        tertiary       = Color(0xFFFF3366),
        onTertiary     = Color.White,
        background     = Color(0xFFFFF8F0),
        onBackground   = Color(0xFF1A0800),
        surface        = Color(0xFFFFFFFF).copy(alpha = 0.8f),
        onSurface      = Color(0xFF1A0800),
        surfaceVariant = Color(0xFFFFEDD5),
        error          = Color(0xFFDC2626),
        onError        = Color.White
    )

    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFFFFEDD5).copy(alpha = 0.9f),
        heatMapCells     = Color(0xFFFF6B35),
        dialogText       = Color(0xFF1A0800)
    )

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        SunriseRays()
    }
}

@Composable
private fun SunriseRays() {
    val transition = rememberInfiniteTransition(label = "sunrise")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.18f
        // Sun glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD700).copy(alpha = 0.18f + pulse * 0.08f),
                    Color(0xFFFF9F1C).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = size.width * 0.55f
            ),
            radius = size.width * 0.55f,
            center = Offset(cx, cy)
        )
        // Light rays
        val rayCount = 12
        repeat(rayCount) { i ->
            val angle = (i * PI * 2 / rayCount).toFloat()
            val rayAlpha = (0.04f + pulse * 0.03f)
            drawLine(
                color = Color(0xFFFF9F1C).copy(alpha = rayAlpha),
                start = Offset(cx, cy),
                end = Offset(
                    cx + cos(angle) * size.width * 0.8f,
                    cy + sin(angle) * size.width * 0.8f
                ),
                strokeWidth = 3f
            )
        }
        // Warm gradient from bottom
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF6B35).copy(alpha = 0.04f + pulse * 0.02f)
                )
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// THEME 3 — ARCTIC 🧊
// Ice white, crisp, sky blue+cyan, drifting snowflakes
// ═══════════════════════════════════════════════════════════════════════
class ArcticTheme : BaseTheme {
    override val name = "Arctic"
    override val description = "Cold focus, sharp mind 🧊"
    override val expandQuestsText = "❄ ❄ ❄ ❄ ❄"
    override val price = 55

    override fun getRootColorScheme(): ColorScheme = lightColorScheme(
        primary        = Color(0xFF0284C7),
        onPrimary      = Color.White,
        secondary      = Color(0xFF06B6D4),
        onSecondary    = Color.White,
        tertiary       = Color(0xFF6366F1),
        onTertiary     = Color.White,
        background     = Color(0xFFF0FAFF),
        onBackground   = Color(0xFF0C1A2E),
        surface        = Color(0xFFFFFFFF).copy(alpha = 0.8f),
        onSurface      = Color(0xFF0C1A2E),
        surfaceVariant = Color(0xFFE0F2FE),
        error          = Color(0xFFDC2626),
        onError        = Color.White
    )

    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFFE0F2FE).copy(alpha = 0.9f),
        heatMapCells     = Color(0xFF0284C7),
        dialogText       = Color(0xFF0C1A2E)
    )

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        ArcticSnow()
    }
}

@Composable
private fun ArcticSnow() {
    val transition = rememberInfiniteTransition(label = "arctic")
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "drift"
    )
    val flakes = remember {
        List(40) {
            floatArrayOf(
                (Math.random() * 1000).toFloat(),
                (Math.random() * 2000).toFloat(),
                (Math.random() * 4f + 2f).toFloat(),
                (Math.random() * PI).toFloat()
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0284C7).copy(alpha = 0.05f),
                    Color(0xFF06B6D4).copy(alpha = 0.03f),
                    Color.Transparent
                )
            )
        )
        flakes.forEach { f ->
            val x = (f[0] + sin(drift * 0.01f + f[3]) * 30f) % size.width
            val y = (f[1] + drift * 0.4f) % size.height
            drawCircle(
                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                radius = f[2],
                center = Offset(x, y)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// THEME 4 — GARDEN 🌿
// Fresh mint white, green+lime, floating leaves
// ═══════════════════════════════════════════════════════════════════════
class GardenTheme : BaseTheme {
    override val name = "Garden"
    override val description = "Grow every day 🌿"
    override val expandQuestsText = "🌿 🌿 🌿 🌿"
    override val price = 50

    override fun getRootColorScheme(): ColorScheme = lightColorScheme(
        primary        = Color(0xFF16A34A),
        onPrimary      = Color.White,
        secondary      = Color(0xFF65A30D),
        onSecondary    = Color.White,
        tertiary       = Color(0xFF0891B2),
        onTertiary     = Color.White,
        background     = Color(0xFFF0FFF4),
        onBackground   = Color(0xFF052E16),
        surface        = Color(0xFFFFFFFF).copy(alpha = 0.8f),
        onSurface      = Color(0xFF052E16),
        surfaceVariant = Color(0xFFDCFCE7),
        error          = Color(0xFFDC2626),
        onError        = Color.White
    )

    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFFDCFCE7).copy(alpha = 0.9f),
        heatMapCells     = Color(0xFF16A34A),
        dialogText       = Color(0xFF052E16)
    )

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        GardenLeaves()
    }
}

@Composable
private fun GardenLeaves() {
    val transition = rememberInfiniteTransition(label = "garden")
    val fall by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)),
        label = "fall"
    )
    val leaves = remember {
        List(20) {
            floatArrayOf(
                (Math.random() * 1000).toFloat(),
                (Math.random() * 2000).toFloat(),
                (Math.random() * 12f + 6f).toFloat(),
                (Math.random() * PI).toFloat()
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF16A34A).copy(alpha = 0.06f),
                    Color(0xFF65A30D).copy(alpha = 0.03f),
                    Color.Transparent
                )
            )
        )
        leaves.forEach { l ->
            val x = (l[0] + sin(fall * 0.015f + l[3]) * 40f) % size.width
            val y = (l[1] + fall * 0.5f) % size.height
            val rotation = fall * 0.5f + l[3] * 100f
            drawOval(
                color = Color(0xFF16A34A).copy(alpha = 0.15f),
                topLeft = Offset(x - l[2], y - l[2] * 0.5f),
                size = androidx.compose.ui.geometry.Size(l[2] * 2f, l[2])
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// THEME 5 — CANDY 🍬
// Bubbly pastel white, pink+purple+yellow, bouncing bubbles
// ═══════════════════════════════════════════════════════════════════════
class CandyTheme : BaseTheme {
    override val name = "Candy"
    override val description = "Life is sweet 🍬"
    override val expandQuestsText = "🍬 🍭 🍬 🍭 🍬"
    override val price = 50

    override fun getRootColorScheme(): ColorScheme = lightColorScheme(
        primary        = Color(0xFFDB2777),
        onPrimary      = Color.White,
        secondary      = Color(0xFF9333EA),
        onSecondary    = Color.White,
        tertiary       = Color(0xFFF59E0B),
        onTertiary     = Color.White,
        background     = Color(0xFFFFF0F8),
        onBackground   = Color(0xFF2D0A1F),
        surface        = Color(0xFFFFFFFF).copy(alpha = 0.8f),
        onSurface      = Color(0xFF2D0A1F),
        surfaceVariant = Color(0xFFFFE4F0),
        error          = Color(0xFFDC2626),
        onError        = Color.White
    )

    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFFFFE4F0).copy(alpha = 0.9f),
        heatMapCells     = Color(0xFFDB2777),
        dialogText       = Color(0xFF2D0A1F)
    )

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        CandyBubbles()
    }
}

@Composable
private fun CandyBubbles() {
    val transition = rememberInfiniteTransition(label = "candy")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "t"
    )
    val bubbles = remember {
        List(25) {
            floatArrayOf(
                (Math.random() * 1000).toFloat(),
                (Math.random() * 2000).toFloat(),
                (Math.random() * 20f + 8f).toFloat(),
                (Math.random() * PI * 2).toFloat()
            )
        }
    }
    val colors = listOf(
        Color(0xFFDB2777), Color(0xFF9333EA),
        Color(0xFFF59E0B), Color(0xFF06B6D4)
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFDB2777).copy(alpha = 0.05f),
                    Color(0xFF9333EA).copy(alpha = 0.04f),
                    Color.Transparent
                )
            )
        )
        bubbles.forEachIndexed { i, b ->
            val bobY = b[1] + sin(time + b[3]) * 18f
            val x = b[0] % size.width
            val y = bobY % size.height
            val color = colors[i % colors.size]
            // Bubble outline
            drawCircle(color.copy(alpha = 0.15f), b[2], Offset(x, y))
            drawCircle(
                color = color.copy(alpha = 0.25f),
                radius = b[2],
                center = Offset(x, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
            // Highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = b[2] * 0.25f,
                center = Offset(x - b[2] * 0.3f, y - b[2] * 0.3f)
            )
        }
    }
}
