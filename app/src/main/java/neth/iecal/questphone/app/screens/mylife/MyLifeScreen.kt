package neth.iecal.questphone.app.screens.mylife

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.navigation.NavController
import neth.iecal.questphone.app.navigation.RootRoute
import java.text.SimpleDateFormat
import java.util.*

// ─── Entry Point ─────────────────────────────────────────────────────────────

private sealed class MLScreen {
    object Passcode : MLScreen()
    object Setup    : MLScreen()
    object Main     : MLScreen()
}

@Composable
fun MyLifeScreen(navController: NavController) {
    val ctx = LocalContext.current
    var screen by remember {
        mutableStateOf<MLScreen>(MLScreen.Passcode)
    }
    var authenticated by remember { mutableStateOf(false) }

    when (screen) {
        is MLScreen.Passcode -> MyLifePasscodeScreen(
            onBack = { navController.popBackStack() },
            onSuccess = {
                authenticated = true
                screen = if (MyLifeStorage.isSetupComplete(ctx)) MLScreen.Main else MLScreen.Setup
            }
        )
        is MLScreen.Setup -> MyLifeSetupScreen(
            onComplete = { screen = MLScreen.Main }
        )
        is MLScreen.Main -> MainMyLifeScreen(
            navController = navController,
            onRequestSettings = {
                navController.navigate(RootRoute.MyLifeSettings.route)
            }
        )
    }
}

// ─── Passcode Screen ──────────────────────────────────────────────────────────

@Composable
private fun MyLifePasscodeScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var shake  by remember { mutableStateOf(false) }
    var error  by remember { mutableStateOf(false) }

    LaunchedEffect(shake) {
        if (shake) {
            kotlinx.coroutines.delay(600)
            shake = false
            error = false
            input = ""
        }
    }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon ornament
            Text("𑀩", fontSize = 40.sp, color = ML_Vermillion)
            Spacer(Modifier.height(8.dp))
            Text(
                "My Life",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = ML_InkBrown,
                letterSpacing = 3.sp
            )
            Text(
                "मेरा जीवन",
                fontSize = 13.sp,
                color = ML_FadedInk,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(40.dp))

            Text(
                "Enter Passcode",
                fontSize = 12.sp,
                color = ML_FadedInk,
                fontFamily = FontFamily.Serif,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))

            // Dot indicators
            AnimatedContent(
                targetState = if (shake) "shake" else input.length.toString(),
                label = "dots"
            ) { state ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    when {
                                        error -> ML_Vermillion
                                        i < input.length -> ML_InkBrown
                                        else -> ML_DividerColor
                                    }
                                )
                        )
                    }
                }
            }

            if (error) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Incorrect passcode",
                    fontSize = 11.sp,
                    color = ML_Vermillion,
                    fontFamily = FontFamily.Serif
                )
            }

            Spacer(Modifier.height(28.dp))

            // Numpad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("←", "0", "↩")
            )

            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    row.forEach { key ->
                        PasscodeKey(key) {
                            when (key) {
                                "←" -> { if (input.isNotEmpty()) input = input.dropLast(1) }
                                "↩" -> {
                                    if (input == MyLifeStorage.PASSCODE) {
                                        onSuccess()
                                    } else {
                                        error = true
                                        shake = true
                                    }
                                }
                                else -> {
                                    if (input.length < 4) {
                                        input += key
                                        if (input.length == 4) {
                                            if (input == MyLifeStorage.PASSCODE) onSuccess()
                                            else { error = true; shake = true }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = ML_FadedInk)
            ) {
                Text("← Back", fontFamily = FontFamily.Serif)
            }
        }
    }
}

@Composable
private fun PasscodeKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF8E8))
            .border(1.dp, ML_Border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 20.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            color = ML_InkBrown
        )
    }
}

// ─── Main Tabbed Screen ───────────────────────────────────────────────────────

