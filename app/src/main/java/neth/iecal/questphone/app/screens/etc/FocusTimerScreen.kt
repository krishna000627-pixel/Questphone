package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

enum class PomodoroPhase { WORK, SHORT_BREAK, LONG_BREAK }

@HiltViewModel
class FocusTimerViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    private val _phase = MutableStateFlow(PomodoroPhase.WORK)
    val phase = _phase.asStateFlow()

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft = _secondsLeft.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount = _sessionCount.asStateFlow()

    private val _coinsEarned = MutableStateFlow(0)
    val coinsEarned = _coinsEarned.asStateFlow()

    private var timerJob: Job? = null

    init { resetTimer() }

    private fun totalSeconds(): Int {
        val u = userRepository.userInfo
        return when (_phase.value) {
            PomodoroPhase.WORK -> u.pomodoroWorkMinutes * 60
            PomodoroPhase.SHORT_BREAK -> u.pomodoroBreakMinutes * 60
            PomodoroPhase.LONG_BREAK -> u.pomodoroBreakMinutes * 60 * 3
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _isRunning.value = false
        _secondsLeft.value = totalSeconds()
    }

    fun toggleTimer() {
        if (_isRunning.value) {
            timerJob?.cancel()
            _isRunning.value = false
        } else {
            _isRunning.value = true
            timerJob = viewModelScope.launch {
                while (_secondsLeft.value > 0) {
                    delay(1000)
                    _secondsLeft.value--
                }
                onPhaseComplete()
            }
        }
    }

    private fun onPhaseComplete() {
        _isRunning.value = false
        if (_phase.value == PomodoroPhase.WORK) {
            // Award coins
            val reward = userRepository.userInfo.pomodoroCoinReward
            userRepository.addCoins(reward)
            _coinsEarned.value += reward
            _sessionCount.value++
            // Alternate work/break
            _phase.value = if (_sessionCount.value % 4 == 0) PomodoroPhase.LONG_BREAK
                           else PomodoroPhase.SHORT_BREAK
        } else {
            _phase.value = PomodoroPhase.WORK
        }
        resetTimer()
    }

    fun skipPhase() { timerJob?.cancel(); onPhaseComplete() }

    override fun onCleared() { timerJob?.cancel(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(
    navController: NavController,
    vm: FocusTimerViewModel = hiltViewModel()
) {
    val phase by vm.phase.collectAsState()
    val secondsLeft by vm.secondsLeft.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val sessionCount by vm.sessionCount.collectAsState()
    val coinsEarned by vm.coinsEarned.collectAsState()
    val u = vm.userRepository.userInfo

    val totalSecs = when (phase) {
        PomodoroPhase.WORK -> u.pomodoroWorkMinutes * 60
        PomodoroPhase.SHORT_BREAK -> u.pomodoroBreakMinutes * 60
        PomodoroPhase.LONG_BREAK -> u.pomodoroBreakMinutes * 60 * 3
    }.coerceAtLeast(1)

    val progress = secondsLeft.toFloat() / totalSecs
    val animProgress by animateFloatAsState(progress, tween(500), label = "p")

    val phaseColor = when (phase) {
        PomodoroPhase.WORK -> Color(0xFFEF5350)
        PomodoroPhase.SHORT_BREAK -> Color(0xFF4CAF50)
        PomodoroPhase.LONG_BREAK -> Color(0xFF2196F3)
    }
    val phaseLabel = when (phase) {
        PomodoroPhase.WORK -> "FOCUS"
        PomodoroPhase.SHORT_BREAK -> "SHORT BREAK"
        PomodoroPhase.LONG_BREAK -> "LONG BREAK"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Timer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("<", color = Color.White, fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Session dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier.size(10.dp).background(
                            if (i < sessionCount % 4) phaseColor else Color(0xFF333333),
                            CircleShape
                        )
                    )
                }
            }

            // Circular timer
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                Canvas(modifier = Modifier.size(260.dp)) {
                    val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(Color(0xFF1A1A1A), 0f, 360f, false, style = stroke)
                    drawArc(phaseColor, -90f, animProgress * 360f, false, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Thin,
                        color = Color.White
                    )
                    Text(phaseLabel, fontSize = 12.sp, color = phaseColor, letterSpacing = 2.sp)
                }
            }

            // Coins earned this session
            if (coinsEarned > 0) {
                Text("+$coinsEarned 🪙 earned today", fontSize = 13.sp, color = Color(0xFFFFAB40))
            }

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { vm.resetTimer() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Reset") }

                Button(
                    onClick = { vm.toggleTimer() },
                    colors = ButtonDefaults.buttonColors(containerColor = phaseColor),
                    modifier = Modifier.width(120.dp)
                ) { Text(if (isRunning) "Pause" else "Start", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = { vm.skipPhase() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888))
                ) { Text("Skip") }
            }

            // Settings row
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = Color(0xFF1A1A1A))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Work", fontSize = 13.sp, color = Color(0xFF888888))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (u.pomodoroWorkMinutes > 5) { u.pomodoroWorkMinutes -= 5; vm.resetTimer() } }) {
                            Text("-", color = phaseColor)
                        }
                        Text("${u.pomodoroWorkMinutes}m", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { if (u.pomodoroWorkMinutes < 60) { u.pomodoroWorkMinutes += 5; vm.resetTimer() } }) {
                            Text("+", color = phaseColor)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Break", fontSize = 13.sp, color = Color(0xFF888888))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (u.pomodoroBreakMinutes > 1) { u.pomodoroBreakMinutes -= 1; vm.resetTimer() } }) {
                            Text("-", color = phaseColor)
                        }
                        Text("${u.pomodoroBreakMinutes}m", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { if (u.pomodoroBreakMinutes < 30) { u.pomodoroBreakMinutes += 1; vm.resetTimer() } }) {
                            Text("+", color = phaseColor)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Coins per session", fontSize = 13.sp, color = Color(0xFF888888))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (u.pomodoroCoinReward > 0) u.pomodoroCoinReward -= 5 }) {
                            Text("-", color = phaseColor)
                        }
                        Text("${u.pomodoroCoinReward} 🪙", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { u.pomodoroCoinReward += 5 }) {
                            Text("+", color = phaseColor)
                        }
                    }
                }
            }
        }
    }
}
