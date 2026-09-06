package neth.iecal.questphone.app.screens.mylife

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

// ─── Scroll Theme Colors ──────────────────────────────────────────────────────

val ML_Parchment      = Color(0xFFF5E6C8)
val ML_ParchmentDark  = Color(0xFFE8D5A3)
val ML_InkBrown       = Color(0xFF2C1810)
val ML_InkMid         = Color(0xFF4A2C0A)
val ML_Gold           = Color(0xFFC09B40)
val ML_GoldLight      = Color(0xFFD4AF70)
val ML_Border         = Color(0xFF8B5E3C)
val ML_BorderLight    = Color(0xFFB08060)
val ML_Vermillion     = Color(0xFF8B1A1A)
val ML_SaffronDeep    = Color(0xFFB06820)
val ML_FadedInk       = Color(0xFF6B4C2A)
val ML_DividerColor   = Color(0xFFCCAA77)

@Composable
fun ScrollSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8EDD4), Color(0xFFF0DFB0), Color(0xFFEDD89C), Color(0xFFF0DFB0))
                )
            )
    ) {
        // Decorative edge lines
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    BorderStroke(1.5.dp, Brush.verticalGradient(
                        listOf(ML_Border.copy(alpha = 0.4f), ML_Gold.copy(alpha = 0.6f), ML_Border.copy(alpha = 0.4f))
                    ))
                )
        )
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun ScrollDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, ML_Gold, Color.Transparent))
        ))
        Text("  ॐ  ", color = ML_Gold, fontSize = 12.sp, fontFamily = FontFamily.Serif)
        Box(Modifier.weight(1f).height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, ML_Gold, Color.Transparent))
        ))
    }
}

@Composable
fun ScrollCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFAEDD0))
            .border(1.dp, ML_Border.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        content = content
    )
}

// ─── Setup Screen ─────────────────────────────────────────────────────────────

