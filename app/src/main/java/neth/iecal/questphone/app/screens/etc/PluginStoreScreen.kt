package neth.iecal.questphone.app.screens.etc

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import neth.iecal.questphone.backed.repositories.PluginStoreRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.PluginEntry
import nethical.questphone.data.PluginLifecycleStatus
import java.io.File
import java.net.URL
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class PluginStoreViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val pluginStoreRepository: PluginStoreRepository
) : ViewModel() {

    private val _plugins = MutableStateFlow<List<PluginEntry>>(emptyList())
    val plugins = _plugins.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _downloadingPkg = MutableStateFlow<String?>(null)
    val downloadingPkg = _downloadingPkg.asStateFlow()

    val ownershipTick = pluginStoreRepository.ownershipTick
    val coins = userRepository.coinsState
    val pendingUnlockBlocker = pluginStoreRepository.pendingUnlockBlocker

    fun dismissUnlockBlocker() = pluginStoreRepository.dismissUnlockBlocker()

    fun confirmUnlockAndOpen(context: android.content.Context, entry: PluginEntry) {
        val unlocked = pluginStoreRepository.tryUnlock(entry.packageName, entry.unlockCost, entry.name)
        pluginStoreRepository.dismissUnlockBlocker()
        if (unlocked) {
            context.packageManager.getLaunchIntentForPackage(entry.packageName)?.let { context.startActivity(it) }
        }
    }

    init { fetchPlugins() }

    private fun fetchPlugins() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = pluginStoreRepository.refreshCatalogFromNetwork()
            result.onSuccess { _plugins.value = it }
                .onFailure { _error.value = "Could not load plugin store. Check your connection." }
            _isLoading.value = false
        }
    }

    fun refresh() { fetchPlugins() }

    fun isInstalled(context: android.content.Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) { false }

    fun isPlugin(packageName: String): Boolean =
        userRepository.getPluginPackages().contains(packageName)

    fun togglePlugin(packageName: String) {
        if (isPlugin(packageName)) userRepository.removePluginPackage(packageName)
        else userRepository.addPluginPackage(packageName)
    }

    fun ownershipState(packageName: String) = pluginStoreRepository.getState(packageName)

    fun requestUnlock(entry: PluginEntry) = pluginStoreRepository.requestUnlockBlocker(entry)

    fun reactivate(entry: PluginEntry): Boolean =
        pluginStoreRepository.reactivate(entry.packageName, entry.weeklyUpkeep, entry.name)

    /** No-Re-Download Failed Install Protection (spec §2): reopen the staged APK if present. */
    fun hasValidCachedApk(packageName: String): Boolean =
        pluginStoreRepository.hasValidCachedApk(packageName)

    private fun launchInstaller(context: android.content.Context, apkFile: File) {
        val apkUri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun install(
        context: android.content.Context,
        plugin: PluginEntry,
        onComplete: () -> Unit
    ) {
        // No-Re-Download Failed Install Protection: if a good APK is already staged
        // (e.g. install was cancelled/dismissed last time), just reopen the installer.
        if (plugin.source != "playstore" && pluginStoreRepository.hasValidCachedApk(plugin.packageName)) {
            try {
                launchInstaller(context, pluginStoreRepository.cachedApkFile(plugin.packageName))
            } catch (_: Exception) { }
            onComplete()
            return
        }

        when (plugin.source) {
            "playstore" -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=${plugin.packageName}")))
                } catch (_: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${plugin.packageName}")))
                }
                onComplete()
            }
            "github" -> {
                viewModelScope.launch {
                    _downloadingPkg.value = plugin.packageName
                    try {
                        // Fetch latest release from GitHub API
                        val apiUrl = "https://api.github.com/repos/${plugin.githubRepo}/releases/latest"
                        val releaseJson = withContext(Dispatchers.IO) { URL(apiUrl).readText() }
                        val json = Json { ignoreUnknownKeys = true }

                        @Serializable data class Asset(val name: String = "", val browser_download_url: String = "")
                        @Serializable data class Release(val assets: List<Asset> = emptyList())

                        val release = json.decodeFromString<Release>(releaseJson)
                        val asset = release.assets.firstOrNull {
                            it.name.endsWith(".apk") && it.name.contains(plugin.apkAssetPattern)
                        } ?: release.assets.firstOrNull { it.name.endsWith(".apk") }

                        if (asset != null) {
                            // Immediate Install Trigger (spec §2): download silently to the
                            // isolated staging cache, then invoke the installer as soon as it lands.
                            val apkFile = pluginStoreRepository.cachedApkFile(plugin.packageName)
                            if (apkFile.exists()) apkFile.delete()
                            withContext(Dispatchers.IO) {
                                URL(asset.browser_download_url).openStream().use { input ->
                                    apkFile.outputStream().use { input.copyTo(it) }
                                }
                            }
                            pluginStoreRepository.recordCachedApk(plugin.packageName)
                            launchInstaller(context, apkFile)
                        } else {
                            // Fallback to GitHub releases page
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/${plugin.githubRepo}/releases")))
                        }
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/${plugin.githubRepo}/releases")))
                    } finally {
                        _downloadingPkg.value = null
                        onComplete()
                    }
                }
            }
            "direct" -> {
                viewModelScope.launch {
                    _downloadingPkg.value = plugin.packageName
                    try {
                        val apkFile = pluginStoreRepository.cachedApkFile(plugin.packageName)
                        if (apkFile.exists()) apkFile.delete()
                        withContext(Dispatchers.IO) {
                            URL(plugin.downloadUrl).openStream().use { input ->
                                apkFile.outputStream().use { input.copyTo(it) }
                            }
                        }
                        pluginStoreRepository.recordCachedApk(plugin.packageName)
                        launchInstaller(context, apkFile)
                    } catch (_: Exception) { }
                    finally {
                        _downloadingPkg.value = null
                        onComplete()
                    }
                }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    navController: NavController,
    viewModel: PluginStoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val plugins by viewModel.plugins.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadingPkg by viewModel.downloadingPkg.collectAsState()
    val ownershipTick by viewModel.ownershipTick.collectAsState()
    val pendingUnlockBlocker by viewModel.pendingUnlockBlocker.collectAsState()
    val coins by viewModel.coins.collectAsState()

    val categories = remember(plugins) { listOf("All") + plugins.map { it.category }.distinct().sorted() }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(plugins, selectedCategory, searchQuery) {
        plugins.filter {
            (selectedCategory == "All" || it.category == selectedCategory) &&
            (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
             it.description.contains(searchQuery, ignoreCase = true))
        }
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
                title = { Text("Plugin Store", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search plugins…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // Category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading plugin store…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No plugins found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.packageName }) { plugin ->
                        val isInstalled = viewModel.isInstalled(context, plugin.packageName)
                        val isPlugin = viewModel.isPlugin(plugin.packageName)
                        val isDownloading = downloadingPkg == plugin.packageName
                        // ownershipTick (collected above) forces recomposition on unlock/upkeep changes
                        val ownership = viewModel.ownershipState(plugin.packageName)
                        val hasCached = viewModel.hasValidCachedApk(plugin.packageName)

                        PluginCard(
                            plugin = plugin,
                            isInstalled = isInstalled,
                            isPlugin = isPlugin,
                            isDownloading = isDownloading,
                            isUnlocked = ownership.isUnlocked,
                            status = ownership.status,
                            hasCachedApk = hasCached,
                            onPrimaryAction = {
                                when {
                                    ownership.status == PluginLifecycleStatus.FROZEN -> {
                                        viewModel.reactivate(plugin)
                                    }
                                    isInstalled && !ownership.isUnlocked -> {
                                        viewModel.requestUnlock(plugin)
                                    }
                                    isInstalled -> {
                                        context.packageManager.getLaunchIntentForPackage(plugin.packageName)
                                            ?.let { context.startActivity(it) }
                                    }
                                    else -> {
                                        viewModel.install(context, plugin) {
                                            if (plugin.autoPlugin && viewModel.isInstalled(context, plugin.packageName)) {
                                                viewModel.togglePlugin(plugin.packageName)
                                            }
                                        }
                                    }
                                }
                            },
                            onTogglePlugin = { viewModel.togglePlugin(plugin.packageName) }
                        )
                    }
                }
            }
        }
    }

    // Distraction Blocker Overlay (spec §2.1) — same purchase overlay used by the launcher,
    // also reachable from this screen's own OPEN action.
    pendingUnlockBlocker?.let { entry ->
        PluginUnlockBlockerDialog(
            entry = entry,
            currentCoins = coins,
            onDismiss = { viewModel.dismissUnlockBlocker() },
            onConfirmUnlock = { viewModel.confirmUnlockAndOpen(context, entry) }
        )
    }
}

