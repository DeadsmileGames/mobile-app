package games.deadsmile.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private enum class Page(val label: String) {
    HOME("Início"), NEWS("Notícias"), VIDEOS("Vídeos"), LIBRARY("Biblioteca"),
    ACCOUNT("Conta"), SETTINGS("Configurações"), DETAILS("Detalhes")
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
    var reduceMotion by rememberSaveable { mutableStateOf(prefs.getBoolean("reduce_motion", false)) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dynamicColors && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 && dynamicColors -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFB8AEFF), secondary = Color(0xFFFF91A2), tertiary = Color(0xFF91D9D0))
        else -> lightColorScheme(primary = Color(0xFF5B4BCC), secondary = Color(0xFFB4234F), tertiary = Color(0xFF176B65))
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DeadsmileRoot(
                vm = vm,
                themeMode = themeMode,
                onThemeMode = { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() },
                dynamicColors = dynamicColors,
                onDynamicColors = { dynamicColors = it; prefs.edit().putBoolean("dynamic_colors", it).apply() },
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
                    if (page == Page.DETAILS || page == Page.SETTINGS) {
                        IconButton(onClick = { page = if (page == Page.DETAILS) returnPage else returnPage }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (page != Page.SETTINGS && page != Page.DETAILS) {
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
            if (page != Page.SETTINGS && page != Page.DETAILS) {
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
                    openStory = { item -> detailItem = item; detailIsGame = false; returnPage = page; page = Page.DETAILS }
                )
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
                Page.ACCOUNT -> AccountScreen(state, pad, vm)
                Page.SETTINGS -> SettingsScreen(
                    themeMode = themeMode, onThemeMode = onThemeMode,
                    dynamicColors = dynamicColors, onDynamicColors = onDynamicColors,
                    reduceMotion = reduceMotion, onReduceMotion = onReduceMotion
                )
                Page.DETAILS -> detailItem?.let { item ->
                    GameDetailsScreen(
                        item = item, isGame = detailIsGame, pad = pad,
                        onOpenSite = { openExternal(context, "https://deadsmilegames.vercel.app") },
                        onOpenVideo = { item.url?.let { url -> openExternal(context, url) } }
                    )
                } ?: Home(state, pad, { page = Page.NEWS }, { page = Page.VIDEOS }, {}, {})
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
                    val active = page == target
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
    openStory: (FeedItem) -> Unit
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
        item { Section("Jogos", if (state.games.isEmpty()) "" else "Ver todos", {}) }
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
    if (loading && content.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (content.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) { Empty("Não há conteúdo para mostrar agora. Confira sua conexão e tente novamente.") }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(content, key = { it.id }) { item -> FeedCard(item, Modifier.fillMaxWidth()) { open(item) } }
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
    onOpenSite: () -> Unit,
    onOpenVideo: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(275.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (item.image != null) {
                    AsyncImage(item.image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(BrandInk.copy(alpha = 0.42f)))
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
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (isGame) "Detalhes do jogo" else "Sobre este conteúdo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.body.ifBlank { item.summary.ifBlank { "Mais informações serão adicionadas assim que estiverem disponíveis." } }, style = MaterialTheme.typography.bodyLarge)
                if (isGame) {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideogameAsset, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Experiência para PC", fontWeight = FontWeight.SemiBold)
                                Text("A instalação pelo celular ainda não está disponível. Consulte o site ou use o launcher para Windows.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Button(onClick = onOpenSite, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Ver no site Deadsmile")
                    }
                } else {
                    if (item.url != null) Button(onClick = onOpenVideo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Abrir conteúdo") }
                    OutlinedButton(onClick = onOpenSite, modifier = Modifier.fillMaxWidth()) { Text("Visitar Deadsmile Games") }
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
private fun AccountScreen(state: UiState, pad: PaddingValues, vm: AppViewModel) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }

    if (state.user != null) {
        LaunchedEffect(state.user.username) { username = state.user.username }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AccountCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text(state.user.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text("Dados da conta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (editing) {
                        OutlinedTextField(username, { username = it }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(state.user.bio, {}, label = { Text("Biografia") }, enabled = false, modifier = Modifier.fillMaxWidth(), supportingText = { Text("A edição da biografia ainda não está disponível.") })
                        Button(
                            onClick = { vm.rename(username); editing = false },
                            enabled = !state.busy && username.trim().length in 3..24 && username.trim() != state.user.username,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Salvar alterações") }
                        TextButton(onClick = { editing = false; username = state.user.username }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    } else {
                        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil") }
                    }
                    OutlinedButton(onClick = vm::account, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Atualizar dados da conta")
                    }
                    Button(onClick = vm::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sair da conta")
                    }
                }
            }
            Text("Preferências do app", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Tema, animações e notificações ficam em Configurações, no ícone de engrenagem.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val images = remember(state.games, state.news, state.videos) {
        (state.games + state.news + state.videos).mapNotNull { it.image }.distinct().take(12)
            .ifEmpty { listOf("https://raw.githubusercontent.com/deadsmilegames/launcher/main/public/readme.png") }
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
private fun SettingsScreen(
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    dynamicColors: Boolean,
    onDynamicColors: (Boolean) -> Unit,
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
