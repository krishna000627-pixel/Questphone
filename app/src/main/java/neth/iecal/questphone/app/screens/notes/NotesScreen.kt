package neth.iecal.questphone.app.screens.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch

// ─── Page List ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(navController: NavController, parentPageId: String? = null, parentTitle: String = "Notes") {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pages by remember { mutableStateOf(NotesStorage.childPages(context, parentPageId)) }
    var showNewPageDialog by remember { mutableStateOf(false) }
    var newPageTitle by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    fun refresh() { pages = NotesStorage.childPages(context, parentPageId) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh() }
    }

    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else NotesStorage.searchPages(context, searchQuery)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    if (isSearching) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) Text(
                                    "Search pages...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(parentTitle, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) { isSearching = false; searchQuery = "" }
                        else navController.popBackStack()
                    }) {
                        Icon(
                            if (isSearching) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showNewPageDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New page")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val displayList = if (isSearching && searchQuery.isNotBlank()) searchResults else pages

        if (displayList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isSearching) {
                        Text("No results for \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No pages yet", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to create your first page",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = { showNewPageDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New page")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(displayList, key = { it.id }) { page ->
                    PageListItem(
                        page = page,
                        onClick = { navController.navigate("notes_editor/${page.id}") },
                        onDelete = { NotesStorage.trashPage(context, page.id); refresh() }
                    )
                }
            }
        }
    }

    if (showNewPageDialog) {
        AlertDialog(
            onDismissRequest = { showNewPageDialog = false; newPageTitle = "" },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("New page", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(value = newPageTitle, onValueChange = { newPageTitle = it },
                    placeholder = { Text("Untitled") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = newPageTitle.ifBlank { "Untitled" }
                    val page = NotesStorage.createPage(context, title, parentPageId)
                    newPageTitle = ""; showNewPageDialog = false; refresh()
                    navController.navigate("notes_editor/${page.id}")
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPageDialog = false; newPageTitle = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PageListItem(page: Page, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Text(page.icon, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(page.title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
                containerColor = MaterialTheme.colorScheme.surface) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

// ─── Page Editor ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesPageEditor(navController: NavController, pageId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var page by remember { mutableStateOf(NotesStorage.load(context).pages.firstOrNull { it.id == pageId }) }
    var blocks by remember { mutableStateOf(NotesStorage.blocksForPage(context, pageId)) }
    var showBlockPicker by remember { mutableStateOf<Int?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    fun refreshBlocks() { blocks = NotesStorage.blocksForPage(context, pageId) }

    if (page == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Page not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val lastPos = blocks.maxOfOrNull { it.position } ?: -1
                        NotesStorage.addBlock(context, pageId, lastPos)
                        refreshBlocks()
                        scope.launch { listState.animateScrollToItem(blocks.size) }
                    }) { Icon(Icons.Default.Add, contentDescription = "Add block") }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface) {
                            DropdownMenuItem(
                                text = { Text("Delete page", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    NotesStorage.trashPage(context, pageId)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Writeopia-style: big emoji icon + large title at top
            item {
                Column(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp)) {
                    Text(page!!.icon, fontSize = 52.sp, modifier = Modifier.padding(bottom = 12.dp))
                    BasicTextField(
                        value = page!!.title,
                        onValueChange = { newTitle ->
                            page = page!!.copy(title = newTitle)
                            NotesStorage.updatePage(context, page!!)
                        },
                        textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground, lineHeight = 36.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        decorationBox = { inner ->
                            if (page!!.title.isEmpty()) Text("Untitled", fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 12.dp))
                }
            }

            itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                NotionBlock(
                    block = block,
                    onChange = { updated -> NotesStorage.updateBlock(context, updated); refreshBlocks() },
                    onDelete = { NotesStorage.deleteBlock(context, pageId, block.id); refreshBlocks() },
                    onSlashCommand = { showBlockPicker = block.position },
                    onEnterPressed = {
                        val newBlock = NotesStorage.addBlock(context, pageId, block.position)
                        refreshBlocks()
                        scope.launch { listState.animateScrollToItem(index + 2) }
                    }
                )
            }

            item {
                if (blocks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).clickable {
                        NotesStorage.addBlock(context, pageId, -1); refreshBlocks()
                    }, contentAlignment = Alignment.TopStart) {
                        Text("Tap to start writing, or type '/' for blocks…",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp).clickable {
                        val lastPos = blocks.maxOfOrNull { it.position } ?: -1
                        NotesStorage.addBlock(context, pageId, lastPos); refreshBlocks()
                    })
                }
            }
        }
    }

    if (showBlockPicker != null) {
        NotionBlockPicker(
            onSelect = { type ->
                NotesStorage.addBlock(context, pageId, showBlockPicker!!, type)
                showBlockPicker = null; refreshBlocks()
            },
            onDismiss = { showBlockPicker = null }
        )
    }
}

// ─── Block Composable ─────────────────────────────────────────────────────────

@Composable
private fun NotionBlock(block: Block, onChange: (Block) -> Unit, onDelete: () -> Unit,
                        onSlashCommand: () -> Unit, onEnterPressed: () -> Unit) {
    val pad = 20.dp
    when (block.type) {
        BlockType.DIVIDER -> HorizontalDivider(
            modifier = Modifier.padding(horizontal = pad, vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant)

        BlockType.TODO -> Row(modifier = Modifier.fillMaxWidth()
            .padding(horizontal = pad, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = block.checked, onCheckedChange = { onChange(block.copy(checked = it)) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            NotionTextField(block = block, onChange = onChange, onSlashCommand = onSlashCommand,
                onEnterPressed = onEnterPressed, onDelete = onDelete,
                strikethrough = block.checked,
                textColor = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
        }

        BlockType.BULLET -> Row(modifier = Modifier.fillMaxWidth()
            .padding(horizontal = pad, vertical = 2.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.padding(top = 10.dp, end = 10.dp).size(5.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
            NotionTextField(block = block, onChange = onChange, onSlashCommand = onSlashCommand,
                onEnterPressed = onEnterPressed, onDelete = onDelete, modifier = Modifier.weight(1f))
        }

        BlockType.HEADING -> NotionTextField(block = block, onChange = onChange,
            onSlashCommand = onSlashCommand, onEnterPressed = onEnterPressed, onDelete = onDelete,
            fontSize = 20.sp, fontWeight = FontWeight.Bold, placeholder = "Heading",
            modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp).padding(top = 8.dp))

        BlockType.TOGGLE -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(block.copy(collapsed = !block.collapsed)) },
                    modifier = Modifier.size(28.dp)) {
                    Icon(if (block.collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                NotionTextField(block = block, onChange = onChange, onSlashCommand = onSlashCommand,
                    onEnterPressed = onEnterPressed, onDelete = onDelete,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            AnimatedVisibility(visible = !block.collapsed,
                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text("Toggle content here", modifier = Modifier.padding(start = 32.dp, top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        BlockType.CODE -> Surface(modifier = Modifier.fillMaxWidth()
            .padding(horizontal = pad, vertical = 6.dp), shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
            NotionTextField(block = block, onChange = onChange, onSlashCommand = onSlashCommand,
                onEnterPressed = onEnterPressed, onDelete = onDelete,
                fontFamily = FontFamily.Monospace, fontSize = 13.sp, placeholder = "Code…",
                modifier = Modifier.fillMaxWidth().padding(12.dp))
        }

        BlockType.TEXT -> NotionTextField(block = block, onChange = onChange,
            onSlashCommand = onSlashCommand, onEnterPressed = onEnterPressed, onDelete = onDelete,
            modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 2.dp))
    }
}

@Composable
private fun NotionTextField(
    block: Block, onChange: (Block) -> Unit, onSlashCommand: () -> Unit,
    onEnterPressed: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "Type '/' for commands…",
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.Default,
    strikethrough: Boolean = false, textColor: Color = Color.Unspecified
) {
    val resolved = if (textColor == Color.Unspecified) MaterialTheme.colorScheme.onBackground else textColor
    val phColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    BasicTextField(
        value = block.content,
        onValueChange = { new ->
            when {
                new.endsWith("\n") -> { onChange(block.copy(content = new.trimEnd('\n'))); onEnterPressed() }
                new == "/" && block.content.isEmpty() -> onSlashCommand()
                else -> onChange(block.copy(content = new))
            }
        },
        textStyle = TextStyle(fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily,
            color = resolved, textDecoration = if (strikethrough) TextDecoration.LineThrough else TextDecoration.None,
            lineHeight = (fontSize.value * 1.5f).sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onEnterPressed() }),
        decorationBox = { inner ->
            if (block.content.isEmpty()) Text(placeholder,
                style = TextStyle(fontSize = fontSize, fontWeight = fontWeight,
                    fontFamily = fontFamily, color = phColor))
            inner()
        },
        modifier = modifier
    )
}

// ─── Block Picker ─────────────────────────────────────────────────────────────

@Composable
private fun NotionBlockPicker(onSelect: (BlockType) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple(BlockType.TEXT,    "📝", "Text"),
        Triple(BlockType.HEADING, "H",  "Heading"),
        Triple(BlockType.TODO,    "☑️", "To-do"),
        Triple(BlockType.BULLET,  "•",  "Bullet list"),
        Triple(BlockType.TOGGLE,  "▶",  "Toggle"),
        Triple(BlockType.CODE,    "</>","Code"),
        Triple(BlockType.DIVIDER, "—",  "Divider")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Add block", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (type, icon, label) ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(type) }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) {
                            Text(icon, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
