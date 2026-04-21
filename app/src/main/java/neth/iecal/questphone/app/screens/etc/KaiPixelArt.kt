package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Pixel art avatar rendered in Compose Canvas
// Each avatar is defined as a 8x8 grid of color indices
// 0=transparent, 1=skin, 2=hair/hat, 3=outfit, 4=accent, 5=dark, 6=eyes, 7=weapon/item

private val SKIN   = Color(0xFFFFCB9A)
private val DARK   = Color(0xFF3D2B1F)

// Avatar color palettes [skin, hair, outfit, accent, extra]
private val PALETTES = listOf(
    // 0 Wizard - Purple
    listOf(SKIN, Color(0xFF6A0572), Color(0xFF9C27B0), Color(0xFFFFD700), DARK),
    // 1 Warrior - Blue
    listOf(SKIN, Color(0xFF1A237E), Color(0xFF1565C0), Color(0xFFB0BEC5), DARK),
    // 2 Archer - Green
    listOf(Color(0xFFFFC87A), Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF8D6E63), DARK),
    // 3 Rogue - Dark
    listOf(SKIN, Color(0xFF212121), Color(0xFF37474F), Color(0xFF78909C), DARK),
    // 4 Paladin - Gold
    listOf(SKIN, Color(0xFFFFD700), Color(0xFFFFA000), Color(0xFFFFFFFF), DARK),
    // 5 Mage - Teal
    listOf(SKIN, Color(0xFF004D40), Color(0xFF00695C), Color(0xFF80CBC4), DARK),
    // 6 Druid - Brown
    listOf(Color(0xFFFFE0B2), Color(0xFF4E342E), Color(0xFF6D4C41), Color(0xFF81C784), DARK),
    // 7 Necromancer - Black/Purple
    listOf(Color(0xFFE0E0E0), Color(0xFF212121), Color(0xFF4A148C), Color(0xFF7C4DFF), DARK),
    // 8 Android - Cyan
    listOf(Color(0xFFB0BEC5), Color(0xFF37474F), Color(0xFF00BCD4), Color(0xFF00E5FF), Color(0xFF263238)),
    // 9 Dragon - Red
    listOf(Color(0xFFFF8A65), Color(0xFFB71C1C), Color(0xFFC62828), Color(0xFFFF6D00), DARK),
    // 10 Pixel - Multi
    listOf(SKIN, Color(0xFFE91E63), Color(0xFF3F51B5), Color(0xFFFFEB3B), DARK),
    // 11 Fox - Orange
    listOf(Color(0xFFFFB74D), Color(0xFFE65100), Color(0xFFBF360C), Color(0xFFFFFFFF), DARK),
    // 12 Wolf - Grey
    listOf(Color(0xFF90A4AE), Color(0xFF546E7A), Color(0xFF37474F), Color(0xFFECEFF1), DARK),
    // 13 Eagle - Brown/White
    listOf(Color(0xFFFFFFFF), Color(0xFF5D4037), Color(0xFF4E342E), Color(0xFFFFD54F), DARK),
    // 14 Moon - Blue/Silver
    listOf(Color(0xFFE3F2FD), Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFFC5CAE9), DARK),
    // 15 Thunder - Yellow
    listOf(SKIN, Color(0xFFF57F17), Color(0xFFFBC02D), Color(0xFFFFFFFF), DARK),
    // 16 Fire - Red/Orange
    listOf(SKIN, Color(0xFFBF360C), Color(0xFFE64A19), Color(0xFFFF6D00), DARK),
    // 17 Ice - White/Blue
    listOf(Color(0xFFE3F2FD), Color(0xFF0277BD), Color(0xFF039BE5), Color(0xFFE1F5FE), DARK),
    // 18 Ocean - Blue/Green
    listOf(SKIN, Color(0xFF01579B), Color(0xFF0277BD), Color(0xFF00B0FF), DARK),
    // 19 Star - Gold
    listOf(Color(0xFFFFF9C4), Color(0xFFF9A825), Color(0xFFFDD835), Color(0xFFFFFFFF), DARK)
)