private val setupSteps = listOf(
    "Who are you?",
    "Your roots",
    "Your spirit",
    "Your path",
    "Your essence"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyLifeSetupScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    // Step 0 — Name
    var name by remember { mutableStateOf("") }
    // Step 1 — Family roots
    var fatherName by remember { mutableStateOf("") }
    var motherName by remember { mutableStateOf("") }
    // Step 2 — Spiritual description
    var spiritualDescription by remember { mutableStateOf("") }
    // Step 3 — Dharma + goal
    var dharma by remember { mutableStateOf("") }
    var fiveYearGoal by remember { mutableStateOf("") }
    // Step 4 — Dominant trait
    val traits = listOf("Determined", "Spiritual", "Intellectual", "Creative", "Empathetic", "Resilient")
    var selectedTrait by remember { mutableStateOf("") }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        // Top ornament
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Header
            Text(
                "✦  My Life  ✦",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = ML_Vermillion,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "जीवन की पुस्तक",
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                color = ML_FadedInk,
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(24.dp))
            ScrollDivider()
            Spacer(Modifier.height(8.dp))

            // Step indicator
            Text(
                "Step ${step + 1} of ${setupSteps.size} — ${setupSteps[step]}",
                fontSize = 11.sp,
                color = ML_FadedInk,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (step + 1f) / setupSteps.size },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = ML_Gold,
                trackColor = ML_DividerColor
            )
            Spacer(Modifier.height(24.dp))

            // Step content
            AnimatedContent(
                targetState = step,
                transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
                label = "setup_step"
            ) { currentStep ->
                when (currentStep) {

                    0 -> SetupStepCard(
                        prompt = "What is your name?",
                        sub = "The name your family gave you — your first identity in this birth."
                    ) {
                        ScrollTextField(value = name, onValueChange = { name = it }, label = "Your name")
                    }

                    1 -> SetupStepCard(
                        prompt = "From whom did you come?",
                        sub = "Knowing your roots is the first step of knowing yourself."
                    ) {
                        ScrollTextField(value = fatherName, onValueChange = { fatherName = it }, label = "Father's name")
                        Spacer(Modifier.height(10.dp))
                        ScrollTextField(value = motherName, onValueChange = { motherName = it }, label = "Mother's name")
                    }

                    2 -> SetupStepCard(
                        prompt = "How do you experience the divine?",
                        sub = "Your personal relationship with spirituality, faith, and the unseen. In your own words."
                    ) {
                        ScrollTextField(
                            value = spiritualDescription,
                            onValueChange = { spiritualDescription = it },
                            label = "Your spiritual nature",
                            maxLines = 4
                        )
                    }

                    3 -> SetupStepCard(
                        prompt = "What is your dharma?",
                        sub = "Your life's purpose — the thing you were sent here to do or become."
                    ) {
                        ScrollTextField(
                            value = dharma,
                            onValueChange = { dharma = it },
                            label = "My dharma / life purpose",
                            maxLines = 3
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Where do you see yourself in 5 years?",
                            fontSize = 12.sp,
                            color = ML_InkMid,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(Modifier.height(6.dp))
                        ScrollTextField(
                            value = fiveYearGoal,
                            onValueChange = { fiveYearGoal = it },
                            label = "5-year vision",
                            maxLines = 3
                        )
                    }

                    4 -> SetupStepCard(
                        prompt = "What defines your soul?",
                        sub = "Choose the trait that most deeply describes your inner nature."
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            traits.forEach { trait ->
                                val selected = selectedTrait == trait
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selected) ML_Vermillion else Color(0xFFFFF3D6))
                                        .border(1.dp, if (selected) ML_Vermillion else ML_Border, RoundedCornerShape(4.dp))
                                        .clickable { selectedTrait = trait }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        trait,
                                        color = if (selected) Color.White else ML_InkBrown,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Next / Finish button
            val canProceed = when (step) {
                0 -> name.isNotBlank()
                1 -> fatherName.isNotBlank() || motherName.isNotBlank()
                2 -> true
                3 -> true
                4 -> selectedTrait.isNotBlank()
                else -> true
            }

            val isLast = step == setupSteps.lastIndex

            Button(
                onClick = {
                    if (isLast) {
                        MyLifeStorage.saveProfile(
                            ctx,
                            MyLifeProfile(
                                name = name.trim(),
                                dharma = dharma.trim(),
                                fatherName = fatherName.trim(),
                                motherName = motherName.trim(),
                                dominantTrait = selectedTrait,
                                spiritualDescription = spiritualDescription.trim(),
                                fiveYearGoal = fiveYearGoal.trim(),
                                setupComplete = true
                            )
                        )
                        onComplete()
                    } else {
                        step++
                    }
                },
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ML_Vermillion,
                    contentColor = Color.White,
                    disabledContainerColor = ML_DividerColor
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    if (isLast) "✦  Seal the Scroll  ✦" else "Proceed →",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 1.sp
                )
            }

            if (step > 0) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { step-- },
                    colors = ButtonDefaults.textButtonColors(contentColor = ML_FadedInk)
                ) {
                    Text("← Back", fontFamily = FontFamily.Serif, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Bottom ornament
            Text(
                "॥ श्री गणेशाय नमः ॥",
                fontSize = 11.sp,
                color = ML_FadedInk,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupStepCard(prompt: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScrollCard {
            Text(
                prompt,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = ML_InkBrown
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sub,
                fontSize = 12.sp,
                color = ML_FadedInk,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun ScrollTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, fontFamily = FontFamily.Serif, fontSize = 12.sp, color = ML_FadedInk)
        },
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ML_Gold,
            unfocusedBorderColor = ML_Border,
            focusedTextColor = ML_InkBrown,
            unfocusedTextColor = ML_InkBrown,
            cursorColor = ML_Gold,
            focusedLabelColor = ML_Gold,
            unfocusedLabelColor = ML_FadedInk,
            focusedContainerColor = Color(0xFFFFFAF0),
            unfocusedContainerColor = Color(0xFFFFFAF0)
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = ML_InkBrown
        )
    )
}
