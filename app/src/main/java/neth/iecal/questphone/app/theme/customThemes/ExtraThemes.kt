package neth.iecal.questphone.app.theme.customThemes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import neth.iecal.questphone.app.theme.data.CustomColor

// ---- 1. Deep Ocean --------------------------------------------------------------------------------------------------------------------------
class DeepOceanTheme : BaseTheme {
    override val name = "Deep Ocean"
    override val description = "Abyssal depths"
    override val expandQuestsText = "〰〰〰〰〰"
    override fun getRootColorScheme() = darkColorScheme(
        primary = Color(0xFF00E5FF), onPrimary = Color.Black,
        secondary = Color(0xFF0077B6), onSecondary = Color.White,
        tertiary = Color(0xFF48CAE4), onTertiary = Color.Black,
        background = Color(0xFF03071E), onBackground = Color(0xFFCAF0F8),
        surface = Color(0xFF023E8A).copy(alpha = 0.4f), onSurface = Color(0xFFCAF0F8),
        error = Color(0xFFFF006E), onError = Color.White
    )
    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFF023E8A),
        heatMapCells = Color(0xFF00E5FF),
        dialogText = Color(0xFFCAF0F8)
    )
    @Composable override fun ThemeObjects(innerPadding: PaddingValues) {}
}

// ---- 2. Crimson Dusk ----------------------------------------------------------------------------------------------------------------------
class CrimsonDuskTheme : BaseTheme {
    override val name = "Crimson Dusk"
    override val description = "The city never sleeps"
    override val expandQuestsText = "▸▸▸▸▸"
    override fun getRootColorScheme() = darkColorScheme(
        primary = Color(0xFFFF4D6D), onPrimary = Color.Black,
        secondary = Color(0xFFFF9F1C), onSecondary = Color.Black,
        tertiary = Color(0xFFFFBF69), onTertiary = Color.Black,
        background = Color(0xFF0D0208), onBackground = Color(0xFFFFE8E8),
        surface = Color(0xFF1A0A0A), onSurface = Color(0xFFFFE8E8),
        error = Color(0xFFFF006E), onError = Color.White
    )
    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFF2A0A0A),
        heatMapCells = Color(0xFFFF4D6D),
        dialogText = Color(0xFFFFE8E8)
    )
    @Composable override fun ThemeObjects(innerPadding: PaddingValues) {}
}

// ---- 3. Void Purple ------------------------------------------------------------------------------------------------------------------------
class VoidPurpleTheme : BaseTheme {
    override val name = "Void Purple"
    override val description = "Between stars"
    override val expandQuestsText = "·:·:·:·:·"
    override fun getRootColorScheme() = darkColorScheme(
        primary = Color(0xFFBE95FF), onPrimary = Color.Black,
        secondary = Color(0xFF9D4EDD), onSecondary = Color.White,
        tertiary = Color(0xFFC77DFF), onTertiary = Color.Black,
        background = Color(0xFF05020D), onBackground = Color(0xFFE8D5FF),
        surface = Color(0xFF0D0520), onSurface = Color(0xFFE8D5FF),
        error = Color(0xFFFF5C8A), onError = Color.White
    )
    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFF180A2A),
        heatMapCells = Color(0xFFBE95FF),
        dialogText = Color(0xFFE8D5FF)
    )
    @Composable override fun ThemeObjects(innerPadding: PaddingValues) {}
}

// ---- 4. Forest Monk ------------------------------------------------------------------------------------------------------------------------
class ForestMonkTheme : BaseTheme {
    override val name = "Forest Monk"
    override val description = "Silence and growth"
    override val expandQuestsText = "🌿🌿🌿🌿🌿"
    override fun getRootColorScheme() = darkColorScheme(
        primary = Color(0xFF52B788), onPrimary = Color.Black,
        secondary = Color(0xFF2D6A4F), onSecondary = Color.White,
        tertiary = Color(0xFF74C69D), onTertiary = Color.Black,
        background = Color(0xFF081C15), onBackground = Color(0xFFD8F3DC),
        surface = Color(0xFF0D2818), onSurface = Color(0xFFD8F3DC),
        error = Color(0xFFFF6B6B), onError = Color.White
    )
    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFF1B4332),
        heatMapCells = Color(0xFF52B788),
        dialogText = Color(0xFFD8F3DC)
    )
    @Composable override fun ThemeObjects(innerPadding: PaddingValues) {}
}

// ---- 5. Neon Tokyo --------------------------------------------------------------------------------------------------------------------------
class NeonTokyoTheme : BaseTheme {
    override val name = "Neon Tokyo"
    override val description = "雨の夜の光"
    override val expandQuestsText = "ネオン東京"
    override fun getRootColorScheme() = darkColorScheme(
        primary = Color(0xFFFF0090), onPrimary = Color.Black,
        secondary = Color(0xFF00F5FF), onSecondary = Color.Black,
        tertiary = Color(0xFFFFFF00), onTertiary = Color.Black,
        background = Color(0xFF080010), onBackground = Color(0xFFF0E0FF),
        surface = Color(0xFF100020), onSurface = Color(0xFFF0E0FF),
        error = Color(0xFFFF3366), onError = Color.Black
    )
    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFF1A0030),
        heatMapCells = Color(0xFFFF0090),
        dialogText = Color(0xFFF0E0FF)
    )
    @Composable override fun ThemeObjects(innerPadding: PaddingValues) {}
}

// ---- 6. Desert Gold ------------------------------------------------------------------------------------------------------------------------
class DesertGoldTheme : BaseTheme {
    override val name = "Desert Gold"
    override val description = "Ancient sands"
    override val expandQuestsText = "◈◈◈◈◈"
    override fun getRootColorScheme() = darkColorScheme(
        primary = Color(0xFFFFB627), onPrimary = Color.Black,
        secondary = Color(0xFFE07B39), onSecondary = Color.Black,
        tertiary = Color(0xFFFFD166), onTertiary = Color.Black,
        background = Color(0xFF0D0800), onBackground = Color(0xFFFFF3E0),
        surface = Color(0xFF1A1000), onSurface = Color(0xFFFFF3E0),
        error = Color(0xFFFF4500), onError = Color.White
    )
    override fun getExtraColorScheme() = CustomColor(
        toolBoxContainer = Color(0xFF2A1800),
        heatMapCells = Color(0xFFFFB627),
        dialogText = Color(0xFFFFF3E0)
    )
    @Composable override fun ThemeObjects(innerPadding: PaddingValues) {}
}
