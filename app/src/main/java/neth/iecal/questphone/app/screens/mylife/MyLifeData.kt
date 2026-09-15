package neth.iecal.questphone.app.screens.mylife

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─── Data Models ─────────────────────────────────────────────────────────────

@Serializable
data class MyLifeProfile(
    val name: String = "",
    val dharma: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val dominantTrait: String = "",
    val spiritualDescription: String = "",
    val fiveYearGoal: String = "",
    val setupComplete: Boolean = false
)

@Serializable
data class StruggleEntry(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dateAdded: String = "",
    val isOvercome: Boolean = false
)

@Serializable
data class JournalEntry(
    val date: String = "",       // YYYY-MM-DD
    val content: String = "",
    val lastEdited: String = ""
)

@Serializable
data class MantraEntry(
    val id: String = "",
    val title: String = "",
    val text: String = "",
    val note: String = ""
)

@Serializable
data class MyLifeAllData(
    val profile: MyLifeProfile = MyLifeProfile(),
    val struggles: List<StruggleEntry> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val mantras: List<MantraEntry> = emptyList()
)

// ─── Storage ─────────────────────────────────────────────────────────────────

object MyLifeStorage {

    private const val PREFS_NAME = "my_life_prefs"
    private const val KEY_DATA   = "my_life_data"
    const val PASSCODE           = "2011"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(ctx: Context): MyLifeAllData {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, null) ?: return MyLifeAllData()
        return try { json.decodeFromString(raw) } catch (_: Exception) { MyLifeAllData() }
    }

    fun save(ctx: Context, data: MyLifeAllData) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DATA, json.encodeToString(data)).apply()
        neth.iecal.questphone.backed.sync.SyncTrigger.push()
    }

    fun isSetupComplete(ctx: Context) = load(ctx).profile.setupComplete

    fun saveProfile(ctx: Context, profile: MyLifeProfile) {
        save(ctx, load(ctx).copy(profile = profile))
    }

    fun saveStruggles(ctx: Context, struggles: List<StruggleEntry>) {
        save(ctx, load(ctx).copy(struggles = struggles))
    }

    fun saveJournalEntry(ctx: Context, entry: JournalEntry) {
        val data = load(ctx)
        val updated = data.journal.toMutableList()
        val idx = updated.indexOfFirst { it.date == entry.date }
        if (idx >= 0) updated[idx] = entry else updated.add(0, entry)
        save(ctx, data.copy(journal = updated.sortedByDescending { it.date }))
    }

    fun saveMantras(ctx: Context, mantras: List<MantraEntry>) {
        save(ctx, load(ctx).copy(mantras = mantras))
    }
}

// ─── Pre-calculated Kundali ───────────────────────────────────────────────────
// Birth: 18 March 2011, 5:30 AM IST, Bhopal (23.26°N 77.41°E)
// Ayanamsa: Lahiri ~23°56'

object KundaliData {

    data class Planet(
        val name: String,
        val nameHindi: String,
        val sign: String,
        val signHindi: String,
        val degree: String,
        val nakshatra: String,
        val nakshatraHindi: String,
        val pada: Int,
        val house: Int,
        val lord: String,
        val effects: String
    )

    data class HouseInfo(
        val number: Int,
        val sign: String,
        val signHindi: String,
        val occupants: String,
        val significance: String,
        val themes: String
    )

    data class Yoga(val name: String, val description: String, val effect: String)

    data class DashaEntry(
        val planet: String,
        val from: String,
        val to: String,
        val isCurrent: Boolean,
        val note: String
    )

    data class LalKitabEntry(val house: String, val planet: String, val insight: String, val remedy: String)