// 8x8 pixel art grids (0=transparent, 1=skin/fur, 2=hair, 3=outfit, 4=accent, 5=dark/shadow)
private val AVATAR_PIXELS = listOf(
    // 0 Wizard
    intArrayOf(0,0,2,2,2,2,0,0,  0,2,2,4,4,2,2,0,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,1,1,1,1,1,0,  0,3,3,3,3,3,3,0,  0,3,4,3,3,4,3,0,  0,3,3,3,3,3,3,0),
    // 1 Warrior  
    intArrayOf(0,2,2,2,2,2,2,0,  2,2,1,1,1,1,2,2,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,1,5,5,1,1,0,  3,3,3,3,3,3,3,3,  3,4,3,3,3,3,4,3,  0,3,3,3,3,3,3,0),
    // 2 Archer
    intArrayOf(0,0,2,2,2,0,0,0,  0,2,1,1,1,2,0,0,  0,1,1,1,1,1,0,4,  0,1,6,1,1,6,1,4,  0,1,1,5,1,1,1,4,  0,3,3,3,3,3,3,0,  3,3,4,3,3,4,3,3,  0,3,3,3,3,3,3,0),
    // 3 Rogue
    intArrayOf(0,0,2,2,2,2,0,0,  0,2,2,1,1,2,2,0,  0,2,1,1,1,1,2,0,  0,2,6,1,1,6,2,0,  0,2,1,5,5,1,2,0,  0,3,3,3,3,3,3,0,  3,3,4,3,3,4,3,3,  0,3,3,3,3,3,3,0),
    // 4 Paladin
    intArrayOf(2,2,2,2,2,2,2,2,  2,1,1,1,1,1,1,2,  2,1,1,1,1,1,1,2,  2,1,6,1,1,6,1,2,  2,1,1,4,4,1,1,2,  3,3,3,3,3,3,3,3,  3,4,3,3,3,3,4,3,  4,3,3,3,3,3,3,4),
    // 5 Mage
    intArrayOf(0,4,2,2,2,2,4,0,  0,2,1,1,1,1,2,0,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,1,4,4,1,1,0,  0,3,3,3,3,3,3,0,  0,3,4,3,3,4,3,0,  4,3,3,3,3,3,3,4),
    // 6 Druid
    intArrayOf(0,4,4,2,2,4,4,0,  4,2,1,1,1,1,2,4,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,1,5,5,1,1,0,  0,3,3,3,3,3,3,0,  4,3,3,3,3,3,3,4,  0,3,4,3,3,4,3,0),
    // 7 Necromancer
    intArrayOf(0,2,2,2,2,2,2,0,  2,2,1,1,1,1,2,2,  2,1,1,1,1,1,1,2,  2,1,6,1,1,6,1,2,  2,1,4,5,5,4,1,2,  2,3,3,3,3,3,3,2,  2,3,4,3,3,4,3,2,  2,3,3,3,3,3,3,2),
    // 8 Android (robot)
    intArrayOf(0,2,2,2,2,2,2,0,  2,2,2,2,2,2,2,2,  2,4,1,1,1,1,4,2,  2,4,6,1,1,6,4,2,  2,4,4,4,4,4,4,2,  0,3,3,3,3,3,3,0,  0,3,4,3,3,4,3,0,  0,3,3,0,0,3,3,0),
    // 9 Dragon
    intArrayOf(2,0,2,2,2,2,0,2,  2,2,1,1,1,1,2,2,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,4,5,5,4,1,0,  3,3,3,3,3,3,3,3,  3,4,3,3,3,3,4,3,  4,3,3,2,2,3,3,4),
    // 10 Pixel
    intArrayOf(2,2,2,2,2,2,2,2,  2,3,3,3,3,3,3,2,  2,3,1,1,1,1,3,2,  2,3,6,1,1,6,3,2,  2,3,4,3,3,4,3,2,  0,3,3,3,3,3,3,0,  0,4,3,3,3,3,4,0,  0,3,3,3,3,3,3,0),
    // 11 Fox
    intArrayOf(1,0,0,2,2,0,0,1,  1,1,2,1,1,2,1,1,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,3,5,5,3,1,0,  0,3,3,3,3,3,3,0,  3,3,4,3,3,4,3,3,  0,3,3,3,3,3,3,0),
    // 12 Wolf
    intArrayOf(1,0,2,2,2,2,0,1,  1,2,1,1,1,1,2,1,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,1,5,5,1,1,0,  0,3,3,3,3,3,3,0,  3,3,3,3,3,3,3,3,  0,3,3,3,3,3,3,0),
    // 13 Eagle
    intArrayOf(0,1,1,2,2,1,1,0,  1,1,2,1,1,2,1,1,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,4,0,  0,1,1,4,4,1,1,0,  0,1,3,3,3,3,3,0,  1,1,3,3,3,3,1,1,  0,4,3,3,3,3,4,0),
    // 14 Moon
    intArrayOf(0,0,2,2,2,2,0,0,  0,2,1,1,1,1,2,0,  4,1,1,1,1,1,1,4,  4,1,6,1,1,6,1,4,  0,1,1,4,4,1,1,0,  0,3,3,3,3,3,3,0,  0,3,4,3,3,4,3,0,  0,3,3,3,3,3,3,0),
    // 15 Thunder
    intArrayOf(0,0,2,4,4,2,0,0,  0,2,1,1,1,1,2,0,  0,1,1,1,1,1,1,0,  4,1,6,1,1,6,1,4,  0,1,4,4,4,4,1,0,  0,3,3,3,3,3,3,0,  0,3,4,3,3,4,3,0,  0,4,3,3,3,3,4,0),
    // 16 Fire
    intArrayOf(0,4,4,2,2,4,4,0,  4,2,1,1,1,1,2,4,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,4,4,4,4,1,0,  0,3,3,3,3,3,3,0,  4,3,4,3,3,4,3,4,  0,4,3,3,3,3,4,0),
    // 17 Ice
    intArrayOf(0,4,2,2,2,2,4,0,  4,2,1,1,1,1,2,4,  0,1,1,1,1,1,1,0,  0,1,6,1,1,6,1,0,  0,1,4,4,4,4,1,0,  0,3,3,3,3,3,3,0,  0,3,4,3,3,4,3,0,  4,3,3,3,3,3,3,4),
    // 18 Ocean
    intArrayOf(0,0,2,2,2,2,0,0,  0,2,1,1,1,1,2,0,  4,1,1,1,1,1,1,4,  0,1,6,1,1,6,1,0,  0,1,1,4,4,1,1,0,  4,3,3,3,3,3,3,4,  0,3,4,3,3,4,3,0,  0,4,3,3,3,3,4,0),
    // 19 Star
    intArrayOf(0,4,0,2,2,0,4,0,  0,0,2,1,1,2,0,0,  4,2,1,1,1,1,2,4,  0,1,6,1,1,6,1,0,  0,1,4,4,4,4,1,0,  0,3,3,3,3,3,3,0,  4,3,4,3,3,4,3,4,  0,4,3,3,3,3,4,0)
)

