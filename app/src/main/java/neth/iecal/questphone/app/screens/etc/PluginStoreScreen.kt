package neth.iecal.questphone.app.screens.etc

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import neth.iecal.questphone.backed.repositories.UserRepository
import java.io.File
import java.net.URL
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

// ─── Data Model ───────────────────────────────────────────────────────────────

@Serializable
data class PluginEntry(
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val category: String = "Utilities",
    val source: String = "playstore", // "playstore", "github", "direct"
    val githubRepo: String = "",
    val apkAssetPattern: String = ".apk",
    val downloadUrl: String = "",
    val autoPlugin: Boolean = false
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class PluginStoreViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val PLUGINS_URL = "https://raw.githubusercontent.com/krishna000627-pixel/Questphone/main/plugins.json"

    private val _plugins = MutableStateFlow<List<PluginEntry>>(emptyList())
    val plugins = _plugins.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _downloadingPkg = MutableStateFlow<String?>(null)
    val downloadingPkg = _downloadingPkg.asStateFlow()

    init { fetchPlugins() }

    private fun fetchPlugins() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val json = withContext(Dispatchers.IO) {
                    URL(PLUGINS_URL).readText()
                }
                _plugins.value = Json { ignoreUnknownKeys = true }.decodeFromString(json)
            } catch (e: Exception) {
                _error.value = "Could not load plugin store. Check your connection."
            } finally {
                _isLoading.value = false
            }
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

    fun getAppIcon(context: android.content.Context, packageName: String) = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) { null }

    fun install(
        context: android.content.Context,
        plugin: PluginEntry,
        onComplete: () -> Unit
    ) {
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
                            // Download APK
                            val apkFile = File(context.cacheDir, "${plugin.packageName}.apk")
                            withContext(Dispatchers.IO) {
                                URL(asset.browser_download_url).openStream().use { input ->
                                    apkFile.outputStream().use { input.copyTo(it) }
                                }
                            }
                            // Trigger install
                            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", apkFile)
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(installIntent)
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
                        val apkFile = File(context.cacheDir, "${plugin.packageName}.apk")
                        withContext(Dispatchers.IO) {
                            URL(plugin.downloadUrl).openStream().use { input ->
                                apkFile.outputStream().use { input.copyTo(it) }
                            }
                        }
                        val apkUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", apkFile)
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
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
                title = {
                    Column {
                        Text("Plugin Store", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace)
                        Text("${plugins.size} plugins available",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
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
                        Text("Loading plugin store…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp)
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

                        PluginCard(
                            plugin = plugin,
                            isInstalled = isInstalled,
                            isPlugin = isPlugin,
                            isDownloading = isDownloading,
                            onInstall = {
                                viewModel.install(context, plugin) {
                                    if (plugin.autoPlugin && viewModel.isInstalled(context, plugin.packageName)) {
                                        viewModel.togglePlugin(plugin.packageName)
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
}

// ─── Plugin Card ──────────────────────────────────────────────────────────────

@Composable
private fun PluginCard(
    plugin: PluginEntry,
    isInstalled: Boolean,
    isPlugin: Boolean,
    isDownloading: Boolean,
    onInstall: () -> Unit,
    onTogglePlugin: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "https://icon.horse/icon/${plugin.packageName}",
                    contentDescription = plugin.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(plugin.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (isPlugin) Text("◈", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(plugin.description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))

                Row(modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category chip
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(plugin.category, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    // Source chip
                    val sourceLabel = when(plugin.source) {
                        "github" -> "GitHub"
                        "direct" -> "Direct"
                        else -> "Play Store"
                    }
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(sourceLabel, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            // Action buttons
            Column(horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {

                // Install / Open button
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInstalled) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            if (isInstalled) "Open" else when(plugin.source) {
                                "playstore" -> "Play Store"
                                else -> "Install"
                            },
                            fontSize = 12.sp,
                            color = if (isInstalled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Plugin toggle (only if installed)
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
    }
}
