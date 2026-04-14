package neth.iecal.questphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.onboard.OnBoarderView
import neth.iecal.questphone.app.screens.onboard.subscreens.TermsScreen
import neth.iecal.questphone.app.theme.LauncherTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme

@AndroidEntryPoint(ComponentActivity::class)
class OnboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read ALL flags synchronously before setContent — no async surprises
        val onboardSp = getSharedPreferences("onboard", MODE_PRIVATE)
        val termsSp  = getSharedPreferences("terms",   MODE_PRIVATE)

        val isOnboarded   = onboardSp.getBoolean("onboard",    false)
        val isTosAccepted = termsSp.getBoolean("isAccepted", false)

        // Already onboarded → go straight to MainActivity, skip all UI
        if (isOnboarded) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val pitchBlackTheme = PitchBlackTheme()
        val startDestination = if (!isTosAccepted) RootRoute.TermsScreen.route
                               else RootRoute.OnBoard.route

        setContent {
            val isTosState = remember { mutableStateOf(isTosAccepted) }

            LauncherTheme(pitchBlackTheme) {
                Surface {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(RootRoute.OnBoard.route) {
                            OnBoarderView(navController)
                        }
                        composable(RootRoute.TermsScreen.route) {
                            TermsScreen(isTosState)
                        }
                    }
                }
            }
        }
    }
}