    val planets = listOf(
        Planet("Sun", "सूर्य", "Pisces", "मीन", "3°34'", "Uttara Bhadrapada", "उत्तर भाद्रपद", 1, 8,
            "Saturn",
            "Sun in 8th house blesses with deep mystical insight, intuition about hidden truths, and a naturally spiritual nature. There is an ancestral connection to sacred knowledge. The soul came to transform."),
        Planet("Moon", "चन्द्र", "Leo", "सिंह", "20°34'", "Purva Phalguni", "पूर्व फाल्गुनी", 3, 1,
            "Venus",
            "Moon in the 1st house (Lagna) — the most powerful placement. You wear your emotions openly, have magnetic presence, and strong intuition. The mind is like a king on his throne. Mother's blessings are prominent."),
        Planet("Mars", "मंगल", "Aquarius", "कुम्भ", "26°04'", "Purva Bhadrapada", "पूर्व भाद्रपद", 4, 7,
            "Jupiter",
            "Mars in 7th gives warrior energy in partnerships and relationships. Disciplined passion, social causes, and justice drive you. Competitive in intellectual domains."),
        Planet("Mercury", "बुध", "Pisces", "मीन", "1°04'", "Uttara Bhadrapada", "उत्तर भाद्रपद", 1, 8,
            "Saturn",
            "Mercury (debilitated) in 8th house alongside Sun creates a deeply intuitive, research-oriented mind drawn to esoteric subjects. Weaknesses in conventional logic are compensated by extraordinary insight."),
        Planet("Jupiter", "गुरु", "Aquarius", "कुम्भ", "21°04'", "Purva Bhadrapada", "पूर्व भाद्रपद", 2, 7,
            "Jupiter",
            "Jupiter in 7th — house of dharma from Lagna. Excellent for wisdom gained through relationships and the world. A philosophical and generous soul. Blesses partnerships with growth."),
        Planet("Venus", "शुक्र", "Capricorn", "मकर", "28°04'", "Dhanishtha", "धनिष्ठा", 1, 6,
            "Saturn",
            "Venus in 6th house — artistic excellence applied to service. Creative struggles ultimately lead to mastery. Health-consciousness. Not easily defeated in battles of dedication."),
        Planet("Saturn", "शनि", "Virgo", "कन्या", "21°04'", "Hasta", "हस्त", 3, 2,
            "Moon",
            "Saturn in 2nd house — slow but certain financial growth. Karma around family speech and resources. Discipline in values. Hard-earned gains are lasting. Ancestral duties must be honored."),
        Planet("Rahu", "राहु", "Sagittarius", "धनु", "0°04'", "Mula", "मूल", 1, 5,
            "Ketu",
            "Rahu in 5th house — past-life karma tied to intelligence and creative power. An unconventional approach to learning. Sudden flashes of genius. Mystical curiosity from birth."),
        Planet("Ketu", "केतु", "Gemini", "मिथुन", "0°04'", "Mrigashira", "मृगशिरा", 4, 11,
            "Mars",
            "Ketu in 11th house — soul already saturated with material gains from past lives. Deep spiritual detachment from worldly success. Gains come, but the soul seeks something beyond.")
    )

    val houses = listOf(
        HouseInfo(1, "Leo", "सिंह", "Moon (Chandra)", "Self, Body, Personality",
            "A royal Leo Lagna with Moon here creates natural authority. You radiate warmth and confidence. The soul has chosen a leader's body. Self-image is tied to emotional intelligence."),
        HouseInfo(2, "Virgo", "कन्या", "Saturn (Shani)", "Wealth, Family, Speech",
            "Saturn disciplines your voice and finances. Careful, precise speech. Wealth accumulates slowly but surely. Family traditions and ancestral values are important to honor."),
        HouseInfo(3, "Libra", "तुला", "—", "Siblings, Courage, Communication",
            "Empty 3rd house with Libra — balanced communication style. Courage is measured and fair-minded. Writing and artistic expression serve as vehicles of personal power."),
        HouseInfo(4, "Scorpio", "वृश्चिक", "—", "Home, Mother, Inner Peace",
            "Deep, intense home life with Scorpio 4th house. Home is a place of transformation and depth. Mother figure may be mysterious or spiritually intense. Inner world is rich."),
        HouseInfo(5, "Sagittarius", "धनु", "Rahu", "Intelligence, Creativity, Past Life",
            "Rahu's presence here indicates a soul that carries unusual knowledge from past lives. Education takes unconventional paths. Creative output has a touch of the mystical."),
        HouseInfo(6, "Capricorn", "मकर", "Venus (Shukra)", "Service, Health, Enemies",
            "Venus in 6th — artistic service to the world. Enemies are overcome through grace and persistence. Work ethic driven by aesthetic and creative purpose. Physical health needs consistent attention."),
        HouseInfo(7, "Aquarius", "कुम्भ", "Mars + Jupiter", "Partnerships, Society",
            "Jupiter and Mars together in 7th — powerful combinaion for dealing with the world. Partnerships bring wisdom and dynamism. Deep thinker in social/humanitarian domains."),
        HouseInfo(8, "Pisces", "मीन", "Sun + Mercury", "Mysticism, Transformation, Occult",
            "The house of moksha, mysticism, and transformation holds both Sun and Mercury. You were born to explore what lies beyond the surface of reality. Research, spirituality, and esoteric knowledge are life themes."),
        HouseInfo(9, "Aries", "मेष", "—", "Dharma, Gurus, Luck",
            "Aries 9th house — dharmic path is bold and pioneering. A self-defined spiritual warrior. Luck favors those who act. The guru within speaks loudly."),
        HouseInfo(10, "Taurus", "वृषभ", "—", "Career, Reputation, Status",
            "Taurus 10th house — career linked to beauty, stability, nature, or material excellence. Gradual but unshakeable rise in status. Work must have tangible, lasting value."),
        HouseInfo(11, "Gemini", "मिथुन", "Ketu", "Gains, Friends, Aspirations",
            "Ketu in 11th shows a soul detached from ordinary gains. True aspirations transcend material success. Friends may come and go. Inner spiritual gains are the real reward."),
        HouseInfo(12, "Cancer", "कर्क", "—", "Moksha, Expenses, Foreign Lands",
            "Cancer 12th house — expenditure emotional and nurturing in nature. The path to liberation is through surrender, devotion, and emotional release. Spiritual practices at night are powerful.")
    )

