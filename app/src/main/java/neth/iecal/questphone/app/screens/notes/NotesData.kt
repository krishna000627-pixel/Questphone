package neth.iecal.questphone.app.screens.notes

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

// ─── Data Models ─────────────────────────────────────────────────────────────

enum class BlockType {
    TEXT, HEADING, TODO, BULLET, TOGGLE, CODE, DIVIDER
}

@Serializable
data class Block(
    val id: String = UUID.randomUUID().toString(),
    val pageId: String = "",
    val parentBlockId: String? = null,   // for nested toggle children
    val type: BlockType = BlockType.TEXT,
    val content: String = "",
    val checked: Boolean = false,        // for TODO blocks
    val collapsed: Boolean = false,      // for TOGGLE blocks
    val position: Int = 0
)

@Serializable
data class Page(
    val id: String = UUID.randomUUID().toString(),
    val parentPageId: String? = null,    // null = top-level page
    val title: String = "Untitled",
    val icon: String = "📄",
    val position: Int = 0,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class NotesAllData(
    val pages: List<Page> = emptyList(),
    val blocks: List<Block> = emptyList()
)

// ─── Storage ─────────────────────────────────────────────────────────────────

object NotesStorage {

    private const val PREFS_NAME = "notes_prefs"
    private const val KEY_DATA = "notes_data"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(ctx: Context): NotesAllData {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, null) ?: return NotesAllData()
        return try { json.decodeFromString(raw) } catch (_: Exception) { NotesAllData() }
    }

    private fun save(ctx: Context, data: NotesAllData) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DATA, json.encodeToString(data)).apply()
        neth.iecal.questphone.backed.sync.SyncTrigger.push()
    }

    // ── Page operations ──

    fun createPage(ctx: Context, title: String = "Untitled", parentPageId: String? = null, icon: String = "📄"): Page {
        val data = load(ctx)
        val siblingCount = data.pages.count { it.parentPageId == parentPageId && !it.isTrashed }
        val page = Page(title = title, parentPageId = parentPageId, icon = icon, position = siblingCount)
        save(ctx, data.copy(pages = data.pages + page))
        return page
    }

    fun updatePage(ctx: Context, page: Page) {
        val data = load(ctx)
        val updated = data.pages.map { if (it.id == page.id) page.copy(updatedAt = System.currentTimeMillis()) else it }
        save(ctx, data.copy(pages = updated))
    }

    fun trashPage(ctx: Context, pageId: String) {
        val data = load(ctx)
        // trash the page and all descendants
        val toTrash = collectDescendants(data.pages, pageId) + pageId
        val updated = data.pages.map { if (it.id in toTrash) it.copy(isTrashed = true) else it }
        save(ctx, data.copy(pages = updated))
    }

    fun restorePage(ctx: Context, pageId: String) {
        val data = load(ctx)
        val updated = data.pages.map { if (it.id == pageId) it.copy(isTrashed = false) else it }
        save(ctx, data.copy(pages = updated))
    }

    fun deletePagePermanently(ctx: Context, pageId: String) {
        val data = load(ctx)
        val toDelete = collectDescendants(data.pages, pageId) + pageId
        save(ctx, data.copy(
            pages = data.pages.filterNot { it.id in toDelete },
            blocks = data.blocks.filterNot { it.pageId in toDelete }
        ))
    }

    private fun collectDescendants(pages: List<Page>, rootId: String): Set<String> {
        val result = mutableSetOf<String>()
        var frontier = pages.filter { it.parentPageId == rootId }.map { it.id }.toSet()
        while (frontier.isNotEmpty()) {
            result += frontier
            frontier = pages.filter { it.parentPageId in frontier }.map { it.id }.toSet()
        }
        return result
    }

    fun childPages(ctx: Context, parentPageId: String?): List<Page> =
        load(ctx).pages.filter { it.parentPageId == parentPageId && !it.isTrashed }.sortedBy { it.position }

    fun trashedPages(ctx: Context): List<Page> = load(ctx).pages.filter { it.isTrashed }

    fun searchPages(ctx: Context, query: String): List<Page> {
        if (query.isBlank()) return emptyList()
        val data = load(ctx)
        val matchingBlockPageIds = data.blocks.filter { it.content.contains(query, ignoreCase = true) }.map { it.pageId }.toSet()
        return data.pages.filter {
            !it.isTrashed && (it.title.contains(query, ignoreCase = true) || it.id in matchingBlockPageIds)
        }
    }

    // ── Block operations ──

    fun blocksForPage(ctx: Context, pageId: String): List<Block> =
        load(ctx).blocks.filter { it.pageId == pageId }.sortedBy { it.position }

    fun saveBlocks(ctx: Context, pageId: String, blocks: List<Block>) {
        val data = load(ctx)
        val others = data.blocks.filterNot { it.pageId == pageId }
        save(ctx, data.copy(blocks = others + blocks))
        updatePage(ctx, data.pages.first { it.id == pageId })
    }

    fun addBlock(ctx: Context, pageId: String, afterPosition: Int, type: BlockType = BlockType.TEXT): Block {
        val existing = blocksForPage(ctx, pageId)
        val newBlock = Block(pageId = pageId, type = type, position = afterPosition + 1)
        // shift subsequent blocks down
        val shifted = existing.map { if (it.position > afterPosition) it.copy(position = it.position + 1) else it }
        saveBlocks(ctx, pageId, shifted + newBlock)
        return newBlock
    }

    fun updateBlock(ctx: Context, block: Block) {
        val existing = blocksForPage(ctx, block.pageId)
        saveBlocks(ctx, block.pageId, existing.map { if (it.id == block.id) block else it })
    }

    fun deleteBlock(ctx: Context, pageId: String, blockId: String) {
        val existing = blocksForPage(ctx, pageId)
        saveBlocks(ctx, pageId, existing.filterNot { it.id == blockId })
    }

    fun reorderBlocks(ctx: Context, pageId: String, orderedIds: List<String>) {
        val existing = blocksForPage(ctx, pageId).associateBy { it.id }
        val reordered = orderedIds.mapIndexedNotNull { idx, id -> existing[id]?.copy(position = idx) }
        saveBlocks(ctx, pageId, reordered)
    }
}
