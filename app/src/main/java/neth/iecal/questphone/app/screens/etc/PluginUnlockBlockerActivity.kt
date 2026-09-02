package neth.iecal.questphone.app.screens.etc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import neth.iecal.questphone.backed.repositories.PluginStoreRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

/**
 * Plugin Store spec §2.1 — "First-Open Distraction Blocker Intercept: Regardless of how a
 * plugin is installed (direct APK, Chrome sideload, or Google Play Store), attempting to
 * launch an unpurchased plugin triggers a Distraction Blocker Overlay."
 *
 * The click-based gate in AppListViewModel/PluginStoreScreen only covers launches routed
 * through QuestPhone itself. It cannot catch Play Store's own post-install "Open" button,
 * the system Package Installer's post-install "Open" button, or Chrome's download-complete
 * "Open" notification — those hand off directly to the target app's launcher Activity,
 * bypassing QuestPhone entirely. This Activity is the system-level backstop: AppBlockerService
 * already polls UsageStatsManager to detect *any* app coming to foreground (that's how App
 * Locker/distraction blocking work regardless of launch source), and now also brings this up
 * whenever the detected foreground package is an unpurchased plugin.
 */
@AndroidEntryPoint
class PluginUnlockBlockerActivity : ComponentActivity() {

    @Inject lateinit var pluginStoreRepository: PluginStoreRepository
    @Inject lateinit var userRepository: UserRepository

    companion object {
        const val EXTRA_PACKAGE = "blocked_plugin_pkg"

        fun launch(ctx: Context, pkg: String) {
            val intent = Intent(ctx, PluginUnlockBlockerActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ctx.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val entry = pluginStoreRepository.getCatalogEntry(pkg)

        // Already unlocked (e.g. race with the click-based gate) — nothing to show, let it be.
        if (entry == null || pluginStoreRepository.isUnlocked(pkg)) {
            finish()
            return
        }

        setContent {
            val coins by userRepository.coinsState.collectAsState()
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center) {
                    PluginUnlockBlockerDialog(
                        entry = entry,
                        currentCoins = coins,
                        onDismiss = {
                            // Decline — go home instead of leaving the unpurchased app visible.
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(homeIntent)
                            finish()
                        },
                        onConfirmUnlock = {
                            val unlocked = pluginStoreRepository.tryUnlock(entry.packageName, entry.unlockCost, entry.name)
                            if (unlocked) {
                                packageManager.getLaunchIntentForPackage(entry.packageName)?.let {
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(it)
                                }
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }
}
