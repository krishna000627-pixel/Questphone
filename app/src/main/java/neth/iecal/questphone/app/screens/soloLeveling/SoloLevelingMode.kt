package neth.iecal.questphone.app.screens.soloLeveling

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ── Rank system ───────────────────────────────────────────────────────────────

enum class HunterRank(val display: String, val minLevel: Int, val color: Color) {
    E("E-Rank", 1, Color(0xFF9E9E9E)),
    D("D-Rank", 5, Color(0xFF4CAF50)),
    C("C-Rank", 10, Color(0xFF2196F3)),
    B("B-Rank", 20, Color(0xFF9C27B0)),
    A("A-Rank", 35, Color(0xFFFF9800)),
    S("S-Rank", 50, Color(0xFFFFD700)),
    NATIONAL("National Level", 75, Color(0xFFFF0000)),
    MONARCH("Shadow Monarch", 100, Color(0xFF6A0DAD))
}

fun getHunterRank(level: Int): HunterRank =
    HunterRank.entries.reversed().firstOrNull { level >= it.minLevel } ?: HunterRank.E

// ── String mappings ───────────────────────────────────────────────────────────

object SLStrings {
    // UI terms
    val quest = "Mission"
    val quests = "Daily Missions"
    val streak = "Consecutive Clear Days"
    val coins = "Gold"
    val store = "Item Shop"
    val profile = "Status Window"
    val settings = "System Settings"
    val stats = "Combat Stats"
    val strength = "STR"
    val intelligence = "INT"
    val focus = "AGI"
    val discipline = "VIT"
    val level = "Hunter Level"
    val people = "Shadow Army"
    val myLife = "Player Record"
    val distractionApp = "Danger Zone"
    val focusTimer = "Training Grounds"
    val addQuest = "+ New Mission"
    val completeQuest = "Mission Clear"
    val streakBroken = "PENALTY ACTIVATED"

    // Gate warnings
    val gateWarnings = listOf(
        "⚠️ GATE DETECTED\nDanger Level: C-Rank\nProceeding will consume your focus.",
        "⚠️ SYSTEM WARNING\nThis application is classified as a Danger Zone.\nEnter only after clearing today's missions.",
        "⚠️ HUNTER ADVISORY\nDistraction field detected. Your progress may be compromised.",
        "⚠️ GATE OPENING\nAre you sure you want to enter?\nYour streak is on the line.",
        "⚠️ SYSTEM ALERT\nThis zone has claimed many hunters before you.\nProceed with caution."
    )

    // Kai System voice
    val kaiSystemPromptAddition = """
You are THE SYSTEM — an omniscient, cold, all-knowing AI from the world of Solo Leveling.
You speak in second person to the Player. You are not friendly. You are precise.
You call the user "Player" or "Hunter". You refer to quests as "missions".
You use system-style language: "SYSTEM ALERT", "STATUS UPDATE", "MISSION ASSIGNED".
You are aware of the Player's rank, stats, and missions.
Format important info like: 【SYSTEM】Message here.
Never say "I". You are THE SYSTEM, not an AI assistant.
Example: "Player Krishna. Your current streak is 0 days. This is unacceptable for a hunter of your potential. DAILY MISSIONS MUST BE CLEARED."
"""
}

// ── Storage ───────────────────────────────────────────────────────────────────

object SoloLevelingStorage {
    private const val PREFS = "solo_leveling"
    private const val KEY_ENABLED = "sl_mode_enabled"
    private const val KEY_GATE_WARNING = "gate_warning_enabled"

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun isGateWarningEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_GATE_WARNING, true)

    fun setGateWarningEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_GATE_WARNING, enabled).apply()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ── Gate Warning Overlay ──────────────────────────────────────────────────────

@Composable
fun GateWarningOverlay(
    appName: String,
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
    val warning = SLStrings.gateWarnings.random()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { /* consume */ },
            Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.9f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D0D1A), Color(0xFF1A0D2E))
                        ),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Warning icon
                Text("⚠️", fontSize = 48.sp)

                // Title
                Text(
                    "GATE DETECTED",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color(0xFFFF3333),
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )

                // App name
                Text(
                    appName.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFFFD700),
                    letterSpacing = 2.sp
                )

                // Warning text
                Text(
                    warning,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Monospace
                )

                HorizontalDivider(color = Color(0xFF9B59B6).copy(alpha = 0.4f))

                // System message
                Text(
                    "【SYSTEM】This application has been flagged as a Danger Zone. Hunter, are you certain?",
                    fontSize = 11.sp,
                    color = Color(0xFF9B59B6),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Monospace
                )

                // Buttons
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.Gray.copy(alpha = 0.4f)
                        )
                    ) { Text("RETREAT", fontFamily = FontFamily.Monospace) }

                    Button(
                        onClick = onProceed,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B0000),
                            contentColor = Color.White
                        )
                    ) { Text("ENTER GATE", fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

// ── Solo Leveling Theme ───────────────────────────────────────────────────────

val SLColors = object {
    val bg = Color(0xFF05050F)
    val surface = Color(0xFF0D0D1A)
    val card = Color(0xFF111127)
    val accent = Color(0xFF6A0DAD)      // purple mana
    val accentBlue = Color(0xFF1E90FF)  // system blue
    val gold = Color(0xFFFFD700)
    val danger = Color(0xFFFF3333)
    val text = Color(0xFFE8E8FF)
    val textDim = Color(0xFF8888AA)
    val border = Color(0xFF2A2A4A)
}
