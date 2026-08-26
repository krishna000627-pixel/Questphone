package neth.iecal.questphone.app.screens.flashcards

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var decks by remember { mutableStateOf(FlashcardsStorage.allDecks(context)) }
    var refreshTick by remember { mutableStateOf(0) } // bump to force badge recompute below
    var showNewDeckDialog by remember { mutableStateOf(false) }
    var newDeckName by remember { mutableStateOf("") }

    fun refresh() {
        decks = FlashcardsStorage.allDecks(context)
        refreshTick++
    }

    // Re-read deck/card counts whenever this screen resumes (e.g. returning from
    // DeckDetailScreen via back button after adding/removing cards).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refresh()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDeckDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New deck")
            }
        }
    ) { padding ->
        if (decks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No decks yet. Tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(decks, key = { it.id }) { deck ->
                    @Suppress("UNUSED_EXPRESSION") refreshTick // read to force recompute below on refresh()
                    val dueCount = FlashcardsStorage.dueCards(context, deck.id).size
                    val totalCount = FlashcardsStorage.cardsInDeck(context, deck.id).size
                    Surface(
                        onClick = { navController.navigate("flashcards_deck/${deck.id}") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(deck.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("$totalCount cards", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (dueCount > 0) {
                                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(20.dp)) {
                                    Text(
                                        "$dueCount due",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewDeckDialog) {
        AlertDialog(
            onDismissRequest = { showNewDeckDialog = false },
            title = { Text("New Deck") },
            text = {
                OutlinedTextField(
                    value = newDeckName,
                    onValueChange = { newDeckName = it },
                    placeholder = { Text("Deck name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newDeckName.isNotBlank()) FlashcardsStorage.createDeck(context, newDeckName)
                    newDeckName = ""
                    showNewDeckDialog = false
                    refresh()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewDeckDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(navController: NavController, deckId: String) {
    val context = LocalContext.current
    var cards by remember { mutableStateOf(FlashcardsStorage.cardsInDeck(context, deckId)) }
    var showNewCardDialog by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    val dueCount = FlashcardsStorage.dueCards(context, deckId).size

    fun refresh() { cards = FlashcardsStorage.cardsInDeck(context, deckId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Deck") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewCardDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New card")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (dueCount > 0) {
                Button(
                    onClick = { navController.navigate("flashcards_review/${deckId}") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Review $dueCount due cards") }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards, key = { it.id }) { card ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(card.front, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(card.back, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showNewCardDialog) {
        AlertDialog(
            onDismissRequest = { showNewCardDialog = false },
            title = { Text("New Card") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = front, onValueChange = { front = it }, placeholder = { Text("Front") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = back, onValueChange = { back = it }, placeholder = { Text("Back") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (front.isNotBlank()) {
                        FlashcardsStorage.addCard(context, deckId, front, back)
                        front = ""; back = ""
                        showNewCardDialog = false
                        refresh()
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showNewCardDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ReviewSessionScreen(navController: NavController, deckId: String) {
    val context = LocalContext.current
    var queue by remember { mutableStateOf(FlashcardsStorage.dueCards(context, deckId)) }
    var index by remember { mutableStateOf(0) }
    var showBack by remember { mutableStateOf(false) }
    var coinsEarned by remember { mutableStateOf(0) }

    val current = queue.getOrNull(index)

    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Session complete! 🎉", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text("Earned $coinsEarned coins", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 16.dp)) { Text("Done") }
            }
        }
        return
    }

    val state = FlashcardsStorage.reviewStateFor(context, current.id)

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.fillMaxSize()) {
            LinearProgressIndicator(
                progress = { index / queue.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                onClick = { showBack = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(targetState = showBack, label = "card_flip") { back ->
                        Text(
                            if (back) current.back else current.front,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!showBack) {
                Button(onClick = { showBack = true }, modifier = Modifier.fillMaxWidth()) { Text("Show Answer") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GradeButton("Again", SM2Scheduler.previewInterval(state, Grade.AGAIN), MaterialTheme.colorScheme.error, Modifier.weight(1f)) {
                        coinsEarned += FlashcardsStorage.gradeCard(context, current.id, Grade.AGAIN)
                        // Failed cards go back to the end of THIS session's queue so they get reviewed again,
                        // instead of silently disappearing when index passes them.
                        queue = queue.toMutableList().also { it.add(current) }
                        index++; showBack = false
                    }
                    GradeButton("Hard", SM2Scheduler.previewInterval(state, Grade.HARD), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) {
                        coinsEarned += FlashcardsStorage.gradeCard(context, current.id, Grade.HARD)
                        index++; showBack = false
                    }
                    GradeButton("Good", SM2Scheduler.previewInterval(state, Grade.GOOD), MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                        coinsEarned += FlashcardsStorage.gradeCard(context, current.id, Grade.GOOD)
                        index++; showBack = false
                    }
                    GradeButton("Easy", SM2Scheduler.previewInterval(state, Grade.EASY), MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                        coinsEarned += FlashcardsStorage.gradeCard(context, current.id, Grade.EASY)
                        index++; showBack = false
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, interval: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
            Text(interval, color = color.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}