@Composable
fun KaiPixelAvatar(
    avatarIndex: Int,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val idx = avatarIndex.coerceIn(0, PALETTES.size - 1)
    val palette = PALETTES[idx]
    val pixels = AVATAR_PIXELS.getOrNull(idx) ?: AVATAR_PIXELS[0]

    val skin   = palette.getOrElse(0) { SKIN }
    val hair   = palette.getOrElse(1) { Color.Gray }
    val outfit = palette.getOrElse(2) { Color.DarkGray }
    val accent = palette.getOrElse(3) { Color.White }
    val shadow = palette.getOrElse(4) { DARK }

    Canvas(modifier = modifier.size(size)) {
        val cellW = this.size.width / 8f
        val cellH = this.size.height / 8f
        pixels.forEachIndexed { i, colorIdx ->
            val row = i / 8
            val col = i % 8
            val color = when (colorIdx) {
                0 -> null        // transparent
                1 -> skin
                2 -> hair
                3 -> outfit
                4 -> accent
                5 -> shadow
                6 -> Color(0xFF1A1A2E) // eyes
                7 -> Color(0xFFFFD700) // weapon/item
                else -> null
            } ?: return@forEachIndexed
            drawRect(
                color = color,
                topLeft = Offset(col * cellW, row * cellH),
                size = Size(cellW, cellH)
            )
        }
    }
}

// Avatar display names for settings
val KAI_AVATAR_NAMES = listOf(
    "Wizard", "Warrior", "Archer", "Rogue", "Paladin",
    "Mage", "Druid", "Necromancer", "Android", "Dragon",
    "Pixel", "Fox", "Wolf", "Eagle", "Moon",
    "Thunder", "Fire", "Ice", "Ocean", "Star"
)

// For side panel - small icon version
@Composable
fun KaiSidePanelIcon(avatarIndex: Int, modifier: Modifier = Modifier) {
    KaiPixelAvatar(avatarIndex = avatarIndex, size = 28.dp, modifier = modifier)
}
