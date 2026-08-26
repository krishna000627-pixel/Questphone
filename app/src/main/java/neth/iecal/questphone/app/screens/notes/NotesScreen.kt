package neth.iecal.questphone.app.screens.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Top-level list of pages (Notion-like sidebar, shown full-screen on mobile).
 * Tapping a page opens NotesPageEditor. Long-press for trash/rename.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(navController: NavController, parentPageId: String? = null, parentTitle: String = "Notes") {
    val context = LocalContext.current
    var pages by remember { mutableStateOf(NotesStorage.childPages(context, parentPageId)) }
    var showNewPageDialog by remember { mutableStateOf(false) }
    var newPageTitle by remember { mutableStateOf("") }

    fun refresh() { pages = NotesStorage.childPages(context, parentPageId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(parentTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewPageDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New page")
            }
        }
    ) { padding ->
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No pages yet. Tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pages, key = { it.id }) { page ->
                    Surface(
                        onClick = {
                            navController.navigate("notes_editor/${page.id}")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(page.icon, fontSize = 20.sp)
                            Column(Modifier.weight(1f)) {
                                Text(page.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = {
                                NotesStorage.trashPage(context, page.id)
                                refresh()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Trash", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewPageDialog) {
        AlertDialog(
            onDismissRequest = { showNewPageDialog = false },
            title = { Text("New Page") },
            text = {
                OutlinedTextField(
                    value = newPageTitle,
                    onValueChange = { newPageTitle = it },
                    placeholder = { Text("Page title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = newPageTitle.ifBlank { "Untitled" }
                    val page = NotesStorage.createPage(context, title, parentPageId)
                    newPageTitle = ""
                    showNewPageDialog = false
                    refresh()
                    navController.navigate("notes_editor/${page.id}")
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPageDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Block-based page editor. Each block is its own row; tapping "+" or typing "/"
 * at the start of an empty block opens the block-type picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesPageEditor(navController: NavController, pageId: String) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(NotesStorage.load(context).pages.firstOrNull { it.id == pageId }) }
    var blocks by remember { mutableStateOf(NotesStorage.blocksForPage(context, pageId)) }
    var showBlockPicker by remember { mutableStateOf<Int?>(null) } // position to insert after

    fun refreshBlocks() { blocks = NotesStorage.blocksForPage(context, pageId) }

    if (page == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Page not found") }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = page!!.title,
                        onValueChange = {
                            page = page!!.copy(title = it)
                            NotesStorage.updatePage(context, page!!)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val lastPos = blocks.maxOfOrNull { it.position } ?: -1
                NotesStorage.addBlock(context, pageId, lastPos)
                refreshBlocks()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add block")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(blocks, key = { it.id }) { block ->
                BlockRow(
                    block = block,
                    onChange = { updated ->
                        NotesStorage.updateBlock(context, updated)
                        refreshBlocks()
                    },
                    onDelete = {
                        NotesStorage.deleteBlock(context, pageId, block.id)
                        refreshBlocks()
                    },
                    onSlashCommand = { showBlockPicker = block.position }
                )
            }
        }
    }

    val pickerPos = showBlockPicker
    if (pickerPos != null) {
        BlockTypePicker(
            onSelect = { type ->
                NotesStorage.addBlock(context, pageId, pickerPos, type)
                showBlockPicker = null
                refreshBlocks()
            },
            onDismiss = { showBlockPicker = null }
        )
    }
}

@Composable
private fun BlockRow(
    block: Block,
    onChange: (Block) -> Unit,
    onDelete: () -> Unit,
    onSlashCommand: () -> Unit
) {
    when (block.type) {
        BlockType.DIVIDER -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        BlockType.TODO -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = block.checked, onCheckedChange = { onChange(block.copy(checked = it)) })
            BlockTextField(block, onChange, onSlashCommand, strikethrough = block.checked)
        }
        BlockType.BULLET -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("•", modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurface)
            BlockTextField(block, onChange, onSlashCommand)
        }
        BlockType.HEADING -> BlockTextField(block, onChange, onSlashCommand, isHeading = true)
        BlockType.TOGGLE -> Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(block.copy(collapsed = !block.collapsed)) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (block.collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BlockTextField(block, onChange, onSlashCommand)
        }
        BlockType.CODE -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BlockTextField(block, onChange, onSlashCommand, monospace = true)
        }
        BlockType.TEXT -> BlockTextField(block, onChange, onSlashCommand)
    }
}

@Composable
private fun BlockTextField(
    block: Block,
    onChange: (Block) -> Unit,
    onSlashCommand: () -> Unit,
    isHeading: Boolean = false,
    monospace: Boolean = false,
    strikethrough: Boolean = false
) {
    OutlinedTextField(
        value = block.content,
        onValueChange = { new ->
            if (new == "/" && block.content.isEmpty()) {
                onSlashCommand()
            } else {
                onChange(block.copy(content = new))
            }
        },
        placeholder = { Text("Type '/' for commands...") },
        textStyle = (if (isHeading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge).copy(
            fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
            textDecoration = if (strikethrough) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BlockTypePicker(onSelect: (BlockType) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        BlockType.TEXT to "Text",
        BlockType.HEADING to "Heading",
        BlockType.TODO to "To-do",
        BlockType.BULLET to "Bullet list",
        BlockType.TOGGLE to "Toggle",
        BlockType.CODE to "Code",
        BlockType.DIVIDER to "Divider"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert block") },
        text = {
            Column {
                options.forEach { (type, label) ->
                    TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
