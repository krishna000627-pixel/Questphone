package neth.iecal.questphone.app.screens.terminal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.launcher.AppListViewModel

// ── Output line types ──────────────────────────────────────────────
sealed class TermLine {
    data class Input(val text: String)                  : TermLine()
    data class Output(val text: String,
                      val color: androidx.compose.ui.graphics.Color =
                          TermColors.White)             : TermLine()
    data class Prompt(val step: String, val hint: String = "") : TermLine()
    object Blank                                        : TermLine()
}

@Composable
fun TerminalScreen(
    navController: NavController,
    appListViewModel: AppListViewModel = hiltViewModel(),
    vm: TerminalViewModel = hiltViewModel()
) {
    val ctx          = LocalContext.current
    val lines        by vm.lines
    val input        by vm.input
    val focusReq     = remember { FocusRequester() }
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(TermColors.BG)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(bottom = 52.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(lines) { line ->
                when (line) {
                    is TermLine.Input  -> TermInputLine(line.text)
                    is TermLine.Output -> TermOutputLine(line.text, line.color)
                    is TermLine.Prompt -> TermPromptStep(line.step, line.hint)
                    TermLine.Blank     -> Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ── Input row ─────────────────────────────────────────────
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(TermColors.BG)
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = if (vm.wizardActive) "  > " else TermPrompt,
                color = if (vm.wizardActive) TermColors.Yellow else TermColors.Green,
                fontFamily = TermFont, fontSize = TermFontSz
            )
            BasicTextField(
                value = input,
                onValueChange = { vm.input.value = it },
                textStyle = TextStyle(
                    color = TermColors.White,
                    fontFamily = TermFont,
                    fontSize = TermFontSz
                ),
                cursorBrush = SolidColor(TermColors.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    scope.launch {
                        vm.onEnter(ctx, navController, appListViewModel)
                    }
                }),
                modifier = Modifier.weight(1f).focusRequester(focusReq),
                singleLine = true
            )
        }
    }
}

@Composable
private fun TermInputLine(text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TermColors.Green)) { append(TermPrompt) }
            withStyle(SpanStyle(color = TermColors.White)) { append(text) }
        },
        fontFamily = TermFont, fontSize = TermFontSz,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun TermOutputLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text, color = color,
        fontFamily = TermFont, fontSize = TermFontSz,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun TermPromptStep(step: String, hint: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TermColors.Yellow)) { append("  $step") }
            if (hint.isNotBlank()) {
                withStyle(SpanStyle(color = TermColors.Gray)) { append("  # $hint") }
            }
        },
        fontFamily = TermFont, fontSize = TermFontSz,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
