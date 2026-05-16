package neth.iecal.questphone.app.screens.vault

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ── Vault colour palette ──────────────────────────────────────────────────────
private val VaultBg       = Color(0xFF0A0D12)
private val VaultCard     = Color(0xFF121820)
private val VaultAccent   = Color(0xFF00D4AA)
private val VaultDim      = Color(0xFF1E2A35)
private val VaultText     = Color.White
private val VaultSubtext  = Color(0xFF8899AA)

// ── Navigation state ──────────────────────────────────────────────────────────
private sealed class VaultScreen {
    data object Lock       : VaultScreen()
    data object SetPin     : VaultScreen()
    data object List       : VaultScreen()
    data class  View(val item: VaultItem) : VaultScreen()
    data class  Edit(val item: VaultItem?) : VaultScreen()
}

// ── Root Screen ───────────────────────────────────────────────────────────────

@Composable
fun DataVaultScreen(navController: NavController) {
    val context = LocalContext.current
    var screen by remember {
        mutableStateOf<VaultScreen>(
            if (VaultDatabase.isPinSet(context)) VaultScreen.Lock else VaultScreen.SetPin
        )
    }
    var unlockedPin by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }

    fun loadItems() {
        items = VaultDatabase.loadItems(context, unlockedPin) ?: emptyList()
    }

    when (val s = screen) {
        is VaultScreen.SetPin -> {
            SetPinScreen(
                onPinSet = { pin ->
                    VaultDatabase.setPin(context, pin)
                    unlockedPin = pin
                    loadItems()
                    screen = VaultScreen.List
                },
                onBack = { navController.popBackStack() }
            )
        }
        is VaultScreen.Lock -> {
            LockScreen(
                onUnlock = { pin ->
                    if (VaultDatabase.verifyPin(context, pin)) {
                        unlockedPin = pin
                        loadItems()
                        screen = VaultScreen.List
                        true
                    } else false
                },
                onBack = { navController.popBackStack() }
            )
        }
        is VaultScreen.List -> {
            VaultListScreen(
                items = items,
                onAdd = { screen = VaultScreen.Edit(null) },
                onView = { screen = VaultScreen.View(it) },
                onLock = {
                    unlockedPin = ""
                    items = emptyList()
                    screen = VaultScreen.Lock
                },
                onBack = { navController.popBackStack() }
            )
        }
        is VaultScreen.View -> {
            VaultItemDetailScreen(
                item = s.item,
                onEdit = { screen = VaultScreen.Edit(s.item) },
                onDelete = {
                    VaultDatabase.deleteItem(context, unlockedPin, s.item.id)
                    loadItems()
                    screen = VaultScreen.List
                },
                onBack = { screen = VaultScreen.List }
            )
        }
        is VaultScreen.Edit -> {
            VaultItemEditorScreen(
                initial = s.item,
                onSave = { item ->
                    VaultDatabase.upsertItem(context, unlockedPin, item)
                    loadItems()
                    screen = VaultScreen.List
                },
                onDismiss = { screen = VaultScreen.List }
            )
        }
    }
}

// ── Set PIN Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetPinScreen(onPinSet: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                title = { Text("Create Vault PIN", color = VaultAccent, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VaultText)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(VaultBg).padding(padding).padding(32.dp),
            Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            Alignment.CenterHorizontally
        ) {
            Text("🔐", fontSize = 64.sp)
            Text("Secure your Data Vault", fontWeight = FontWeight.Black,
                fontSize = 22.sp, color = VaultText, textAlign = TextAlign.Center)
            Text("Your PIN is used to encrypt all secrets locally.\nWe never see it. There is no recovery.",
                fontSize = 13.sp, color = VaultSubtext, textAlign = TextAlign.Center,
                lineHeight = 20.sp)

            if (error.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(0.15f))
                ) {
                    Text(error, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            VaultTextField(
                value = pin, onValueChange = { pin = it; error = "" },
                label = "PIN (min 4 digits)",
                isPassword = !showPin,
                keyboardType = KeyboardType.NumberPassword
            )
            VaultTextField(
                value = confirm, onValueChange = { confirm = it; error = "" },
                label = "Confirm PIN",
                isPassword = !showPin,
                keyboardType = KeyboardType.NumberPassword
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showPin, onCheckedChange = { showPin = it },
                    colors = CheckboxDefaults.colors(checkedColor = VaultAccent))
                Text("Show PIN", color = VaultSubtext, fontSize = 13.sp,
                    modifier = Modifier.clickable { showPin = !showPin })
            }

            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 digits."
                        pin != confirm -> error = "PINs do not match."
                        else -> onPinSet(pin)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Create Vault", fontWeight = FontWeight.Black,
                    color = VaultBg, fontSize = 15.sp)
            }
        }
    }
}

