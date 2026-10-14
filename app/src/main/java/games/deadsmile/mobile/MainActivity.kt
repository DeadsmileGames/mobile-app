package games.deadsmile.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import games.deadsmile.mobile.data.AppViewModel
import games.deadsmile.mobile.data.FeedItem
import games.deadsmile.mobile.data.UiState
import games.deadsmile.mobile.data.PrivacyOptions
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private enum class Page(val label: String) {
    HOME("Início"), GAMES("Jogos"), NEWS("Notícias"), VIDEOS("Vídeos"), LIBRARY("Biblioteca"),
    ACCOUNT("Conta"), WISHLIST("Lista de desejos"), SETTINGS("Configurações"), DETAILS("Detalhes")
}

private val BrandInk = Color(0xFF111019)
private val BrandViolet = Color(0xFF8173FF)
private val BrandRose = Color(0xFFFF647C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DeadsmileApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadsmileApp(vm: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE) }
    var themeMode by rememberSaveable { mutableIntStateOf(prefs.getInt("theme_mode", 0)) }
    var dynamicColors by rememberSaveable { mutableStateOf(prefs.getBoolean("dynamic_colors", true)) }
    var accentPalette by rememberSaveable { mutableIntStateOf(prefs.getInt("accent_palette", 0).coerceIn(0, 3)) }
    var reduceMotion by rememberSaveable { mutableStateOf(prefs.getBoolean("reduce_motion", false)) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dynamicColors && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 && dynamicColors -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = listOf(Color(0xFFB8AEFF), Color(0xFFFF9DB0), Color(0xFF8AD8CF), Color(0xFF9CBFFF))[accentPalette],
            secondary = listOf(Color(0xFFFF91A2), Color(0xFFB8AEFF), Color(0xFFFFB3C0), Color(0xFF91D9D0))[accentPalette],
            tertiary = Color(0xFF91D9D0)
        )
        else -> lightColorScheme(
            primary = listOf(Color(0xFF5B4BCC), Color(0xFFB4234F), Color(0xFF176B65), Color(0xFF2458B8))[accentPalette],
            secondary = listOf(Color(0xFFB4234F), Color(0xFF5B4BCC), Color(0xFF8C3655), Color(0xFF176B65))[accentPalette],
            tertiary = Color(0xFF176B65)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DeadsmileRoot(
                vm = vm,
                themeMode = themeMode,
                onThemeMode = { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() },
                dynamicColors = dynamicColors,
                onDynamicColors = { dynamicColors = it; prefs.edit().putBoolean("dynamic_colors", it).apply() },
                accentPalette = accentPalette,
                onAccentPalette = {
                    accentPalette = it.coerceIn(0, 3)
                    dynamicColors = false
                    prefs.edit().putInt("accent_palette", accentPalette).putBoolean("dynamic_colors", false).apply()
                },
                reduceMotion = reduceMotion,
                onReduceMotion = { reduceMotion = it; prefs.edit().putBoolean("reduce_motion", it).apply() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadsmileRoot(
    vm: AppViewModel,
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    dynamicColors: Boolean,
    onDynamicColors: (Boolean) -> Unit,
    accentPalette: Int,
    onAccentPalette: (Int) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit
) {
    val state by vm.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var returnPage by rememberSaveable { mutableStateOf(Page.HOME) }
    var detailItem by remember { mutableStateOf<FeedItem?>(null) }
    var detailIsGame by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let {
            snackbar.showSnackbar(it)
            vm.dismiss()
        }
    }
    LaunchedEffect(page, detailItem?.id, detailItem?.slug, detailIsGame) {
        if (page == Page.DETAILS) detailItem?.let { vm.loadDetails(it, detailIsGame) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Deadsmile", fontWeight = FontWeight.Bold)
                        Text(
                            when (page) {
                                Page.HOME -> "Jogos, notícias e novidades"
                                Page.DETAILS -> "Detalhes do jogo"
                                else -> page.label
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (page == Page.DETAILS || page == Page.SETTINGS || page == Page.WISHLIST) {
                        IconButton(onClick = { page = if (page == Page.DETAILS) returnPage else returnPage }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (page != Page.SETTINGS && page != Page.DETAILS && page != Page.WISHLIST) {
                        IconButton(onClick = { returnPage = page; page = Page.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Configurações")
                        }
                    }
                    IconButton(onClick = {
                        when (page) {
                            Page.ACCOUNT -> vm.account()
                            Page.LIBRARY -> vm.library()
                            else -> vm.reload()
                        }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Atualizar conteúdo") }
                }
            )
        },
        bottomBar = {
            if (page != Page.SETTINGS && page != Page.DETAILS && page != Page.WISHLIST) {
                FloatingDock(page = page, onSelect = { page = it }, reduceMotion = reduceMotion)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        val duration = if (reduceMotion) 0 else 220
        Crossfade(
            targetState = page,
            animationSpec = tween(durationMillis = duration),
            label = "deadsmile-page"
        ) { targetPage ->
            when (targetPage) {
                Page.HOME -> Home(
                    state = state, pad = pad,
                    news = { page = Page.NEWS }, videos = { page = Page.VIDEOS },
                    openGame = { item -> detailItem = item; detailIsGame = true; returnPage = page; page = Page.DETAILS },
                    openStory = { item -> detailItem = item; detailIsGame = false; returnPage = page; page = Page.DETAILS },
                    openCatalog = { page = Page.GAMES }
                )
                Page.GAMES -> FeedScreen("Catálogo de jogos", state.games, state.loading, pad) { item ->
                    detailItem = item; detailIsGame = true; returnPage = page; page = Page.DETAILS
                }
                Page.NEWS -> FeedScreen("Últimas notícias", state.news, state.loading, pad) {
                    detailItem = it; detailIsGame = false; returnPage = page; page = Page.DETAILS
                }
                Page.VIDEOS -> FeedScreen("Vídeos", state.videos, state.loading, pad) { item ->
                    val url = item.url
                    if (url != null) openExternal(context, url) else {
                        detailItem = item; detailIsGame = false; returnPage = page; page = Page.DETAILS
                    }
                }
                Page.LIBRARY -> LibraryScreen(state, pad) { page = Page.ACCOUNT }
                Page.ACCOUNT -> AccountScreen(state, pad, vm) { returnPage = Page.ACCOUNT; page = Page.WISHLIST }
                Page.WISHLIST -> WishlistScreen(
                    state = state,
                    pad = pad,
                    openGame = { item -> detailItem = item; detailIsGame = true; returnPage = Page.WISHLIST; page = Page.DETAILS },
                    signIn = { returnPage = Page.WISHLIST; page = Page.ACCOUNT },
                    remove = vm::removeWishlist
                )
                Page.SETTINGS -> SettingsScreen(
                    themeMode = themeMode, onThemeMode = onThemeMode,
                    dynamicColors = dynamicColors, onDynamicColors = onDynamicColors,
                    accentPalette = accentPalette, onAccentPalette = onAccentPalette,
                    reduceMotion = reduceMotion, onReduceMotion = onReduceMotion
                )
                Page.DETAILS -> detailItem?.let { item ->
                    val displayItem = state.detail?.takeIf {
                        it.id == item.id || (item.slug.isNotBlank() && it.slug == item.slug)
                    } ?: item
                    GameDetailsScreen(
                        item = displayItem, isGame = detailIsGame, pad = pad,
                        loading = state.detailLoading,
                        isWishlisted = state.wishlist.any { it.id.equals(displayItem.id, ignoreCase = true) },
                        onToggleWishlist = {
                            if (state.user == null) { returnPage = Page.DETAILS; page = Page.ACCOUNT }
                            else vm.toggleWishlist(displayItem)
                        },
                        onRelated = { related -> detailItem = related; detailIsGame = true },
                        onOpenSite = { openExternal(context, "https://deadsmilegames.vercel.app") },
                        onOpenVideo = { displayItem.url?.let { url -> openExternal(context, url) } }
                    )
                } ?: Home(state, pad, { page = Page.NEWS }, { page = Page.VIDEOS }, {}, {}, { page = Page.GAMES })
            }
        }
    }
}

@Composable
private fun FloatingDock(page: Page, onSelect: (Page) -> Unit, reduceMotion: Boolean) {
    val destinations = listOf(
        Triple(Page.HOME, Icons.Default.Home, "Início"),
        Triple(Page.NEWS, Icons.Default.Article, "Notícias"),
        Triple(Page.VIDEOS, Icons.Default.Videocam, "Vídeos"),
        Triple(Page.LIBRARY, Icons.Default.CollectionsBookmark, "Biblioteca"),
        Triple(Page.ACCOUNT, Icons.Default.AccountCircle, "Conta")
    )
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 5.dp,
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { (target, icon, label) ->
                    val active = (if (page == Page.GAMES) Page.HOME else page) == target
                    Surface(
                        onClick = { onSelect(target) },
                        shape = RoundedCornerShape(26.dp),
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateContentSize(tween(if (reduceMotion) 0 else 180))
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = if (active) 13.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
                            if (active) {
                                Spacer(Modifier.width(7.dp))
                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(
    state: UiState,
    pad: PaddingValues,
    news: () -> Unit,
    videos: () -> Unit,
    openGame: (FeedItem) -> Unit,
    openStory: (FeedItem) -> Unit,
    openCatalog: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.VideogameAsset, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Text("DEADSMILE GAMES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("Seu próximo jogo começa aqui.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Descubra lançamentos, acompanhe novidades e consulte sua biblioteca do PC.", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FilledTonalButton(onClick = news) { Icon(Icons.Default.Article, null); Spacer(Modifier.width(6.dp)); Text("Novidades") }
                        OutlinedButton(onClick = videos) { Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(6.dp)); Text("Vídeos") }
                    }
                }
            }
        }
        item { Section("Jogos", if (state.games.isEmpty()) "" else "Ver catálogo", openCatalog) }
        if (state.loading && state.games.isEmpty()) item { LoadingRow() }
        else if (state.games.isEmpty()) item { Empty("O catálogo não está disponível agora. Tente atualizar em alguns instantes.") }
        else item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.games, key = { it.id }) { game -> FeedCard(game, Modifier.width(230.dp), "Detalhes") { openGame(game) } }
            }
        }
        item { Section("Notícias", if (state.news.isEmpty()) "" else "Ver todas", news) }
        if (state.loading && state.news.isEmpty()) item { LoadingRow() }
        else if (state.news.isEmpty()) item { Empty("Nenhuma notícia nova por enquanto.") }
        else item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.news.take(8), key = { it.id }) { story -> FeedCard(story, Modifier.width(280.dp)) { openStory(story) } }
            }
        }
        item { Section("Vídeos", if (state.videos.isEmpty()) "" else "Ver todos", videos) }
        if (state.loading && state.videos.isEmpty()) item { LoadingRow() }
        else if (state.videos.isEmpty()) item { Empty("Novos vídeos aparecerão aqui quando estiverem disponíveis.") }
        else item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.videos.take(8), key = { it.id }) { video -> FeedCard(video, Modifier.width(250.dp), "Assistir") {
                    if (video.url != null) openStory(video) else openStory(video)
                } }
            }
        }
    }
}