private val tabs = listOf(
    "📜" to "About",
    "🔯" to "Kundali",
    "🕉" to "Spiritual",
    "⚔" to "Struggles",
    "📔" to "Journal"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainMyLifeScreen(navController: NavController, onRequestSettings: () -> Unit) {
    val ctx = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(MyLifeStorage.load(ctx)) }

    fun reload() { data = MyLifeStorage.load(ctx) }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEDD89C))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ML_InkBrown)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "✦  My Life  ✦",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = ML_Vermillion,
                        letterSpacing = 2.sp
                    )
                    Text(
                        data.profile.name.ifBlank { "मेरा जीवन" },
                        fontSize = 11.sp,
                        color = ML_FadedInk,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRequestSettings) {
                    Icon(Icons.Default.Settings, null, tint = ML_FadedInk)
                }
            }

            // Decorative line
            Box(Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, ML_Gold, ML_Gold, Color.Transparent))
            ))

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFFE8CF90),
                contentColor = ML_InkBrown,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .height(2.5.dp)
                                .background(ML_Vermillion)
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { i, (icon, title) ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(icon, fontSize = 15.sp)
                            Text(
                                title,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Serif,
                                color = if (selectedTab == i) ML_Vermillion else ML_FadedInk,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(ML_DividerColor))

            // Tab content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    0 -> ProfileSection(data.profile)
                    1 -> KundaliSection()
                    2 -> SpiritualSection(data)
                    3 -> StrugglesSection(data.struggles)
                    4 -> JournalSection(data.journal, onSave = { entry ->
                        MyLifeStorage.saveJournalEntry(ctx, entry)
                        reload()
                    })
                }
            }
        }
    }
}

// ─── Profile Section ──────────────────────────────────────────────────────────

