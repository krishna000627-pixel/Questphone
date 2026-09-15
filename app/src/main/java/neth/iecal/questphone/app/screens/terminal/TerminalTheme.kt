package neth.iecal.questphone.app.screens.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// ── Terminal color palette ─────────────────────────────────────────
object TermColors {
    val BG       = Color(0xFF000000)
    val Surface  = Color(0xFF1C1C1C)
    val Green    = Color(0xFF55FF55)
    val GreenDim = Color(0xFF00AA00)
    val Cyan     = Color(0xFF55FFFF)
    val Yellow   = Color(0xFFFFFF55)
    val Red      = Color(0xFFFF5555)
    val White    = Color(0xFFFFFFFF)
    val Gray     = Color(0xFFAAAAAA)
    val Prompt   = Color(0xFF55FF55)
}

data class TermTheme(
    val bg:      Color = TermColors.BG,
    val prompt:  Color = TermColors.Prompt,
    val green:   Color = TermColors.Green,
    val greenDim:Color = TermColors.GreenDim,
    val cyan:    Color = TermColors.Cyan,
    val yellow:  Color = TermColors.Yellow,
    val red:     Color = TermColors.Red,
    val white:   Color = TermColors.White,
    val gray:    Color = TermColors.Gray
)

val DefaultTermTheme = TermTheme()

val TermFont   = FontFamily.Monospace
val TermFontSz = 13.sp
val TermPrompt = "questphone@krishna:~\$ "