// ── Lock / Unlock Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockScreen(onUnlock: (String) -> Boolean, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                title = { Text("Data Vault", color = VaultAccent, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VaultText)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(VaultBg).padding(padding).padding(32.dp),
            Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            Alignment.CenterHorizontally
        ) {
            Text("🔒", fontSize = 64.sp)
            Text("Enter your PIN", fontWeight = FontWeight.Black,
                fontSize = 22.sp, color = VaultText)
            if (attempts > 0) {
                Text("Wrong PIN. $attempts failed attempt${if (attempts == 1) "" else "s"}.",
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            VaultTextField(
                value = pin, onValueChange = { pin = it },
                label = "Vault PIN",
                isPassword = !showPin,
                keyboardType = KeyboardType.NumberPassword
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showPin, onCheckedChange = { showPin = it },
                    colors = CheckboxDefaults.colors(checkedColor = VaultAccent))
                Text("Show PIN", color = VaultSubtext, fontSize = 13.sp,
                    modifier = Modifier.clickable { showPin = !showPin })
            }

            Button(
                onClick = {
                    val ok = onUnlock(pin)
                    if (!ok) { attempts++; pin = "" }
                },
                enabled = pin.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Unlock", fontWeight = FontWeight.Black,
                    color = VaultBg, fontSize = 15.sp)
            }
        }
    }
}

// ── Vault List ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultListScreen(
    items: List<VaultItem>,
    onAdd: () -> Unit,
    onView: (VaultItem) -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf<VaultCategory?>(null) }

    val filtered = items.filter { item ->
        val matchesSearch = search.isBlank() ||
            item.title.contains(search, true) ||
            item.category.contains(search, true)
        val matchesCat = filterCategory == null || item.category == filterCategory!!.name
        matchesSearch && matchesCat
    }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                title = {
                    Column {
                        Text("🔐 Data Vault", color = VaultAccent, fontWeight = FontWeight.Black)
                        Text("${items.size} secret${if (items.size != 1) "s" else ""} · encrypted",
                            fontSize = 11.sp, color = VaultSubtext)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VaultText)
                    }
                },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, "Lock", tint = VaultSubtext)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = VaultAccent,
                contentColor = VaultBg,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Secret", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(VaultBg).padding(padding)
        ) {
            // Search
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search secrets…", color = VaultSubtext) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = VaultAccent) },
                trailingIcon = {
                    if (search.isNotEmpty())
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null, tint = VaultAccent)
                        }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccent,
                    unfocusedBorderColor = VaultDim,
                    focusedTextColor = VaultText,
                    unfocusedTextColor = VaultText,
                    cursorColor = VaultAccent
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Category filter row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { filterCategory = null },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultAccent,
                            selectedLabelColor = VaultBg
                        )
                    )
                }
                items(VaultCategory.entries) { cat ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { filterCategory = if (filterCategory == cat) null else cat },
                        label = { Text("${cat.emoji} ${cat.label}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultAccent,
                            selectedLabelColor = VaultBg
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🔐", fontSize = 48.sp)
                        Text(
                            if (items.isEmpty()) "Vault is empty" else "No matches",
                            fontWeight = FontWeight.SemiBold, color = VaultText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (items.isEmpty()) "Add passwords, notes, codes — anything you want locked away."
                            else "Try a different search or category.",
                            color = VaultSubtext, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(filtered, key = { it.id }) { item ->
                        VaultItemCard(item = item, onClick = { onView(item) })
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
}

// ── Vault Item Card ───────────────────────────────────────────────────────────

@Composable
private fun VaultItemCard(item: VaultItem, onClick: () -> Unit) {
    val cat = item.categoryEnum
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultCard)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            Arrangement.spacedBy(12.dp),
            Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(VaultDim),
                Alignment.Center
            ) { Text(cat.emoji, fontSize = 22.sp) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, color = VaultText)
                Text(cat.label, fontSize = 11.sp, color = VaultAccent)
            }

            Icon(Icons.Default.ChevronRight, null, tint = VaultSubtext)
        }
    }
}