@Composable
private fun ProfileSection(profile: MyLifeProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "॥ जीवन परिचय ॥",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = ML_Vermillion,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Profile of the Soul",
                fontSize = 11.sp,
                color = ML_FadedInk,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ScrollDivider()
        }

        // Identity
        item {
            ScrollCard {
                SectionTitle("Identity")
                ProfileRow("Full Name", profile.name.ifBlank { "—" })
                ProfileRow("Date of Birth", "18 March 2011")
                ProfileRow("Birthplace", "Bhopal, Madhya Pradesh")
                ProfileRow("Birth Time", "~5:30 AM IST")
                ProfileRow("Dominant Trait", profile.dominantTrait.ifBlank { "—" })
            }
        }

        // Lineage
        item {
            ScrollCard {
                SectionTitle("Lineage · वंश")
                ProfileRow("Religion", "Hindu (सनातन धर्म)")
                ProfileRow("Caste / Jati", "Brahmin (ब्राह्मण)")
                ProfileRow("Sub-caste / Varna", "Brahman")
                ProfileRow("Gotra", "Vasistha (वसिष्ठ)")
                ProfileRow("Father", profile.fatherName.ifBlank { "—" })
                ProfileRow("Mother", profile.motherName.ifBlank { "—" })
            }
        }

        // Spiritual identity
        item {
            ScrollCard {
                SectionTitle("Spiritual Identity · आध्यात्मिक परिचय")
                ProfileRow("Kuldevi (कुलदेवी)", "Sharda Mata (शारदा माता)")
                ProfileRow("Ishta Dev (इष्ट देव)", "Anantshaktidev\n(Krishna's self-manifested form)")
                ProfileRow("Varna (वर्ण)", "Brahmin")
                if (profile.spiritualDescription.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = ML_DividerColor, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "In your own words:",
                        fontSize = 10.sp,
                        color = ML_FadedInk,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "\"${profile.spiritualDescription}\"",
                        fontSize = 13.sp,
                        color = ML_InkMid,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Dharma & Goals
        item {
            ScrollCard {
                SectionTitle("Dharma & Path · धर्म")
                if (profile.dharma.isNotBlank()) {
                    Text(
                        "Life Purpose:",
                        fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "\"${profile.dharma}\"",
                        fontSize = 13.sp, color = ML_InkBrown, fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (profile.fiveYearGoal.isNotBlank()) {
                    Text(
                        "5-Year Vision:",
                        fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "\"${profile.fiveYearGoal}\"",
                        fontSize = 13.sp, color = ML_InkBrown, fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic, lineHeight = 19.sp
                    )
                }
                if (profile.dharma.isBlank() && profile.fiveYearGoal.isBlank()) {
                    Text("—", color = ML_FadedInk, fontFamily = FontFamily.Serif)
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
internal fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 11.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            value,
            fontSize = 12.sp, color = ML_InkBrown, fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f),
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = ML_DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Serif,
        color = ML_SaffronDeep,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = ML_DividerColor, thickness = 0.5.dp)
    Spacer(Modifier.height(8.dp))
}

// ─── Kundali Section ─────────────────────────────────────────────────────────

@Composable
private fun KundaliSection() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "॥ जन्म कुंडली ॥",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                color = ML_Vermillion, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Birth Chart — Vedic Jyotish (Lahiri Ayanamsa)",
                fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ScrollDivider()
        }

        // Core identity
        item {
            ScrollCard {
                SectionTitle("Core Identity")
                ProfileRow("Lagna (Ascendant)", "Simha · Leo")
                ProfileRow("Lagna Nakshatra", "Magha · Pada 3")
                ProfileRow("Rashi (Moon Sign)", "Simha · Leo")
                ProfileRow("Janma Nakshatra", "Purva Phalguni")
                ProfileRow("Nakshatra Lord", "Shukra (Venus)")
                ProfileRow("Gana", "Manushya (Human)")
                ProfileRow("Birth Star Quality", "Ugra (Fierce, Creative)")
            }
        }

        // Planetary table
        item {
            ScrollCard {
                SectionTitle("Graha Sthiti · Planetary Positions")
                KundaliData.planets.forEach { planet ->
                    PlanetRow(planet)
                }
            }
        }

        // Nakshatra details
        item {
            ScrollCard {
                SectionTitle("Janma Nakshatra · Birth Star")
                Text(
                    "Purva Phalguni (पूर्व फाल्गुनी)",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, color = ML_InkBrown
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    KundaliData.nakshatraDetails,
                    fontSize = 12.sp, color = ML_InkMid, fontFamily = FontFamily.Serif,
                    lineHeight = 20.sp
                )
            }
        }

        // Houses
        item {
            ScrollCard {
                SectionTitle("Bhava Chart · House Analysis")
            }
        }
        items(KundaliData.houses) { house ->
            ScrollCard {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ML_Vermillion),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${house.number}",
                            color = Color.White, fontSize = 13.sp,
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${house.sign} · ${house.signHindi}",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif, color = ML_InkBrown
                            )
                            if (house.occupants != "—") {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ML_Gold.copy(alpha = 0.3f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(house.occupants, fontSize = 9.sp, color = ML_InkBrown, fontFamily = FontFamily.Serif)
                                }
                            }
                        }
                        Text(
                            house.significance,
                            fontSize = 10.sp, color = ML_SaffronDeep,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            house.themes,
                            fontSize = 12.sp, color = ML_InkMid,
                            fontFamily = FontFamily.Serif, lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Yogas
        item {
            ScrollCard {
                SectionTitle("Yoga Vichar · Special Combinations")
            }
        }
        items(KundaliData.yogas) { yoga ->
            ScrollCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(ML_Gold)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            yoga.name,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif, color = ML_InkBrown
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            yoga.description,
                            fontSize = 10.sp, color = ML_FadedInk,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            yoga.effect,
                            fontSize = 12.sp, color = ML_InkMid,
                            fontFamily = FontFamily.Serif, lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Vimshottari Dasha
        item {
            ScrollCard {
                SectionTitle("Vimshottari Dasha · Life Timeline")
                Text(
                    "Based on Moon in Purva Phalguni (Venus nakshatra) — 54% elapsed at birth.",
                    fontSize = 10.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                )
            }
        }
        items(KundaliData.dashas) { dasha ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (dasha.isCurrent) Color(0xFFFFF0C0)
                        else Color(0xFFFAEDD0)
                    )
                    .border(
                        width = if (dasha.isCurrent) 1.5.dp else 0.5.dp,
                        color = if (dasha.isCurrent) ML_Gold else ML_Border.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dasha.planet,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = if (dasha.isCurrent) ML_Vermillion else ML_InkBrown
                        )
                        if (dasha.isCurrent) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ML_Vermillion)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("CURRENT", fontSize = 8.sp, color = Color.White, letterSpacing = 1.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${dasha.from} – ${dasha.to}",
                            fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        dasha.note,
                        fontSize = 11.sp, color = ML_InkMid,
                        fontFamily = FontFamily.Serif, lineHeight = 17.sp
                    )
                }
            }
        }

        // Lal Kitab
        item {
            ScrollCard {
                SectionTitle("लाल किताब · Lal Kitab")
                Text(
                    "Insights from the Red Book of Persian-Vedic astrology. Each placement carries its own karmic debt and remedy.",
                    fontSize = 11.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, lineHeight = 17.sp
                )
            }
        }
        items(KundaliData.lalKitab) { entry ->
            ScrollCard {
                Text(
                    "${entry.house} · ${entry.planet}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, color = ML_InkBrown
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.insight,
                    fontSize = 12.sp, color = ML_InkMid,
                    fontFamily = FontFamily.Serif, lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFFF3C8))
                        .border(0.5.dp, ML_Gold.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Remedy:", fontSize = 9.sp, color = ML_SaffronDeep, letterSpacing = 1.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.remedy,
                            fontSize = 11.sp, color = ML_InkMid,
                            fontFamily = FontFamily.Serif, lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Numerology
        item {
            ScrollCard {
                SectionTitle("Numerology · अंक ज्योतिष")
            }
        }
        items(KundaliData.numerology.entries.toList()) { (key, pair) ->
            val (number, meaning) = pair
            ScrollCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ML_InkBrown),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(number, color = ML_Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(key, fontSize = 11.sp, color = ML_SaffronDeep, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            meaning, fontSize = 11.sp, color = ML_InkMid,
                            fontFamily = FontFamily.Serif, lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun PlanetRow(planet: KundaliData.Planet) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(0.35f)) {
                Text(planet.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, color = ML_InkBrown)
                Text(planet.nameHindi, fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif)
            }
            Column(modifier = Modifier.weight(0.35f)) {
                Text("${planet.sign} ${planet.degree}", fontSize = 11.sp, color = ML_InkMid, fontFamily = FontFamily.Serif)
                Text("H${planet.house} · ${planet.nakshatra} P${planet.pada}", fontSize = 9.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif)
            }
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ML_Gold.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    "Lord: ${planet.lord}",
                    fontSize = 9.sp, color = ML_SaffronDeep, fontFamily = FontFamily.Serif
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            planet.effects,
            fontSize = 11.sp, color = ML_InkMid,
            fontFamily = FontFamily.Serif, lineHeight = 17.sp
        )
        HorizontalDivider(color = ML_DividerColor.copy(alpha = 0.4f), modifier = Modifier.padding(top = 6.dp))
    }
}

// ─── Spiritual Section ───────────────────────────────────────────────────────

@Composable
private fun SpiritualSection(data: MyLifeAllData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "॥ आध्यात्मिक जगत् ॥",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                color = ML_Vermillion, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Spiritual Cosmos — Personal Sacred Universe",
                fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ScrollDivider()
        }

        // Divine connections
        item {
            ScrollCard {
                SectionTitle("Divine Connections · दैवी संबंध")
                Spacer(Modifier.height(4.dp))
                DivineEntry(
                    title = "Ishta Dev · इष्ट देव",
                    subtitle = "Personal Deity",
                    content = "Anantshaktidev (अनंतशक्तिदेव)\n\nA self-manifested form of Shri Krishna — the Infinite Divine Energy, Lord of all Shakti. This is not an idol or an imagined form, but a self-arising divine presence. The soul's primary celestial relationship.",
                    accent = ML_Vermillion
                )
                Spacer(Modifier.height(12.dp))
                DivineEntry(
                    title = "Kuldevi · कुलदेवी",
                    subtitle = "Family Goddess",
                    content = "Sharda Mata (शारदा माता)\n\nGoddess of knowledge, learning, and creative speech. The ancestral protector of the lineage. Her blessings carry through generations. Reciting her prayers aligns the entire family karma.",
                    accent = ML_SaffronDeep
                )
            }
        }

        // Lineage prayer
        item {
            ScrollCard {
                SectionTitle("Sacred Lineage · वंश परम्परा")
                ProfileRow("Gotra", "Vasistha (वसिष्ठ)")
                ProfileRow("Rishi", "Maharishi Vasistha — the divine sage, guru of Rama")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Being of Vasistha Gotra means your lineage descends spiritually from Maharishi Vasistha — one of the Saptarishis (Seven Divine Sages). The Vasistha gotra carries deep Vedic wisdom, mastery of Yoga, and the blessing of clarity. Rishi Vasistha was the cosmic guru who transmitted the Yoga Vasistha — one of the deepest scriptures on consciousness.",
                    fontSize = 12.sp, color = ML_InkMid,
                    fontFamily = FontFamily.Serif, lineHeight = 19.sp
                )
            }
        }

        // Birth chart spiritual significance
        item {
            ScrollCard {
                SectionTitle("Spiritual Chart Indicators")
                Text(
                    "Your chart carries strong spiritual imprints:\n\n" +
                    "• Sun + Mercury in the 8th house (house of moksha) — born to investigate hidden and sacred truths.\n\n" +
                    "• Magha Nakshatra rising — the nakshatra of the Pitrs (ancestors) and royal throne. You carry the weight and blessing of your entire ancestral line.\n\n" +
                    "• Moon in Leo Lagna — the king within. Spirituality for you is self-luminous, not borrowed from others.\n\n" +
                    "• Currently in Moon Mahadasha (2026–2036) — the Moon represents mind, mother, and devotion. This decade is designed for inner deepening.",
                    fontSize = 12.sp, color = ML_InkMid,
                    fontFamily = FontFamily.Serif, lineHeight = 20.sp
                )
            }
        }

        // Mantras
        if (data.mantras.isNotEmpty()) {
            item {
                ScrollCard {
                    SectionTitle("Mantras & Prayers · मंत्र")
                }
            }
            items(data.mantras) { mantra ->
                ScrollCard {
                    Text(
                        mantra.title,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, color = ML_InkBrown
                    )
                    if (mantra.note.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            mantra.note,
                            fontSize = 10.sp, color = ML_FadedInk,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFFF8E8))
                            .border(0.5.dp, ML_Gold.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            mantra.text,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Serif,
                            color = ML_InkBrown,
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            item {
                ScrollCard {
                    SectionTitle("Mantras & Prayers · मंत्र")
                    Text(
                        "No mantras added yet. Go to Settings (⚙) to add your personal mantras and prayers in Sanskrit or Devanagari.",
                        fontSize = 12.sp, color = ML_FadedInk,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, lineHeight = 18.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun DivineEntry(title: String, subtitle: String, content: String, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 60.dp)
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = ML_InkBrown)
            Text(subtitle, fontSize = 10.sp, color = accent, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            Spacer(Modifier.height(6.dp))
            Text(content, fontSize = 12.sp, color = ML_InkMid, fontFamily = FontFamily.Serif, lineHeight = 19.sp)
        }
    }
}

// ─── Struggles Section ───────────────────────────────────────────────────────

@Composable
private fun StrugglesSection(struggles: List<StruggleEntry>) {
    if (struggles.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("⚔", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "No struggles recorded yet.",
                    fontSize = 14.sp, fontFamily = FontFamily.Serif,
                    color = ML_InkBrown, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Go to Settings (⚙) to add your struggles.\nThey are kept here, away from easy reach.",
                    fontSize = 12.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center, lineHeight = 18.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "॥ संघर्ष · Struggles ॥",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                color = ML_Vermillion, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "What I carry — recorded for the soul",
                fontSize = 10.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ScrollDivider()
        }

        val active = struggles.filter { !it.isOvercome }
        val overcome = struggles.filter { it.isOvercome }

        if (active.isNotEmpty()) {
            item {
                Text(
                    "Ongoing",
                    fontSize = 11.sp, color = ML_SaffronDeep,
                    fontFamily = FontFamily.Serif, letterSpacing = 1.sp
                )
            }
            items(active, key = { it.id }) { s -> StruggleCard(s) }
        }

        if (overcome.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Overcome ✓",
                    fontSize = 11.sp, color = Color(0xFF4A7C4A),
                    fontFamily = FontFamily.Serif, letterSpacing = 1.sp
                )
            }
            items(overcome, key = { it.id }) { s -> StruggleCard(s) }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun StruggleCard(entry: StruggleEntry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (entry.isOvercome) Color(0xFFF0F5F0) else Color(0xFFFAEDD0))
            .border(
                0.5.dp,
                if (entry.isOvercome) Color(0xFF8BC88B) else ML_Border.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, color = ML_InkBrown,
                    modifier = Modifier.weight(1f)
                )
                if (entry.isOvercome) {
                    Text("✓", fontSize = 14.sp, color = Color(0xFF4A7C4A))
                }
            }
            if (entry.dateAdded.isNotBlank()) {
                Text(entry.dateAdded, fontSize = 9.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif)
            }
            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.description,
                    fontSize = 12.sp, color = ML_InkMid,
                    fontFamily = FontFamily.Serif, lineHeight = 18.sp
                )
            }
        }
    }
}