// ─── Plugin Card ──────────────────────────────────────────────────────────────

@Composable
private fun PluginCard(
    plugin: PluginEntry,
    isInstalled: Boolean,
    isPlugin: Boolean,
    isDownloading: Boolean,
    isUnlocked: Boolean,
    status: PluginLifecycleStatus,
    hasCachedApk: Boolean,
    onPrimaryAction: () -> Unit,
    onTogglePlugin: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon Container: 48x48dp, 12dp rounded corners, Surface Variant background,
            // falls back to a stylized monogram letter if the icon fails to load.
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                var iconFailed by remember(plugin.packageName) { mutableStateOf(false) }
                if (!iconFailed) {
                    AsyncImage(
                        model = "https://icon.horse/icon/${plugin.packageName}",
                        contentDescription = plugin.name,
                        contentScale = ContentScale.Crop,
                        onError = { iconFailed = true },
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(
                        plugin.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Text Information Stack
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(plugin.name, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isPlugin) Text("◈", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${plugin.unlockCost} GC ${plugin.description}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Action Button — dynamic states (spec §1)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary)
                } else {
                    val label = when {
                        status == PluginLifecycleStatus.FROZEN -> "UNFREEZE"
                        isInstalled -> "OPEN"
                        hasCachedApk && plugin.source != "playstore" -> "INSTALL"
                        plugin.source == "playstore" -> "PLAY STORE"
                        else -> "INSTALL"
                    }
                    val isWarning = status == PluginLifecycleStatus.FROZEN
                    Button(
                        onClick = onPrimaryAction,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isWarning -> MaterialTheme.colorScheme.error
                                isInstalled -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(
                            label, fontSize = 12.sp,
                            color = when {
                                isWarning -> MaterialTheme.colorScheme.onError
                                isInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onPrimary
                            }
                        )
                    }
                }

                if (isInstalled) {
                    TextButton(
                        onClick = onTogglePlugin,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            if (isPlugin) "◈ Plugin" else "+ Plugin",
                            fontSize = 11.sp,
                            color = if (isPlugin) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Metadata Badges — full-width, horizontally scrollable so they never overlap
        // the icon or get clipped by the action button column (spec §1: cost, category,
        // XP multiplier, weekly upkeep).
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MetaBadge("${plugin.unlockCost} GC", MaterialTheme.colorScheme.primary)
            MetaBadge(plugin.category, MaterialTheme.colorScheme.onSurfaceVariant)
            MetaBadge("+${plugin.xpBonusPercent}% XP", MaterialTheme.colorScheme.primary)
            MetaBadge(
                "${plugin.weeklyUpkeep} GC/wk",
                if (status == PluginLifecycleStatus.GRACE_PERIOD || status == PluginLifecycleStatus.FROZEN)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (status == PluginLifecycleStatus.GRACE_PERIOD) {
            Text("Payment Due", fontSize = 10.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp))
        }
      }
    }
}

@Composable
private fun MetaBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
        Text(text, fontSize = 10.sp, color = color, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ─── Distraction Blocker Overlay (spec §2.1) ───────────────────────────────────

@Composable
fun PluginUnlockBlockerDialog(
    entry: PluginEntry,
    currentCoins: Int,
    onDismiss: () -> Unit,
    onConfirmUnlock: () -> Unit
) {
    val canAfford = currentCoins >= entry.unlockCost
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(entry.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaBadge("${entry.unlockCost} GC to unlock", MaterialTheme.colorScheme.primary)
                    MetaBadge("+${entry.xpBonusPercent}% XP", MaterialTheme.colorScheme.primary)
                    MetaBadge("${entry.weeklyUpkeep} GC/wk", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (canAfford) "Spend ${entry.unlockCost} GC to unlock this plugin and open it."
                    else "You need ${entry.unlockCost} GC to unlock this plugin. You have $currentCoins.",
                    fontSize = 12.sp,
                    color = if (canAfford) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmUnlock, enabled = canAfford) {
                Text("Unlock", color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