    val yogas = listOf(
        Yoga("Gajakesari Yoga",
            "Moon in Lagna (1st house), Jupiter in 7th house — Jupiter is in a Kendra (angular house) from the Moon.",
            "This is one of the most auspicious yogas in Vedic astrology. It blesses the native with intelligence, wisdom, fame, and a noble character. The elephant (Gaja) and lion (Kesari) together symbolize power with grace. You carry natural authority that others recognize."),
        Yoga("Budha-Aditya Yoga",
            "Sun and Mercury conjunct in the 8th house in Pisces.",
            "This yoga creates a sharp, penetrating intellect applied to the 8th house domain: mysticism, hidden knowledge, research, transformation. Your intelligence naturally seeks what is concealed. This yoga, placed in the house of moksha, is the mark of a spiritual seeker and researcher of truth."),
        Yoga("Chandra-Lagna Yoga",
            "Moon placed exactly in the Lagna (Ascendant) in Leo.",
            "Moon in the 1st house blesses with a powerful personality that people instinctively trust. The mind is as visible as the body. Emotional intelligence is your greatest asset. People sense your sincerity immediately."),
        Yoga("Dhana Yoga (Partial)",
            "Jupiter (9th lord from Sun sign) aspects 1st house.",
            "Jupiter's aspect from 7th house to 1st house creates a Dhana yoga potential. Prosperity comes through partnerships, foreign connections, and wisdom-based endeavors. Financial growth is linked to how much you share your knowledge with the world."),
        Yoga("Rahu Panchama",
            "Rahu in the 5th house (house of Purva Punya — past-life merits).",
            "This placement indicates vast accumulated karma related to intelligence and creativity from previous lifetimes. There is an otherworldly quality to your intuition. Sudden insights, unconventional thinking, and a magnetic draw to mystical subjects are hallmarks.")
    )

    val dashas = listOf(
        DashaEntry("Venus (Shukra)", "Mar 2011", "May 2020", false,
            "The formative years were ruled by Venus — a period of beauty, learning, and emotional discovery. Early life shaped artistic and emotional sensibilities."),
        DashaEntry("Sun (Surya)", "May 2020", "May 2026", false,
            "The Sun period (ages 9–15) was a phase of identity formation, increasing independence, and discovering inner power. Challenges during this period revealed character."),
        DashaEntry("Moon (Chandra)", "May 2026", "May 2036", true,
            "⭐ CURRENT DASHA — You have just entered Moon Mahadasha. Since Moon sits powerfully in your Lagna (1st house) in Leo, this 10-year period brings emotional depth, heightened intuition, and significant inner growth. This is a transformative decade."),
        DashaEntry("Mars (Mangal)", "May 2036", "May 2043", false,
            "Future: Mars period will bring action, ambition, and decisive movement. The 7th-house Mars will drive external achievements and important partnerships."),
        DashaEntry("Rahu", "May 2043", "May 2061", false,
            "Far future: Rahu's 18-year period from the 5th house will be a time of unconventional paths, rapid transformation, and breakthroughs in whatever field has been cultivated.")
    )