// ── Vault Item Detail ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultItemDetailScreen(
    item: VaultItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${item.title}\"?") },
            text = { Text("This secret will be permanently destroyed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                title = { Text(item.title, color = VaultAccent, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VaultText)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = VaultAccent)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(VaultBg).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category badge
            Surface(
                color = VaultDim,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    "${item.categoryEnum.emoji}  ${item.categoryEnum.label}",
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = VaultAccent, fontWeight = FontWeight.Medium, fontSize = 13.sp
                )
            }

            // Content card (hidden by default)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VaultCard)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Content", fontWeight = FontWeight.Bold, color = VaultAccent)
                        IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (revealed) "Hide" else "Reveal",
                                tint = VaultAccent, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = VaultDim)
                    if (revealed) {
                        Text(item.content, color = VaultText, lineHeight = 22.sp,
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("•".repeat(minOf(item.content.length, 24)),
                            color = VaultSubtext, letterSpacing = 3.sp)
                        Text("Tap 👁 to reveal", fontSize = 11.sp, color = VaultSubtext)
                    }
                }
            }

            // Timestamps
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VaultCard)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val created = java.util.Date(item.createdAt)
                    val updated = java.util.Date(item.updatedAt)
                    Text("Created: $created", fontSize = 11.sp, color = VaultSubtext)
                    if (item.updatedAt != item.createdAt)
                        Text("Updated: $updated", fontSize = 11.sp, color = VaultSubtext)
                }
            }
        }
    }
}

// ── Vault Item Editor ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultItemEditorScreen(
    initial: VaultItem?,
    onSave: (VaultItem) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var category by remember {
        mutableStateOf(
            try { VaultCategory.valueOf(initial?.category ?: "") }
            catch (_: Exception) { VaultCategory.NOTE }
        )
    }
    var showContent by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                title = {
                    Text(if (initial == null) "Add Secret" else "Edit Secret",
                        color = VaultAccent, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = VaultText)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                onSave(VaultItem(
                                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                    title = title.trim(),
                                    content = content,
                                    category = category.name,
                                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                                ))
                            }
                        },
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = VaultAccent)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(VaultBg).padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            VaultTextField(
                value = title, onValueChange = { title = it },
                label = "Title *", isPassword = false
            )

            // Category picker
            Text("Category", fontWeight = FontWeight.SemiBold, color = VaultText)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VaultCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text("${cat.emoji} ${cat.label}", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultAccent,
                            selectedLabelColor = VaultBg
                        )
                    )
                }
            }

            // Content field with reveal toggle
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Content *", fontWeight = FontWeight.SemiBold, color = VaultText)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showContent = !showContent }
                    ) {
                        Icon(
                            if (showContent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, Modifier.size(16.dp), tint = VaultAccent
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showContent) "Hide" else "Show",
                            fontSize = 12.sp, color = VaultAccent)
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Password, code, note, anything…", color = VaultSubtext) },
                    visualTransformation = if (showContent) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultAccent,
                        unfocusedBorderColor = VaultDim,
                        focusedTextColor = VaultText,
                        unfocusedTextColor = VaultText,
                        cursorColor = VaultAccent,
                        focusedLabelColor = VaultAccent,
                        unfocusedLabelColor = VaultSubtext
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 4, maxLines = 20
                )
            }

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = VaultDim)
            ) {
                Text(
                    "🔒 All content is encrypted with AES-256-GCM using your PIN.\nData never leaves your device.",
                    Modifier.padding(12.dp),
                    fontSize = 11.sp, color = VaultSubtext, lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Shared text field ─────────────────────────────────────────────────────────

@Composable
private fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = VaultAccent.copy(0.8f)) },
        visualTransformation = if (isPassword) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VaultAccent,
            unfocusedBorderColor = VaultDim,
            focusedTextColor = VaultText,
            unfocusedTextColor = VaultText,
            cursorColor = VaultAccent,
            focusedLabelColor = VaultAccent,
            unfocusedLabelColor = VaultSubtext
        ),
        singleLine = keyboardType == KeyboardType.NumberPassword,
        modifier = Modifier.fillMaxWidth()
    )
}