// ─── Journal Section ─────────────────────────────────────────────────────────

@Composable
private fun JournalSection(
    entries: List<JournalEntry>,
    onSave: (JournalEntry) -> Unit
) {
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var editingContent by remember { mutableStateOf("") }
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    val todayDisplay = remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
    }

    if (selectedEntry != null) {
        // Journal entry editor
        val entry = selectedEntry!!
        val isNew = entries.none { it.date == entry.date }

        ScrollSurface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEDD89C))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { selectedEntry = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = ML_InkBrown)
                    ) { Text("← Back", fontFamily = FontFamily.Serif) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                            .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entry.date) ?: Date()),
                        fontSize = 12.sp, color = ML_InkBrown, fontFamily = FontFamily.Serif
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            onSave(entry.copy(content = editingContent, lastEdited = now))
                            selectedEntry = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = ML_Vermillion)
                    ) { Text("Save", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ML_DividerColor))

                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    placeholder = {
                        Text(
                            "Write freely... this scroll holds only your truth.",
                            color = ML_FadedInk.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = ML_InkBrown,
                        unfocusedTextColor = ML_InkBrown,
                        cursorColor = ML_Vermillion,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = ML_InkBrown,
                        lineHeight = 24.sp
                    )
                )
            }
        }
        return
    }

    // Journal list view
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "॥ दैनन्दिनी · Journal ॥",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                color = ML_Vermillion, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ScrollDivider()
            Spacer(Modifier.height(8.dp))

            // Today's entry button
            val todayEntry = entries.firstOrNull { it.date == today }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(ML_Vermillion, Color(0xFFB54040))))
                    .clickable {
                        editingContent = todayEntry?.content ?: ""
                        selectedEntry = todayEntry ?: JournalEntry(date = today, content = "", lastEdited = "")
                    }
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "✦ Today · $todayDisplay",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, color = Color.White
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (todayEntry != null) "Continue writing..." else "Begin today's entry...",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Your journal is empty.\nBegin writing and it will remember.",
                    fontSize = 12.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(entries.filter { it.date != today }, key = { it.date }) { entry ->
                JournalEntryCard(entry, onClick = {
                    editingContent = entry.content
                    selectedEntry = entry
                })
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntry, onClick: () -> Unit) {
    val displayDate = remember(entry.date) {
        try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entry.date)
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(d ?: Date())
        } catch (_: Exception) { entry.date }
    }
    val preview = entry.content.take(80).replace('\n', ' ') + if (entry.content.length > 80) "…" else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFAEDD0))
            .border(0.5.dp, ML_Border.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Text(displayDate, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, color = ML_SaffronDeep)
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(preview, fontSize = 11.sp, color = ML_InkMid,
                    fontFamily = FontFamily.Serif, lineHeight = 17.sp)
            }
            if (entry.lastEdited.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Last edited: ${entry.lastEdited}", fontSize = 9.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif)
            }
        }
    }
}
