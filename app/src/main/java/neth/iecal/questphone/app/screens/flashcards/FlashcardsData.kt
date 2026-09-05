package neth.iecal.questphone.app.screens.flashcards

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

// ─── Data Models ─────────────────────────────────────────────────────────────

@Serializable
data class Deck(
    val id: String = UUID.randomUUID().toString(),
    val parentDeckId: String? = null,
    val name: String = "Untitled Deck",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Card(
    val id: String = UUID.randomUUID().toString(),
    val deckId: String = "",
    val front: String = "",
    val back: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** SM-2 scheduling state, kept separate from Card so review logic stays isolated. */
@Serializable
data class ReviewState(
    val cardId: String = "",
    val easeFactor: Double = 2.5,     // SM-2 "E-Factor", min 1.3
    val intervalDays: Int = 0,        // days until next review
    val repetitions: Int = 0,         // consecutive correct reviews
    val dueAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long = 0
)

enum class Grade { AGAIN, HARD, GOOD, EASY }

@Serializable
data class FlashcardsAllData(
    val decks: List<Deck> = emptyList(),
    val cards: List<Card> = emptyList(),
    val reviewStates: List<ReviewState> = emptyList()
)

// ─── SM-2 Scheduler ──────────────────────────────────────────────────────────
// Standard SuperMemo-2 algorithm, as used by Anki's "classic" scheduler.

object SM2Scheduler {

    /** Maps our 4-button UI grade to SM-2's 0-5 quality score. */
    private fun Grade.toQuality(): Int = when (this) {
        Grade.AGAIN -> 0
        Grade.HARD -> 3
        Grade.GOOD -> 4
        Grade.EASY -> 5
    }

    fun review(state: ReviewState, grade: Grade, now: Long = System.currentTimeMillis()): ReviewState {
        val quality = grade.toQuality()

        if (quality < 3) {
            // Failed recall: reset repetitions, short relearn interval, ease unaffected... 
            // (SM-2 keeps EF as-is on failure in the classic formulation)
            return state.copy(
                repetitions = 0,
                intervalDays = 0, // due again same day / next session
                lastReviewedAt = now,
                dueAt = now + TimeUnit.MINUTES.toMillis(10)
            )
        }

        val newEase = max(1.3, state.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
        val newReps = state.repetitions + 1
        val newInterval = when (newReps) {
            1 -> 1
            2 -> 6
            else -> (state.intervalDays * newEase).roundToInt().coerceAtLeast(1)
        }

        return state.copy(
            easeFactor = newEase,
            repetitions = newReps,
            intervalDays = newInterval,
            lastReviewedAt = now,
            dueAt = now + TimeUnit.DAYS.toMillis(newInterval.toLong())
        )
    }

    /** Rough estimate shown on grading buttons, e.g. "10m" / "1d" / "6d". */
    fun previewInterval(state: ReviewState, grade: Grade): String {
        val result = review(state, grade)
        val days = result.intervalDays
        return when {
            days <= 0 -> "10m"
            days == 1 -> "1d"
            days < 30 -> "${days}d"
            days < 365 -> "${(days / 30.0).roundToInt()}mo"
            else -> "${(days / 365.0).roundToInt()}y"
        }
    }
}

// ─── Storage ─────────────────────────────────────────────────────────────────

object FlashcardsStorage {

    private const val PREFS_NAME = "flashcards_prefs"
    private const val KEY_DATA = "flashcards_data"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(ctx: Context): FlashcardsAllData {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, null) ?: return FlashcardsAllData()
        return try { json.decodeFromString(raw) } catch (_: Exception) { FlashcardsAllData() }
    }

    private fun save(ctx: Context, data: FlashcardsAllData) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DATA, json.encodeToString(data)).apply()
        neth.iecal.questphone.backed.sync.SyncTrigger.push()
    }

    // ── Deck operations ──

    fun createDeck(ctx: Context, name: String, parentDeckId: String? = null): Deck {
        val data = load(ctx)
        val deck = Deck(name = name, parentDeckId = parentDeckId)
        save(ctx, data.copy(decks = data.decks + deck))
        return deck
    }

    fun deleteDeck(ctx: Context, deckId: String) {
        val data = load(ctx)
        val toDelete = collectDeckAndDescendants(data.decks, deckId)
        val cardIds = data.cards.filter { it.deckId in toDelete }.map { it.id }.toSet()
        save(ctx, data.copy(
            decks = data.decks.filterNot { it.id in toDelete },
            cards = data.cards.filterNot { it.deckId in toDelete },
            reviewStates = data.reviewStates.filterNot { it.cardId in cardIds }
        ))
    }

    private fun collectDeckAndDescendants(decks: List<Deck>, rootId: String): Set<String> {
        val result = mutableSetOf(rootId)
        var frontier = decks.filter { it.parentDeckId == rootId }.map { it.id }.toSet()
        while (frontier.isNotEmpty()) {
            result += frontier
            frontier = decks.filter { it.parentDeckId in frontier }.map { it.id }.toSet()
        }
        return result
    }

    fun allDecks(ctx: Context): List<Deck> = load(ctx).decks

    // ── Card operations ──

    fun addCard(ctx: Context, deckId: String, front: String, back: String, tags: List<String> = emptyList()): Card {
        val data = load(ctx)
        val card = Card(deckId = deckId, front = front, back = back, tags = tags)
        val state = ReviewState(cardId = card.id, dueAt = System.currentTimeMillis()) // due immediately (new card)
        save(ctx, data.copy(cards = data.cards + card, reviewStates = data.reviewStates + state))
        return card
    }

    fun updateCard(ctx: Context, card: Card) {
        val data = load(ctx)
        save(ctx, data.copy(cards = data.cards.map { if (it.id == card.id) card else it }))
    }

    fun deleteCard(ctx: Context, cardId: String) {
        val data = load(ctx)
        save(ctx, data.copy(
            cards = data.cards.filterNot { it.id == cardId },
            reviewStates = data.reviewStates.filterNot { it.cardId == cardId }
        ))
    }

    fun cardsInDeck(ctx: Context, deckId: String): List<Card> = load(ctx).cards.filter { it.deckId == deckId }

    // ── Review operations ──

    fun dueCards(ctx: Context, deckId: String, now: Long = System.currentTimeMillis()): List<Card> {
        val data = load(ctx)
        val statesByCard = data.reviewStates.associateBy { it.cardId }
        return data.cards.filter { it.deckId == deckId && (statesByCard[it.id]?.dueAt ?: 0) <= now }
    }

    fun dueCountAcrossAllDecks(ctx: Context, now: Long = System.currentTimeMillis()): Int {
        val data = load(ctx)
        val statesByCard = data.reviewStates.associateBy { it.cardId }
        return data.cards.count { (statesByCard[it.id]?.dueAt ?: 0) <= now }
    }

    fun reviewStateFor(ctx: Context, cardId: String): ReviewState =
        load(ctx).reviewStates.firstOrNull { it.cardId == cardId } ?: ReviewState(cardId = cardId)

    /** Grades a card, updates its schedule, and returns coins earned (hook for reward system). */
    fun gradeCard(ctx: Context, cardId: String, grade: Grade, coinsPerReview: Int = 1): Int {
        val data = load(ctx)
        val current = data.reviewStates.firstOrNull { it.cardId == cardId } ?: ReviewState(cardId = cardId)
        val updated = SM2Scheduler.review(current, grade)
        save(ctx, data.copy(reviewStates = data.reviewStates.map { if (it.cardId == cardId) updated else it }
            .let { if (data.reviewStates.none { s -> s.cardId == cardId }) it + updated else it }))
        // Only award coins for a real recall (not "Again")
        return if (grade != Grade.AGAIN) coinsPerReview else 0
    }
}