@Composable
private fun Section(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action.isNotBlank()) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun FeedScreen(title: String, content: List<FeedItem>, loading: Boolean, pad: PaddingValues, open: (FeedItem) -> Unit) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val searchable = title == "Catálogo de jogos" || title == "Últimas notícias" || title == "Vídeos"
    val filtered = remember(content, query) {
        val term = query.trim()
        if (term.isBlank()) content else content.filter {
            it.title.contains(term, ignoreCase = true) ||
                it.summary.contains(term, ignoreCase = true) ||
                it.body.contains(term, ignoreCase = true) ||
                it.genres.any { genre -> genre.contains(term, ignoreCase = true) }
        }
    }
    if (loading && content.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (content.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Empty("Não há conteúdo para mostrar agora. Confira sua conexão e tente novamente.")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (searchable) item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (title == "Catálogo de jogos") "Buscar jogos" else "Buscar conteúdo") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                    }
                }
            )
        }
        if (filtered.isEmpty()) {
            item { Empty("Nenhum resultado para essa busca. Tente outro termo.") }
        } else {
            items(filtered, key = { it.id }) { item -> FeedCard(item, Modifier.fillMaxWidth()) { open(item) } }
        }
    }
}

@Composable
private fun FeedCard(item: FeedItem, modifier: Modifier = Modifier, action: String? = null, open: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = open),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column {
            if (item.image != null) {
                AsyncImage(item.image, item.title, Modifier.fillMaxWidth().height(155.dp), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxWidth().height(112.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.VideogameAsset, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                if (item.summary.isNotBlank()) Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                if (action != null) Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GameDetailsScreen(
    item: FeedItem,
    isGame: Boolean,
    pad: PaddingValues,
    loading: Boolean,
    isWishlisted: Boolean,
    onToggleWishlist: () -> Unit,
    onRelated: (FeedItem) -> Unit,
    onOpenSite: () -> Unit,
    onOpenVideo: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(285.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                item.image?.let { image ->
                    AsyncImage(image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, BrandInk.copy(alpha = 0.78f))
                        )
                    ))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            if (isGame) "CATÁLOGO DEADSMILE" else "DEADSMILE · CONTEÚDO",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Carregando informações…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isGame) {
                    val meta = listOfNotNull(
                        item.developer.takeIf { it.isNotBlank() }?.let { "Desenvolvido por $it" },
                        item.publisher.takeIf { it.isNotBlank() }?.let { "Publicado por $it" },
                        item.genres.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                    )
                    if (meta.isNotEmpty()) {
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                meta.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideogameAsset, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Disponível para PC", fontWeight = FontWeight.SemiBold)
                                Text("A instalação pelo celular ainda não está disponível. Você pode consultar os detalhes no site ou usar o launcher para Windows.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Button(onClick = onOpenSite, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver no site Deadsmile") }
                    OutlinedButton(onClick = onToggleWishlist, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Icon(if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isWishlisted) "Remover da lista de desejos" else "Adicionar à lista de desejos")
                    }
                } else {
                    val byline = listOf(item.author, item.publishedAt).filter { it.isNotBlank() }.joinToString(" · ")
                    if (byline.isNotBlank()) Text(byline, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (isGame) "Sobre o jogo" else "Artigo completo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val completeBody = item.body.ifBlank { item.summary.ifBlank { "Mais informações serão adicionadas assim que estiverem disponíveis." } }
                Text(completeBody, style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.14f)
                if (item.screenshots.isNotEmpty()) {
                    Text("Capturas de tela", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(item.screenshots, key = { it }) { screenshot ->
                            AsyncImage(
                                screenshot, "Captura de tela de ${item.title}",
                                Modifier.width(275.dp).height(160.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                if (item.relatedGames.isNotEmpty()) {
                    Text("Jogos relacionados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(item.relatedGames, key = { it.id }) { related ->
                            FeedCard(related, Modifier.width(205.dp), "Ver detalhes") { onRelated(related) }
                        }
                    }
                }
                if (!isGame) {
                    if (item.url != null) Button(onClick = onOpenVideo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Abrir conteúdo") }
                    OutlinedButton(onClick = onOpenSite, modifier = Modifier.fillMaxWidth()) { Text("Visitar Deadsmile Games") }
                }
            }
        }
    }
}

@Composable
private fun WishlistScreen(
    state: UiState,
    pad: PaddingValues,
    openGame: (FeedItem) -> Unit,
    signIn: () -> Unit,
    remove: (String) -> Unit
) {
    if (state.user == null) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.FavoriteBorder, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Sua lista de desejos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Entre na sua conta para guardar jogos e sincronizar sua lista com o launcher.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = signIn) { Text("Entrar na conta") }
                }
            }
        }
    } else if (state.wishlist.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sua lista está vazia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Adicione jogos aos favoritos pela tela de detalhes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Lista de desejos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Sincronizada com sua conta Deadsmile", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.wishlist, key = { it.id }) { game ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (game.image != null) AsyncImage(game.image, game.title, Modifier.size(86.dp, 112.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        else Box(Modifier.size(86.dp, 112.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.VideogameAsset, null, Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            if (game.summary.isNotBlank()) Text(game.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                            TextButton(onClick = { openGame(game) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Ver detalhes") }
                            TextButton(onClick = { remove(game.id) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Remover da lista") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: UiState, pad: PaddingValues, goAccount: () -> Unit) {
    if (state.user == null) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(36.dp))
                    Text("Sua biblioteca", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Entre para consultar os jogos associados à sua conta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = goAccount) { Text("Entrar na conta") }
                }
            }
        }
    } else if (state.library.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Empty(if (state.loading) "Carregando sua biblioteca…" else "Sua biblioteca está vazia ou ainda não foi carregada.")
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Sua biblioteca", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Jogos vinculados à sua conta · somente consulta", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.library, key = { it.id }) { game ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (game.image != null) AsyncImage(game.image, game.title, Modifier.size(82.dp, 96.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        else Box(Modifier.size(82.dp, 96.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Icons.Default.CollectionsBookmark, null) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (game.summary.isNotBlank()) Text(game.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                            AssistChip(onClick = {}, label = { Text("Na biblioteca") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(state: UiState, pad: PaddingValues, vm: AppViewModel, goWishlist: () -> Unit) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }

    var fullName by rememberSaveable { mutableStateOf("") }
    var profileBio by rememberSaveable { mutableStateOf("") }
    var websiteUrl by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf("") }
    var avatarPickerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val encoded = runCatching { encodeProfileImage(context, uri) }.getOrNull()
        if (encoded.isNullOrBlank()) {
            avatarPickerMessage = "Não foi possível usar essa imagem. Escolha outra foto."
        } else {
            avatarUrl = encoded
            avatarPickerMessage = "Foto selecionada. Salve o perfil para aplicar."
        }
    }
    var showEmailForm by rememberSaveable { mutableStateOf(false) }
    var newEmail by rememberSaveable { mutableStateOf("") }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var totpToken by rememberSaveable { mutableStateOf("") }
    var confirmDisableTwoFactor by rememberSaveable { mutableStateOf(false) }
    var shareActivity by rememberSaveable { mutableStateOf(false) }
    var sharePlaytime by rememberSaveable { mutableStateOf(false) }
    var shareAchievements by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.accountProfile) {
        state.accountProfile?.let { profile ->
            fullName = profile.fullName
            username = profile.username
            profileBio = profile.bio
            websiteUrl = profile.websiteUrl
            location = profile.location
            avatarUrl = profile.avatarUrl
        }
    }
    LaunchedEffect(state.privacy) {
        shareActivity = state.privacy.shareGameActivity
        sharePlaytime = state.privacy.sharePlaytime
        shareAchievements = state.privacy.shareAchievements
    }

    if (state.user != null) {
        val profile = state.accountProfile
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccountAvatar(profile?.avatarUrl, Modifier.size(68.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(profile?.fullName?.ifBlank { state.user.username } ?: state.user.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("@" + (profile?.username?.ifBlank { state.user.username } ?: state.user.username), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(profile?.email?.ifBlank { state.user.email } ?: state.user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.accountLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (editingProfile) {
                        HorizontalDivider()
                        OutlinedTextField(fullName, { fullName = it.take(80) }, label = { Text("Nome de exibição") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(username, { username = it.take(24) }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(profileBio, { profileBio = it.take(500) }, label = { Text("Biografia") }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(websiteUrl, { websiteUrl = it.take(2000) }, label = { Text("Site pessoal") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(location, { location = it.take(120) }, label = { Text("Localização") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(avatarUrl, { avatarUrl = it.take(500_000) }, label = { Text("URL da foto (opcional)") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Escolher foto do dispositivo")
                        }
                        avatarPickerMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(
                            onClick = { vm.saveProfile(fullName, username, profileBio, websiteUrl, location, avatarUrl); editingProfile = false },
                            enabled = !state.busy && username.trim().length in 3..24,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) { Text(if (state.busy) "Salvando…" else "Salvar perfil") }
                        TextButton(onClick = { editingProfile = false; profile?.let { username = it.username; fullName = it.fullName; profileBio = it.bio; websiteUrl = it.websiteUrl; location = it.location; avatarUrl = it.avatarUrl } }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    } else {
                        if (profile?.bio?.isNotBlank() == true) Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { editingProfile = true }, modifier = Modifier.weight(1f)) { Text("Editar perfil") }
                            OutlinedButton(onClick = vm::loadAccountSettings, enabled = !state.accountLoading, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Atualizar")
                            }
                        }
                    }
                }
            }

            Text("Conta e acesso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Endereço de e-mail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(profile?.email?.ifBlank { state.user.email } ?: state.user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showEmailForm = !showEmailForm }) { Text(if (showEmailForm) "Fechar" else "Alterar") }
                    }
                    if (showEmailForm) {
                        Text("Vamos enviar um link para confirmar o novo endereço. Seu e-mail atual continuará ativo até a confirmação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(newEmail, { newEmail = it.trim() }, label = { Text("Novo e-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(currentPassword, { currentPassword = it }, label = { Text("Senha atual") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = { vm.changeEmail(newEmail, currentPassword); currentPassword = ""; showEmailForm = false },
                            enabled = !state.busy && newEmail.contains("@") && currentPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enviar link de confirmação") }
                    }
                    HorizontalDivider()
                    Text("Sessões ativas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Veja onde sua conta está conectada e encerre sessões que você não reconhece.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.sessions.isEmpty()) {
                        Text(if (state.accountLoading) "Carregando dispositivos…" else "Nenhuma outra sessão encontrada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.sessions.forEach { session ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(listOf(session.client, session.platform).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Dispositivo conectado" }, fontWeight = FontWeight.Medium)
                                    if (session.isCurrent) Text("Sessão atual", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                                if (!session.isCurrent) TextButton(onClick = { vm.revokeSession(session.id) }, enabled = !state.busy) { Text("Desconectar") }
                            }
                        }
                    }
                }
            }

            Text("Segurança", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Verificação em duas etapas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (state.accountTwoFactorEnabled) "Ativada para sua conta" else "Adicione uma camada extra de proteção à conta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.accountTwoFactorEnabled) {
                        OutlinedTextField(totpToken, { totpToken = it.take(10) }, label = { Text("Código do autenticador") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { confirmDisableTwoFactor = true }, enabled = !state.busy && totpToken.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Desativar verificação em duas etapas") }
                    } else {
                        if (state.twoFactorSetupSecret.isBlank()) {
                            Button(onClick = vm::startTwoFactorSetup, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Configurar verificação em duas etapas") }
                        } else {
                            Text("Adicione esta chave manualmente no aplicativo autenticador:", style = MaterialTheme.typography.bodyMedium)
                            SelectionContainer { Text(state.twoFactorSetupSecret, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            if (state.twoFactorSetupUri.isNotBlank()) {
                                Text("URI de configuração", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer { Text(state.twoFactorSetupUri, style = MaterialTheme.typography.bodySmall) }
                            }
                            OutlinedTextField(totpToken, { totpToken = it.take(10) }, label = { Text("Código de seis dígitos") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                            Button(onClick = { vm.confirmTwoFactor(totpToken); totpToken = "" }, enabled = !state.busy && totpToken.length >= 6, modifier = Modifier.fillMaxWidth()) { Text("Confirmar e ativar") }
                        }
                    }
                }
            }

            Text("Privacidade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreferenceSwitch(
                        title = "Compartilhar atividade de jogos",
                        subtitle = "Permite mostrar sua atividade no perfil.",
                        checked = shareActivity,
                        onCheckedChange = { checked -> shareActivity = checked; if (!checked) sharePlaytime = false }
                    )
                    PreferenceSwitch(
                        title = "Mostrar tempo de jogo",
                        subtitle = "Exibe seu tempo de jogo no perfil público.",
                        checked = sharePlaytime,
                        onCheckedChange = { if (shareActivity) sharePlaytime = it }
                    )
                    PreferenceSwitch(
                        title = "Compartilhar conquistas",
                        subtitle = "Permite exibir suas conquistas no perfil.",
                        checked = shareAchievements,
                        onCheckedChange = { shareAchievements = it }
                    )
                    Button(
                        onClick = { vm.savePrivacy(PrivacyOptions(shareActivity, sharePlaytime, shareAchievements)) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Salvar privacidade") }
                }
            }

            OutlinedButton(onClick = vm::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sair da conta")
            }
            Spacer(Modifier.height(14.dp))
        }
        if (confirmDisableTwoFactor) {
            AlertDialog(
                onDismissRequest = { confirmDisableTwoFactor = false },
                title = { Text("Desativar verificação em duas etapas?") },
                text = { Text("Confirme com o código atual do seu autenticador.") },
                confirmButton = {
                    TextButton(onClick = { confirmDisableTwoFactor = false; vm.confirmTwoFactor(totpToken, disable = true); totpToken = "" }) { Text("Desativar") }
                },
                dismissButton = { TextButton(onClick = { confirmDisableTwoFactor = false }) { Text("Cancelar") } }
            )
        }
        return
    }

    val images = remember(state.games, state.news, state.videos) {
        (state.games + state.news + state.videos).mapNotNull { it.image }.distinct().take(12)
            .ifEmpty { listOf("https://raw.githubusercontent.com/deadsmilegames/launcher/main/winui/src/Deadsmile.Launcher/Assets/LargeTile.png") }
    }
    var imageIndex by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        while (true) {
            delay(3000)
            imageIndex = if (images.isEmpty()) 0 else (imageIndex + 1) % images.size
        }
    }

    Box(Modifier.fillMaxSize().padding(pad)) {
        AnimatedContent(
            targetState = imageIndex,
            transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(650)) },
            label = "login-background"
        ) { index ->
            AsyncImage(
                model = images.getOrNull(index),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(BrandInk.copy(alpha = 0.70f)))
        Box(Modifier.fillMaxSize().background(BrushVerticalShade()))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(BrandViolet), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideogameAsset, null, Modifier.size(27.dp), tint = Color.White)
                }
                Column {
                    Text("DEADSMILE GAMES", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Seu universo de jogos", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
                }
            }
            Spacer(Modifier.height(22.dp))
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text(
                        if (state.twoFactor) "Confirme que é você" else if (registerMode) "Crie sua conta" else "Bom ter você de volta",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.twoFactor) "Digite o código do seu autenticador para continuar."
                        else if (registerMode) "Entre para acompanhar seus jogos e novidades."
                        else "Entre para acessar sua biblioteca e seu perfil.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.twoFactor) {
                        OutlinedTextField(
                            code, { code = it.take(10) }, label = { Text("Código de autenticação") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { vm.verify(code) }, enabled = !state.busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp) else Text("Confirmar código")
                        }
                    } else {
                        if (registerMode) OutlinedTextField(
                            username, { username = it }, label = { Text("Nome de usuário") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            email, { email = it.trim() }, label = { Text("E-mail") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            password, { password = it }, label = { Text("Senha") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { if (registerMode) vm.register(username, email, password) else vm.login(email, password) },
                            enabled = !state.busy && email.contains("@") && password.isNotBlank() && (!registerMode || username.trim().length in 3..24),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            else Text(if (registerMode) "Criar conta" else "Entrar")
                        }
                        TextButton(onClick = { registerMode = !registerMode }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (registerMode) "Já tenho uma conta" else "Criar uma conta")
                        }
                        TextButton(onClick = { openExternal(context, "https://deadsmilegames.vercel.app") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Precisa de ajuda com a conta?")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Notícias e jogos em um só lugar.", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BrushVerticalShade(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xAA090810), Color(0x33090810), Color(0xDD090810))
    )


@Composable
private fun AccountAvatar(url: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(url) { decodeProfileImage(url) }
    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto do perfil",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        !url.isNullOrBlank() -> AsyncImage(
            model = url,
            contentDescription = "Foto do perfil",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        else -> Box(
            modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountCircle, null, Modifier.fillMaxSize().padding(4.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun decodeProfileImage(value: String?): Bitmap? {
    val raw = value?.takeIf { it.startsWith("data:image/") && it.length <= 500_000 } ?: return null
    val comma = raw.indexOf(',')
    if (comma < 0) return null
    val bytes = runCatching { Base64.decode(raw.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return null
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}

private fun encodeProfileImage(context: Context, uri: Uri): String? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 640 || bounds.outHeight / sampleSize > 640) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val scaled = if (decoded.width > 512 || decoded.height > 512) {
        val ratio = minOf(512f / decoded.width, 512f / decoded.height)
        Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
    } else decoded
    val stream = ByteArrayOutputStream()
    var quality = 84
    do {
        stream.reset()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        quality -= 8
    } while (stream.size() > 340_000 && quality >= 44)
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    val encoded = "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    return encoded.takeIf { it.length <= 500_000 }
}

@Composable
private fun SettingsScreen(
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    dynamicColors: Boolean,
    onDynamicColors: (Boolean) -> Unit,
    accentPalette: Int,
    onAccentPalette: (Int) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Personalize sua experiência", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Deixe o app do seu jeito. As mudanças são aplicadas na hora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aparência", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    listOf("Seguir o sistema", "Claro", "Escuro").forEachIndexed { index, label ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onThemeMode(index) }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == index, onClick = { onThemeMode(index) })
                            Text(label, Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider()
                    PreferenceSwitch(
                        title = "Cores do sistema",
                        subtitle = "Usa as cores dinâmicas do Android quando disponíveis.",
                        checked = dynamicColors,
                        onCheckedChange = onDynamicColors
                    )
                    Text("Cor de destaque", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val swatches = listOf(
                        Triple("Violeta", Color(0xFF5B4BCC), Color(0xFFB8AEFF)),
                        Triple("Rosa", Color(0xFFB4234F), Color(0xFFFF9DB0)),
                        Triple("Verde-água", Color(0xFF176B65), Color(0xFF8AD8CF)),
                        Triple("Azul", Color(0xFF2458B8), Color(0xFF9CBFFF))
                    )
                    swatches.forEachIndexed { index, palette ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAccentPalette(index) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(selected = accentPalette == index && !dynamicColors, onClick = { onAccentPalette(index) })
                            Box(Modifier.size(26.dp).clip(CircleShape).background(if (themeMode == 2) palette.third else palette.second))
                            Text(palette.first, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Comportamento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    PreferenceSwitch(
                        title = "Reduzir animações",
                        subtitle = "Encurta transições e efeitos de movimento.",
                        checked = reduceMotion,
                        onCheckedChange = onReduceMotion
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Notificações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Avisos de novidades do catálogo, notícias e vídeos. A atualização em segundo plano depende das regras de bateria e rede do Android.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    NotificationPreference()
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sobre", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Deadsmile Games para Android", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Conteúdo conectado à plataforma Deadsmile. A biblioteca mostra seus jogos para PC; instalações no Android não estão disponíveis.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NotificationPreference() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", false)) }
    var notifyGames by remember { mutableStateOf(prefs.getBoolean("notify_games", true)) }
    var notifyNews by remember { mutableStateOf(prefs.getBoolean("notify_news", true)) }
    var notifyVideos by remember { mutableStateOf(prefs.getBoolean("notify_videos", true)) }
    var notifyPlatform by remember { mutableStateOf(prefs.getBoolean("notify_platform", true)) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        prefs.edit().putBoolean("notifications_enabled", granted).apply()
        permissionMessage = if (granted) "Notificações ativadas." else "Permissão não concedida. Você pode ativá-la nas configurações do Android."
        if (granted) scheduleContentNotifications(context) else WorkManager.getInstance(context).cancelUniqueWork("deadsmile-content-notifications")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreferenceSwitch(
            title = "Receber novidades",
            subtitle = "Consulta atualizações periodicamente, mesmo fora do app.",
            checked = enabled,
            onCheckedChange = { turnOn ->
                if (!turnOn) {
                    enabled = false
                    prefs.edit().putBoolean("notifications_enabled", false).apply()
                    WorkManager.getInstance(context).cancelUniqueWork("deadsmile-content-notifications")
                    permissionMessage = "Notificações pausadas."
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enabled = true
                    prefs.edit().putBoolean("notifications_enabled", true).apply()
                    scheduleContentNotifications(context)
                    permissionMessage = "Notificações ativadas."
                }
            }
        )
        if (enabled) {
            HorizontalDivider()
            Text("Escolha os avisos que quer receber", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            PreferenceSwitch("Novos jogos", "Publicações e atualizações do catálogo.", notifyGames, {
                notifyGames = it; prefs.edit().putBoolean("notify_games", it).apply()
            })
            PreferenceSwitch("Notícias", "Novas publicações e mudanças nas notícias.", notifyNews, {
                notifyNews = it; prefs.edit().putBoolean("notify_news", it).apply()
            })
            PreferenceSwitch("Vídeos", "Vídeos adicionados ao feed.", notifyVideos, {
                notifyVideos = it; prefs.edit().putBoolean("notify_videos", it).apply()
            })
            PreferenceSwitch("Eventos da conta", "Eventos da plataforma, como conquistas e lista de desejos.", notifyPlatform, {
                notifyPlatform = it; prefs.edit().putBoolean("notify_platform", it).apply()
            })
        }
        permissionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun scheduleContentNotifications(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request = PeriodicWorkRequestBuilder<games.deadsmile.mobile.data.ContentNotificationWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "deadsmile-content-notifications",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Empty(message: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openExternal(context: Context, value: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
