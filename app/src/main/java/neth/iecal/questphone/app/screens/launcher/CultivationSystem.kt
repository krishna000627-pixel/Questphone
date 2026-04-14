package neth.iecal.questphone.app.screens.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nethical.questphone.data.xpToLevelUp

// ─── Cultivation Realms (20 total, 9 sub-levels each = 180 cultivation levels) ───
// User's numeric level maps to: realm = (level-1)/9, sublevel = (level-1)%9 + 1

data class CultivationRealm(
    val name: String,
    val color: Color,
    val glowColor: Color,
    val emoji: String,
    val flavorText: String
)

val CULTIVATION_REALMS = listOf(
    CultivationRealm("Mortal Body",       Color(0xFF9E9E9E), Color(0xFF757575), "🪨", "The path begins with a single breath."),
    CultivationRealm("Qi Condensation",   Color(0xFF80CBC4), Color(0xFF4DB6AC), "💧", "Qi gathers within the dantian."),
    CultivationRealm("Foundation Building",Color(0xFF81C784),Color(0xFF4CAF50), "🌱", "The foundation is laid in silence."),
    CultivationRealm("Core Formation",    Color(0xFF64B5F6), Color(0xFF2196F3), "💠", "A golden core pulses with life."),
    CultivationRealm("Nascent Soul",      Color(0xFFBA68C8), Color(0xFF9C27B0), "👁️", "The soul awakens beyond the flesh."),
    CultivationRealm("Soul Transformation",Color(0xFFFF8A65),Color(0xFFFF5722),"🔥", "The self is reforged in heavenly fire."),
    CultivationRealm("Void Refinement",   Color(0xFF4FC3F7), Color(0xFF0288D1), "🌊", "Void and self become one."),
    CultivationRealm("Body Integration",  Color(0xFFA5D6A7), Color(0xFF388E3C), "⚡", "Heaven and body are no longer separate."),
    CultivationRealm("Mahayana",          Color(0xFFFFD54F), Color(0xFFFFC107), "☀️", "One step from transcendence."),
    CultivationRealm("Tribulation Crossing",Color(0xFFEF9A9A),Color(0xFFF44336),"⚡","Face the heavenly tribulation without fear."),
    CultivationRealm("True Immortal",     Color(0xFFFFF176), Color(0xFFFFEB3B), "✨", "Mortality is shed like an old robe."),
    CultivationRealm("Earth Immortal",    Color(0xFFA1887F), Color(0xFF795548), "🌍", "The earth trembles at your step."),
    CultivationRealm("Heaven Immortal",   Color(0xFF90CAF9), Color(0xFF1565C0), "🌌", "Stars bow before your presence."),
    CultivationRealm("Profound Immortal", Color(0xFFCE93D8), Color(0xFF7B1FA2), "🔮", "Mysteries unravel at your gaze."),
    CultivationRealm("Golden Immortal",   Color(0xFFFFCC80), Color(0xFFE65100), "🌟", "Gold flows through your meridians."),
    CultivationRealm("Taiyi Golden Immortal",Color(0xFFFFD700),Color(0xFFFF8F00),"👑","One of the ten thousand chosen."),
    CultivationRealm("Daluo Golden Immortal",Color(0xFFB2EBF2),Color(0xFF006064),"🌠","Beyond the cycle of reincarnation."),
    CultivationRealm("Quasi-Saint",       Color(0xFFE1BEE7), Color(0xFF4A148C), "🌙", "The Dao whispers your name."),
    CultivationRealm("Saint",             Color(0xFFF8BBD0), Color(0xFF880E4F), "🔱", "A Saint walks where gods once feared."),
    CultivationRealm("Dao Ancestor",      Color(0xFFFFFFFF), Color(0xFFE0E0E0), "☯️", "The Dao itself. Boundless. Eternal.")
)

val SUB_LEVEL_NAMES = listOf(
    "Level 1", "Level 2", "Level 3",
    "Level 4", "Level 5", "Level 6",
    "Level 7", "Level 8", "Level 9 — Peak"
)

// ─── Map numeric level → cultivation info ───────────────────────────────────
data class CultivationInfo(
    val realm: CultivationRealm,
    val realmIndex: Int,
    val subLevel: Int,          // 1–9
    val subLevelName: String,
    val nextBreakthrough: String,
    val isMaxRealm: Boolean,
    val xpIntoLevel: Int,
    val xpNeeded: Int
)

fun getCultivationInfo(level: Int, currentXp: Int, xpNeeded: Int): CultivationInfo {
    // Each realm has 9 sub-levels. Realms 0-19, sub-levels 1-9.
    val zeroLevel = (level - 1).coerceAtLeast(0)
    val realmIndex = (zeroLevel / 9).coerceAtMost(CULTIVATION_REALMS.size - 1)
    val subLevel = (zeroLevel % 9) + 1
    val realm = CULTIVATION_REALMS[realmIndex]
    val isMaxRealm = realmIndex >= CULTIVATION_REALMS.size - 1 && subLevel == 9
    val nextRealm = if (realmIndex + 1 < CULTIVATION_REALMS.size) CULTIVATION_REALMS[realmIndex + 1].name else "The Dao"
    val nextBreakthrough = if (subLevel < 9) {
        "${realm.name} ${SUB_LEVEL_NAMES[subLevel]}"
    } else {
        if (!isMaxRealm) "⚡ Breakthrough → $nextRealm" else "☯️ The Dao — Peak of Existence"
    }
    return CultivationInfo(
        realm = realm,
        realmIndex = realmIndex,
        subLevel = subLevel,
        subLevelName = "${realm.name} — ${SUB_LEVEL_NAMES[subLevel - 1]}",
        nextBreakthrough = nextBreakthrough,
        isMaxRealm = isMaxRealm,
        xpIntoLevel = currentXp,
        xpNeeded = xpNeeded
    )
}

// ─── UI Card ─────────────────────────────────────────────────────────────────

@Composable
fun CultivationCard(
    level: Int,
    currentXp: Int,
    streak: Int,
    modifier: Modifier = Modifier
) {
    val xpNeeded = xpToLevelUp(level)
    val info = getCultivationInfo(level, currentXp, xpNeeded)
    val progress = if (xpNeeded > 0) (currentXp.toFloat() / xpNeeded).coerceIn(0f, 1f) else 1f
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800),
        label = "xp_progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        info.realm.color.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(info.realm.color.copy(alpha = 0.6f), info.realm.glowColor.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ── Realm badge + streak ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info.realm.emoji,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = info.realm.name,
                            fontSize = 11.sp,
                            color = info.realm.color,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Level ${SUB_LEVEL_NAMES[info.subLevel - 1]}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // Streak pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF6D00).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$streak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6D00)
                    )
                }
            }

            // ── XP Progress bar ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cultivation Qi",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "$currentXp / $xpNeeded XP",
                        fontSize = 10.sp,
                        color = info.realm.color.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
                LinearProgressIndicator(
                    progress = { animProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = info.realm.color,
                    trackColor = info.realm.color.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
            }

            // ── Next Breakthrough ────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(info.realm.glowColor.copy(alpha = 0.7f))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Next: ${info.nextBreakthrough}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
