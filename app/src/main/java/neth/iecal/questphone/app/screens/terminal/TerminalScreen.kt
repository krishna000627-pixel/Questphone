package neth.iecal.questphone.app.screens.terminal

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Line types ─────────────────────────────────────────────────────
sealed class TermLine {
    data class Input(val text: String) : TermLine()
    data class Output(val text: String, val color: Color = TermColors.White) : TermLine()
    data class Prompt(val step: String, val hint: String = "") : TermLine()
    object Blank : TermLine()
}

// ── Extra keys — exact Termux default layout ───────────────────────
private val ROW1 = listOf("ESC", "≡", "SCROLL", "HOME", "↑", "END", "PGUP")
private val ROW2 = listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN")

@Composable
fun TerminalScreen(
    navController: NavController,
    vm: TerminalViewModel = hiltViewModel()
) {
    val ctx        = LocalContext.current
    val lines      by vm.lines
    val input      by vm.input
    val sessions   by vm.sessions
    val sessionId   = vm.currentSessionId

    val listState  = rememberLazyListState()
    val focusReq   = remember { FocusRequester() }
    val scope      = rememberCoroutineScope()
    val keyboard   = LocalSoftwareKeyboardController.current

    var drawerOpen by remember { mutableStateOf(false) }
    var ctrlDown   by remember { mutableStateOf(false) }
    var altDown    by remember { mutableStateOf(false) }

    // Auto scroll to bottom on new output
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    LaunchedEffect(Unit) {
        delay(150)
        focusReq.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // ── Main area ─────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = if (drawerOpen) 240.dp else 0.dp)
        ) {
            // Output log
            LazyColumn(
                state   = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                items(lines) { line ->
                    when (line) {
                        is TermLine.Input  -> TermInputLine(line.text)
                        is TermLine.Output -> TermOutputLine(line.text, line.color)
                        is TermLine.Prompt -> TermPromptStep(line.step, line.hint)
                        TermLine.Blank     -> Spacer(Modifier.height(4.dp))
                    }
                }
                // Live input at bottom of list
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = if (vm.wizardActive) "  > " else TermPrompt,
                            color      = if (vm.wizardActive) TermColors.Yellow else TermColors.Green,
                            fontFamily = TermFont,
                            fontSize   = TermFontSz
                        )
                        BasicTextField(
                            value         = input,
                            onValueChange = { vm.input.value = it },
                            modifier      = Modifier
                                .weight(1f)
                                .focusRequester(focusReq),
                            textStyle = TextStyle(
                                color      = TermColors.White,
                                fontFamily = TermFont,
                                fontSize   = TermFontSz
                            ),
                            cursorBrush   = SolidColor(TermColors.Green),
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                scope.launch {
                                    vm.onEnter(ctx, navController)
                                    ctrlDown = false
                                    altDown  = false
                                }
                            })
                        )
                    }
                }
            }

            // ── Extra keys bar — 2 rows exact Termux layout ───────
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .navigationBarsPadding()
            ) {
                listOf(ROW1, ROW2).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            val toggled = (key == "CTRL" && ctrlDown) || (key == "ALT" && altDown)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (toggled) Color(0xFF555555)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        when (key) {
                                            "CTRL"   -> ctrlDown = !ctrlDown
                                            "ALT"    -> altDown  = !altDown
                                            "≡"      -> drawerOpen = !drawerOpen
                                            "↑"      -> vm.historyUp()
                                            "↓"      -> vm.historyDown()
                                            "TAB"    -> vm.onTab(ctx)
                                            "SCROLL" -> scope.launch {
                                                listState.animateScrollToItem(0)
                                            }
                                            "PGUP"   -> scope.launch {
                                                val t = (listState.firstVisibleItemIndex - 8).coerceAtLeast(0)
                                                listState.animateScrollToItem(t)
                                            }
                                            "PGDN"   -> scope.launch {
                                                listState.animateScrollToItem(lines.size)
                                            }
                                            "ESC"    -> {
                                                vm.input.value = ""
                                                vm.onEsc()
                                            }
                                            else     -> {}
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = key,
                                    color      = if (toggled) Color.White else Color(0xFFCCCCCC),
                                    fontSize   = 11.sp,
                                    fontWeight = if (toggled) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = TermFont
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Left drawer ───────────────────────────────────────────
        if (drawerOpen) {
            // Scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { drawerOpen = false }
            )
            // Drawer panel
            Column(
                Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1C1C1C))
                    .navigationBarsPadding()
            ) {
                // Top settings
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚙ Settings",
                        color    = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                drawerOpen = false
                                navController.navigate("launcher_settings/")
                            }
                            .padding(8.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFF333333))

                // Sessions list
                LazyColumn(Modifier.weight(1f)) {
                    items(sessions) { sid ->
                        val selected = sid == sessionId
                        Text(
                            text       = "[$sid]",
                            color      = if (selected) Color.White else Color(0xFF888888),
                            fontSize   = 14.sp,
                            fontFamily = TermFont,
                            modifier   = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) Color(0xFF333333)
                                    else Color.Transparent
                                )
                                .clickable {
                                    vm.switchSession(sid)
                                    drawerOpen = false
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF333333))

                // Bottom buttons
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                ) {
                    TextButton(
                        onClick  = {
                            drawerOpen = false
                            keyboard?.show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "KEYBOARD",
                            color      = Color(0xFFDDDDDD),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick  = {
                            vm.newSession()
                            drawerOpen = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "NEW SESSION",
                            color      = Color(0xFFDDDDDD),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Line renderers ─────────────────────────────────────────────────
@Composable
private fun TermInputLine(text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TermColors.Green)) { append(TermPrompt) }
            withStyle(SpanStyle(color = TermColors.White)) { append(text) }
        },
        fontFamily = TermFont,
        fontSize   = TermFontSz,
        modifier   = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun TermOutputLine(text: String, color: Color) {
    Text(
        text       = text,
        color      = color,
        fontFamily = TermFont,
        fontSize   = TermFontSz,
        modifier   = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun TermPromptStep(step: String, hint: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TermColors.Yellow)) { append("  $step") }
            if (hint.isNotBlank())
                withStyle(SpanStyle(color = TermColors.Gray)) { append("  # $hint") }
        },
        fontFamily = TermFont,
        fontSize   = TermFontSz,
        modifier   = Modifier.padding(vertical = 1.dp)
    )
}
