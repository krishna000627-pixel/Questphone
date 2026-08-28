package neth.iecal.questphone

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import neth.iecal.questphone.app.screens.components.NeuralMeshAsymmetrical
import neth.iecal.questphone.app.screens.components.NeuralMeshSymmetrical
import neth.iecal.questphone.app.screens.quest.stats.components.HeatMapHomeScreenWrapper
import neth.iecal.questphone.app.theme.customThemes.BaseTheme
import neth.iecal.questphone.app.theme.customThemes.BonsaiTheme
import neth.iecal.questphone.app.theme.customThemes.CherryBlossomsTheme
import neth.iecal.questphone.app.theme.customThemes.HackerTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme
import neth.iecal.questphone.app.theme.customThemes.DeepOceanTheme
import neth.iecal.questphone.app.theme.customThemes.CrimsonDuskTheme
import neth.iecal.questphone.app.theme.customThemes.VoidPurpleTheme
import neth.iecal.questphone.app.theme.customThemes.ForestMonkTheme
import neth.iecal.questphone.app.theme.customThemes.NeonTokyoTheme
import neth.iecal.questphone.app.theme.customThemes.DesertGoldTheme
import neth.iecal.questphone.app.theme.customThemes.CelestialTheme
import neth.iecal.questphone.app.theme.customThemes.SunriseTheme
import neth.iecal.questphone.app.theme.customThemes.ArcticTheme
import neth.iecal.questphone.app.theme.customThemes.GardenTheme
import neth.iecal.questphone.app.theme.customThemes.CandyTheme

val themes: Map<String, BaseTheme> = mapOf(
    "Cherry Blossoms" to CherryBlossomsTheme(),
    "Hacker" to HackerTheme(),
    "Pitch Black" to PitchBlackTheme(),
    "Bonsai" to BonsaiTheme(),
    "Deep Ocean" to DeepOceanTheme(),
    "Crimson Dusk" to CrimsonDuskTheme(),
    "Void Purple" to VoidPurpleTheme(),
    "Forest Monk" to ForestMonkTheme(),
    "Neon Tokyo" to NeonTokyoTheme(),
    "Desert Gold" to DesertGoldTheme(),
    "Celestial" to CelestialTheme(),
    "Sunrise" to SunriseTheme(),
    "Arctic" to ArcticTheme(),
    "Garden" to GardenTheme(),
    "Candy" to CandyTheme()
)

const val HOME_WIDGET_PRICE = 30
var homeWidgets: Map<String, @Composable (Modifier)-> Unit> = mapOf(
    "Heat Map" to { HeatMapHomeScreenWrapper(it) },
    "Neural Mesh Symmetrical" to { NeuralMeshSymmetrical(it) },
    "Neural Mesh ASymmetrical" to { NeuralMeshAsymmetrical(it) },
)