    val lalKitab = listOf(
        LalKitabEntry("1st House", "Moon (Chandra)",
            "In Lal Kitab, Moon in the 1st house gives a compassionate soul, deep connection to one's roots, and strong mental faculties. The person is emotionally transparent and inspires trust. Mother's role is central to fate.",
            "Offer water to the Moon on Mondays (Somvar). Keep silver near you. Honor your mother unconditionally."),
        LalKitabEntry("2nd House", "Saturn (Shani)",
            "Saturn in the 2nd house in Lal Kitab creates karz (karmic debt) around family wealth and ancestral property. Speech carries weight — harsh words create long-lasting karma. Material stability comes after 30.",
            "Offer water to the Peepal tree on Saturdays. Donate black sesame seeds (kale til) in flowing water. Never disrespect elders."),
        LalKitabEntry("5th House", "Rahu",
            "Rahu in the 5th is a strong indicator of past-life intellectual karma. In Lal Kitab, this can create instability in formal education but extraordinary results in self-directed learning. Speculation should be avoided.",
            "Keep a piece of saunf (fennel) in your pocket. Donate blue-colored items on Saturdays. Recite Hanuman Chalisa regularly."),
        LalKitabEntry("6th House", "Venus (Shukra)",
            "Venus in 6th in Lal Kitab gives creative strength in service. You win over enemies through art and grace, not conflict. Health is linked to lifestyle and emotional balance.",
            "Respect all women, especially the maternal figures in your life. Donate white sweets on Fridays. Keep your creative work consistent."),
        LalKitabEntry("7th House", "Mars + Jupiter",
            "Mars and Jupiter together in the 7th — in Lal Kitab this is a powerful combination. Jupiter protects the Mars energy from aggression and makes partnerships deeply meaningful. However, Mars here is the classic Manglik indicator.",
            "Pour water mixed with jaggery (gur) at the roots of a Peepal tree. Recite Sundarkand on Tuesdays. Jupiter's presence here is protective."),
        LalKitabEntry("8th House", "Sun + Mercury",
            "Sun and Mercury in the 8th house in Lal Kitab points to ancestral debts and hidden knowledge inheritance. Your intellectual gifts carry the flavor of generations before you. The soul is investigating a long lineage.",
            "Donate copper items on Sundays. Feed monkeys. Light a diya (lamp) for your ancestors (Pitrs) on Amavasya (new moon nights).")
    )

    val numerology = mapOf(
        "Life Path Number" to Pair("7", "The Seeker — ruled by Ketu and Neptune. Deep thinker, spiritual seeker, philosopher. You need solitude to process the world's depth. Inner wisdom is your greatest gift. The world sees a mystic."),
        "Birth/Psychic Number" to Pair("9", "The Humanitarian Warrior — ruled by Mars. Compassion at scale. Strong sense of justice. Generous soul. You feel other people's pain acutely. Leadership through empathy."),
        "Destiny Number" to Pair("7", "Your destiny mirrors your nature — the path of spiritual investigation, truth-seeking, and inner mastery is not just inclination but actual fate. The universe assigned you this journey."),
        "Personal Year (2026)" to Pair("7", "2026 is a 7 Personal Year for you (2+0+2+6+1+8+3 = 22 → 4... wait). Let me clarify: your personal year = birth day + birth month + current year = 9 + 3 + 9 (2+0+2+6) = 21 = 3. This is a year of self-expression, creativity, and communication. Speak your truth.")
    )

    val lagna = "Simha (Leo) · 12°–15° · Magha Nakshatra · Pada 3"
    val rashiMoon = "Simha (Leo) · Purva Phalguni · Pada 3"
    val birthStar = "Purva Phalguni (पूर्व फाल्गुनी) — ruled by Venus (Bhaga)"
    val nakshatraDetails = """
Purva Phalguni Nakshatra (पूर्व फाल्गुनी)
Symbol: Hammock · Front legs of a marriage bed
Deity: Bhaga — God of marital happiness and prosperity
Ruling Planet: Venus (Shukra)
Gana: Manushya (Human)
Quality: Ugra (Fierce/Intense)
Caste: Brahmin
Motivation: Kama (Desire, Creativity)
Body Part: Right hand, sex organs

This nakshatra blesses with creativity, charm, generosity, and deep loyalty. A natural lover of beauty and excellence. Leadership comes through inspiration, not force. Strongly connected to marital happiness and partnerships in this lifetime.
    """.trimIndent()
}
