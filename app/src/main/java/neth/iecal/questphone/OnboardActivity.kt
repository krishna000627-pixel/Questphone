package neth.iecal.questphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.onboard.OnBoarderView
import neth.iecal.questphone.app.theme.LauncherTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme

@AndroidEntryPoint(ComponentActivity::class)
class OnboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pitchBlackTheme = PitchBlackTheme()

        setContent {
            LauncherTheme(pitchBlackTheme) {
                Surface {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = RootRoute.OnBoard.route
                    ) {
                        composable(RootRoute.OnBoard.route) {
                            // OnBoarderView handles TOS check internally and
                            // shows TermsScreen itself if not yet accepted
                            OnBoarderView(navController)
                        }
                    }
                }
            }
        }
    }
}
