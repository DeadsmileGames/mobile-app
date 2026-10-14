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
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material3.Text as MaterialText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import games.deadsmile.mobile.data.AppViewModel
import games.deadsmile.mobile.data.FeedItem
import games.deadsmile.mobile.data.UiState
import games.deadsmile.mobile.data.PrivacyOptions
import games.deadsmile.mobile.data.GitHubRelease
import games.deadsmile.mobile.data.GitHubReleaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private enum class Page(val label: String) {
    HOME("Início"), GAMES("Jogos"), NEWS("Newswire"), VIDEOS("Vídeos"), LIBRARY("Biblioteca"),
    ACCOUNT("Conta"), WISHLIST("Lista de desejos"), SETTINGS("Configurações"), DETAILS("Detalhes"), UPDATES("Atualizações"),
    NOTIFICATIONS("Notificações"), VIDEO_PLAYER("Reprodutor")
}

private val BrandInk = Color(0xFF101010)

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = AppI18n.translate(LocalContext.current, text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { DeadsmileApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadsmileApp(vm: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (prefs.getBoolean("notifications_enabled", false) && permissionGranted) scheduleContentNotifications(context)
    }
    var themeMode by rememberSaveable { mutableIntStateOf(prefs.getInt("theme_mode", 0)) }
    var dynamicColors by rememberSaveable { mutableStateOf(prefs.getBoolean("dynamic_colors", true)) }
    var reduceMotion by rememberSaveable { mutableStateOf(prefs.getBoolean("reduce_motion", false)) }
    var appLanguage by rememberSaveable { mutableStateOf(prefs.getString("app_language", "").orEmpty()) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) { 1 -> false; 2, 3 -> true; else -> systemDark }
    val scheme = when {
        themeMode == 3 && Build.VERSION.SDK_INT >= 31 -> dynamicDarkColorScheme(context).copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF080808),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF050505),
            surfaceContainerHigh = Color(0xFF0B0B0B),
            surfaceContainerHighest = Color(0xFF141414)
        )
        themeMode == 3 -> darkColorScheme(
            primary = Color(0xFFE2E2E2), secondary = Color(0xFFC8C8C8), tertiary = Color(0xFFAAAAAA),
            background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF080808),
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF050505), surfaceContainerHigh = Color(0xFF0B0B0B),
            surfaceContainerHighest = Color(0xFF141414)
        )
        Build.VERSION.SDK_INT >= 31 && dynamicColors && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 && dynamicColors -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFD8D8D8), secondary = Color(0xFFBFBFBF), tertiary = Color(0xFFA5A5A5)
        )
        else -> lightColorScheme(
            primary = Color(0xFF454545), secondary = Color(0xFF626262), tertiary = Color(0xFF777777)
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
                reduceMotion = reduceMotion,
                onReduceMotion = { reduceMotion = it; prefs.edit().putBoolean("reduce_motion", it).apply() },
                appLanguage = appLanguage,
                onAppLanguage = { language -> appLanguage = language; prefs.edit().putString("app_language", language).apply() }
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
    onReduceMotion: (Boolean) -> Unit,
    appLanguage: String,
    onAppLanguage: (String) -> Unit
) {
    val state by vm.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var videoItem by remember { mutableStateOf<FeedItem?>(null) }
    var returnPage by rememberSaveable { mutableStateOf(Page.HOME) }
    var detailItem by remember { mutableStateOf<FeedItem?>(null) }
    var detailIsGame by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val preferences = remember { context.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE) }
    var tourComplete by rememberSaveable { mutableStateOf(preferences.getBoolean("tour_completed", false)) }
    var tourStage by rememberSaveable { mutableIntStateOf(if (tourComplete) -1 else 0) }
    var tourFeedback by remember { mutableStateOf<String?>(null) }
    val tourTargets = listOf(Page.HOME, Page.NEWS, Page.VIDEOS, Page.LIBRARY, Page.ACCOUNT)

    var releases by remember { mutableStateOf<List<GitHubRelease>>(emptyList()) }
    var releasesLoading by remember { mutableStateOf(true) }
    var releaseError by remember { mutableStateOf<String?>(null) }
    var updateBusyVersion by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var pendingUpdateFile by remember { mutableStateOf<File?>(null) }
    var showInstallPermissionDialog by remember { mutableStateOf(false) }
    var autoUpdateError by remember { mutableStateOf<String?>(null) }
    val updateScope = rememberCoroutineScope()

    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let {
            snackbar.showSnackbar(it)
            vm.dismiss()
        }
    }
    LaunchedEffect(page, detailItem?.id, detailItem?.slug, detailIsGame) {
        if (page == Page.DETAILS) detailItem?.let { vm.loadDetails(it, detailIsGame) }
    }
    LaunchedEffect(tourFeedback) {
        val message = tourFeedback ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        tourFeedback = null
    }
    LaunchedEffect(autoUpdateError) {
        val message = autoUpdateError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        autoUpdateError = null
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pendingUpdateFile
        if (file != null && file.exists() && GitHubReleaseRepository.canInstall(context)) {
            runCatching { GitHubReleaseRepository.install(context, file) }
                .onFailure { autoUpdateError = it.message ?: "Não foi possível abrir o instalador." }
            showInstallPermissionDialog = false
        }
    }

    val beginUpdateDownload: (GitHubRelease) -> Unit = { release ->
        updateScope.launch {
            if (BuildConfig.APPLICATION_ID != "games.deadsmile.mobile") {
                autoUpdateError = "A instalação automática exige a versão de distribuição assinada."
            } else {
                updateBusyVersion = release.version
                updateProgress = 0
                releaseError = null
                try {
                    val cached = GitHubReleaseRepository.cachedFile(context, release)
                    val file = if (cached != null && cached.isFile && cached.length() > 0L) cached
                    else GitHubReleaseRepository.download(context, release) { percent -> updateProgress = percent }
                    pendingUpdateFile = file
                    if (GitHubReleaseRepository.canInstall(context)) {
                        GitHubReleaseRepository.install(context, file)
                    } else {
                        showInstallPermissionDialog = true
                    }
                } catch (error: Exception) {
                    autoUpdateError = error.message ?: "Não foi possível baixar ou preparar a atualização."
                } finally {
                    updateBusyVersion = null
                }
            }
        }
    }

    fun refreshReleases() {
        updateScope.launch {
            releasesLoading = true
            releaseError = null
            try {
                releases = GitHubReleaseRepository.fetchReleases()
            } catch (error: Exception) {
                releaseError = error.message ?: "Não foi possível carregar as releases do GitHub."
            } finally {
                releasesLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        releasesLoading = true
        releaseError = null
        try {
            releases = GitHubReleaseRepository.fetchReleases()
            val newest = GitHubReleaseRepository.latestNewerRelease(releases, BuildConfig.VERSION_NAME)
            // Update download is deferred until the one-time tour ends.
        } catch (error: Exception) {
            releaseError = error.message ?: "Não foi possível carregar as releases do GitHub."
        } finally {
            releasesLoading = false
        }
    }

    LaunchedEffect(tourStage, releasesLoading, releases) {
        if (tourStage >= 0 || releasesLoading || BuildConfig.APPLICATION_ID != "games.deadsmile.mobile") return@LaunchedEffect
        val newest = GitHubReleaseRepository.latestNewerRelease(releases, BuildConfig.VERSION_NAME) ?: return@LaunchedEffect
        if (updateBusyVersion == null) beginUpdateDownload(newest)
    }

    BackHandler(enabled = page != Page.HOME && tourStage < 0) {
        page = when (page) {
            Page.SETTINGS, Page.DETAILS, Page.WISHLIST, Page.UPDATES, Page.NOTIFICATIONS, Page.VIDEO_PLAYER -> returnPage.takeIf { it != page } ?: Page.HOME
            else -> Page.HOME
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (page == Page.DETAILS) {
                        MaterialText(
                            text = detailItem?.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        val title = when (page) {
                            Page.HOME -> "Início"
                            Page.GAMES -> "Catálogo de jogos"
                            Page.NEWS -> "Newswire"
                            Page.VIDEOS -> "Vídeos"
                            Page.LIBRARY -> "Biblioteca"
                            Page.ACCOUNT -> "Conta"
                            Page.WISHLIST -> "Lista de desejos"
                            Page.SETTINGS -> "Configurações"
                            Page.DETAILS -> "Detalhes"
                            Page.UPDATES -> "Atualizações"
                            Page.NOTIFICATIONS -> "Notificações"
                            Page.VIDEO_PLAYER -> "Reproduzindo"
                        }
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (page == Page.DETAILS || page == Page.SETTINGS || page == Page.WISHLIST || page == Page.GAMES || page == Page.UPDATES || page == Page.NOTIFICATIONS || page == Page.VIDEO_PLAYER) {
                        IconButton(onClick = { page = when (page) { Page.GAMES -> Page.HOME; else -> returnPage.takeIf { it != page } ?: Page.HOME } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppI18n.translate(LocalContext.current, "Voltar"))
                        }
                    }
                },
                actions = {
                    if (tourStage < 0 && page != Page.SETTINGS && page != Page.DETAILS && page != Page.WISHLIST && page != Page.UPDATES) {
                        IconButton(onClick = { returnPage = page; page = Page.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = AppI18n.translate(LocalContext.current, "Configurações"))
                        }
                    }
                    if (tourStage < 0 && page != Page.NOTIFICATIONS && page != Page.VIDEO_PLAYER) {
                        IconButton(onClick = { returnPage = page; page = Page.NOTIFICATIONS }) {
                            Icon(Icons.Default.Notifications, contentDescription = AppI18n.translate(LocalContext.current, "Notificações"))
                        }
                    }
                    if (tourStage < 0) {
                        IconButton(onClick = {
                            when (page) {
                                Page.ACCOUNT -> vm.account()
                                Page.LIBRARY -> vm.library()
                                Page.UPDATES -> refreshReleases()
                                else -> vm.reload()
                            }
                        }) { Icon(Icons.Default.Refresh, contentDescription = AppI18n.translate(LocalContext.current, "Atualizar conteúdo")) }
                    }
                }
            )
        },
        bottomBar = {
            if (page != Page.SETTINGS && page != Page.DETAILS && page != Page.WISHLIST && page != Page.UPDATES && page != Page.NOTIFICATIONS && page != Page.VIDEO_PLAYER && tourStage != 6) {
                val tourTarget = if (tourStage in 1..5) tourTargets.getOrNull(tourStage - 1) else null
                FloatingDock(
                    page = page,
                    tourTarget = tourTarget,
                    onSelect = { selected ->
                        when {
                            tourStage == 0 || tourStage == 6 -> Unit
                            tourStage in 1..5 -> {
                                val expected = tourTargets[tourStage - 1]
                                if (selected == expected) {
                                    page = selected
                                    tourStage += 1
                                    tourFeedback = when (selected) {
                                        Page.HOME -> "Parabéns! Você conheceu a página inicial."
                                        Page.NEWS -> "Parabéns! Você encontrou a área de newswire."
                                        Page.VIDEOS -> "Parabéns! Você encontrou os vídeos."
                                        Page.LIBRARY -> "Parabéns! Você encontrou sua biblioteca."
                                        Page.ACCOUNT -> "Parabéns! Você chegou à conta e ao login."
                                        else -> ""
                                    }
                                } else {
                                    tourFeedback = "Toque na aba destacada para continuar o tour."
                                }
                            }
                            else -> page = selected
                        }
                    },
                    reduceMotion = reduceMotion
                )
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
                    openCatalog = { returnPage = Page.HOME; page = Page.GAMES }
                )
                Page.GAMES -> GameCatalogScreen(state.games, state.loading, pad) { item ->
                    detailItem = item; detailIsGame = true; returnPage = page; page = Page.DETAILS
                }
                Page.NEWS -> FeedScreen("Últimas notícias", state.news, state.loading, pad) {
                    detailItem = it; detailIsGame = false; returnPage = page; page = Page.DETAILS
                }
                Page.VIDEOS -> FeedScreen("Vídeos", state.videos, state.loading, pad) { item ->
                    if (item.url != null && youtubeVideoId(item.url) != null) { videoItem = item; returnPage = Page.VIDEOS; page = Page.VIDEO_PLAYER }
                    else { detailItem = item; detailIsGame = false; returnPage = page; page = Page.DETAILS }
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
                    pad = pad,
                    themeMode = themeMode, onThemeMode = onThemeMode,
                    dynamicColors = dynamicColors, onDynamicColors = onDynamicColors,
                    reduceMotion = reduceMotion, onReduceMotion = onReduceMotion,
                    appLanguage = appLanguage, onAppLanguage = onAppLanguage,
                    onOpenUpdates = { returnPage = Page.SETTINGS; page = Page.UPDATES }
                )
                Page.NOTIFICATIONS -> NotificationsScreen(pad)
                Page.VIDEO_PLAYER -> videoItem?.let { VideoPlayerScreen(it, pad) } ?: run { page = Page.VIDEOS }
                Page.UPDATES -> UpdatesScreen(
                    pad = pad,
                    releases = releases,
                    loading = releasesLoading,
                    error = releaseError,
                    installedVersion = BuildConfig.VERSION_NAME,
                    updateBusyVersion = updateBusyVersion,
                    updateProgress = updateProgress,
                    autoUpdateError = autoUpdateError,
                    allowInstall = BuildConfig.APPLICATION_ID == "games.deadsmile.mobile",
                    onRefresh = ::refreshReleases,
                    onInstall = beginUpdateDownload,
                    onOpenRelease = { url -> openExternal(context, url) }
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
                        onOpenExternal = { url -> openExternal(context, url) },
                        onOpenVideo = { displayItem.url?.let { url -> if (youtubeVideoId(url) != null) { videoItem = displayItem; returnPage = Page.DETAILS; page = Page.VIDEO_PLAYER } else openExternal(context, url) } }
                    )
                } ?: Home(state, pad, { page = Page.NEWS }, { page = Page.VIDEOS }, {}, {}, { page = Page.GAMES })
            }
        }
        if (tourStage == 0) {
            TourWelcomeOverlay(
                onStart = { tourStage = 1 },
                onSkip = { tourStage = -1; tourComplete = true; preferences.edit().putBoolean("tour_completed", true).apply() }
            )
        } else if (tourStage in 1..5) {
            val target = tourTargets[tourStage - 1]
            TourStepOverlay(
                title = when (target) {
                    Page.HOME -> "Página inicial"
                    Page.NEWS -> "Newswire"
                    Page.VIDEOS -> "Vídeos"
                    Page.LIBRARY -> "Biblioteca"
                    Page.ACCOUNT -> "Conta"
                    else -> "Navegação"
                },
                description = when (target) {
                    Page.HOME -> "Aqui você encontra o resumo do Companion e o carrossel de jogos."
                    Page.NEWS -> "Esta aba mostra as publicações e notícias carregadas da plataforma."
                    Page.VIDEOS -> "Aqui ficam os vídeos publicados e seus links oficiais."
                    Page.LIBRARY -> "A biblioteca exibe os jogos da sua conta para PC, sem instalar jogos no Android."
                    Page.ACCOUNT -> "Nesta área você entra na conta, gerencia o perfil e acessa a lista de desejos."
                    else -> ""
                },
                onSkip = { tourStage = -1; tourComplete = true; preferences.edit().putBoolean("tour_completed", true).apply() }
            )
        } else if (tourStage == 6) {
            TourThanksOverlay(
                onFinish = {
                    tourStage = -1
                    tourComplete = true
                    preferences.edit().putBoolean("tour_completed", true).apply()
                }
            )
        }
    }
    if (appLanguage.isBlank()) LanguageSelectionOverlay(onSelect = onAppLanguage)
    if (showInstallPermissionDialog && pendingUpdateFile != null) {
        AlertDialog(
            onDismissRequest = { showInstallPermissionDialog = false },
            title = { Text("Atualização pronta") },
            text = { Text("O APK foi baixado. Para continuar, permita que o Companion solicite a instalação desta versão nas configurações do Android.") },
            confirmButton = {
                TextButton(onClick = {
                    showInstallPermissionDialog = false
                    installPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    )
                }) { Text("Abrir configurações") }
            },
            dismissButton = { TextButton(onClick = { showInstallPermissionDialog = false }) { Text("Agora não") } }
        )
    }
}

@Composable
private fun TourWelcomeOverlay(onStart: () -> Unit, onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).padding(20.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 440.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bem-vindo ao Deadsmile Games Companion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Conheça as áreas principais do Companion. O tour vai destacar cada aba e explicar o que você encontra nela.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Começar tour") }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Pular por enquanto") }
            }
        }
    }
}

@Composable
private fun TourStepOverlay(title: String, description: String, onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)).padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Card(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(top = 12.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Toque na aba destacada na barra inferior para continuar.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) { Text("Pular tour") }
            }
        }
    }
}

@Composable
private fun TourThanksOverlay(onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).padding(20.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 440.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Obrigado por conhecer o Companion!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Você chegou à área da conta. Entre para sincronizar sua biblioteca e sua lista de desejos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Concluir e acessar a conta") }
            }
        }
    }
}

@Composable
private fun FloatingDock(page: Page, tourTarget: Page?, onSelect: (Page) -> Unit, reduceMotion: Boolean) {
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
                    val isTourTarget = tourTarget == target
                    Surface(
                        onClick = { onSelect(target) },
                        shape = RoundedCornerShape(26.dp),
                        color = if (isTourTarget) MaterialTheme.colorScheme.primaryContainer else if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (isTourTarget) MaterialTheme.colorScheme.onPrimaryContainer else if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isTourTarget) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                        shadowElevation = if (isTourTarget) 8.dp else 0.dp,
                        modifier = Modifier.animateContentSize(tween(if (reduceMotion) 0 else 180))
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = if (active) 13.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = AppI18n.translate(LocalContext.current, label), modifier = Modifier.size(22.dp))
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
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            val hero = state.games.firstOrNull { it.title.contains("abby", true) && it.title.contains("restless", true) } ?: state.games.firstOrNull()
            Card(Modifier.fillMaxWidth().height(310.dp), shape = RoundedCornerShape(28.dp)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    hero?.image?.let { heroImage ->
                        SubcomposeAsyncImage(model = heroImage, contentDescription = hero.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
                            if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        }
                    }
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))))
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("DEADSMILE GAMES", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .88f), fontWeight = FontWeight.Bold)
                        Text(hero?.title ?: "Deadsmile Games Companion", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(hero?.summary ?: "Jogos, notícias e novidades da Deadsmile.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .88f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { if (hero != null) openGame(hero) else openCatalog() }) { Text("Ver jogo") }
                            OutlinedButton(onClick = openCatalog, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Explorar catálogo") }
                        }
                    }
                }
            }
        }
        item {
            Section("Seu catálogo", if (state.games.isEmpty()) "" else "Ver todos", openCatalog)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 2.dp)) {
                items(state.games.take(12), key = { "home-game-" + it.id }) { game ->
                    FeedCard(game, Modifier.width(178.dp).height(276.dp), "Detalhes", compact = true) { openGame(game) }
                }
            }
        }
        if (state.loading && state.games.isEmpty()) item { LoadingRow() }
        else if (state.games.isEmpty()) item { Empty("O catálogo não está disponível agora. Tente atualizar em alguns instantes.") }
        item { Section("Notícias", if (state.news.isEmpty()) "" else "Ver todas", news) }
        if (state.loading && state.news.isEmpty()) item { LoadingRow() }
        else if (state.news.isEmpty()) item { Empty("Nenhuma notícia nova por enquanto.") }
        else item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 2.dp)) { items(state.news.take(8), key = { "home-news-" + it.id }) { story -> FeedCard(story, Modifier.width(280.dp)) { openStory(story) } } } }
        item { Section("Vídeos", if (state.videos.isEmpty()) "" else "Ver todos", videos) }
        if (state.loading && state.videos.isEmpty()) item { LoadingRow() }
        else if (state.videos.isEmpty()) item { Empty("Novos vídeos aparecerão aqui quando estiverem disponíveis.") }
        else item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 2.dp)) { items(state.videos.take(8), key = { "home-video-" + it.id }) { video -> FeedCard(video, Modifier.width(250.dp), "Assistir") { openStory(video) } } } }
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
                        Icon(Icons.Default.Close, contentDescription = AppI18n.translate(LocalContext.current, "Limpar busca"))
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
private fun FeedCard(item: FeedItem, modifier: Modifier = Modifier, action: String? = null, compact: Boolean = false, open: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = open),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (item.image != null) {
                Box(Modifier.fillMaxWidth().height(if (compact) 106.dp else 155.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    SubcomposeAsyncImage(model = item.image, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
                        if (painter.state is AsyncImagePainter.State.Error || painter.state is AsyncImagePainter.State.Empty) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideogameAsset, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else SubcomposeAsyncImageContent()
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(if (compact) 106.dp else 112.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.VideogameAsset, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val statusLabel = when (item.status.lowercase()) { "released" -> "Lançado"; "in_development" -> "Em desenvolvimento"; "announced" -> "Anunciado"; else -> "" }
                if (statusLabel.isNotBlank()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(7.dp)) { Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.summary.isNotBlank()) Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
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
    onOpenExternal: (String) -> Unit,
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
                    SubcomposeAsyncImage(model = image, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
                        if (painter.state is AsyncImagePainter.State.Success) {
                            SubcomposeAsyncImageContent()
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideogameAsset, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, BrandInk.copy(alpha = 0.78f))
                        )
                    ))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    GameAvailabilityCard(item)
                    Button(onClick = onOpenSite, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Visitar site oficial") }
                    item.microsoftStoreUrl?.let { storeUrl ->
                        OutlinedButton(onClick = { onOpenExternal(storeUrl) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Abrir Microsoft Store") }
                    }
                    item.itchUrl?.let { itchUrl ->
                        OutlinedButton(onClick = { onOpenExternal(itchUrl) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver página no itch.io") }
                    } ?: item.purchaseUrl?.let { purchaseUrl ->
                        OutlinedButton(onClick = { onOpenExternal(purchaseUrl) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver opções de compra") }
                    }
                    OutlinedButton(onClick = onToggleWishlist, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Icon(if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isWishlisted) "Remover da lista de desejos" else "Adicionar à lista de desejos")
                    }
                } else {
                    val dateLabel = item.publishedAt.takeIf { it.isNotBlank() }?.let { formatDisplayDate(it, LocalContext.current) }.orEmpty()
                    val byline = listOf(item.author, dateLabel).filter { it.isNotBlank() }.joinToString(" · ")
                    if (byline.isNotBlank()) Text(byline, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (isGame) "Sobre o jogo" else "Artigo completo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val completeBody = item.body.ifBlank { item.summary.ifBlank { "Mais informações serão adicionadas assim que estiverem disponíveis." } }
                Text(completeBody, style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.14f)
                if (item.screenshots.isNotEmpty()) {
                    Text(if (isGame) "Capturas de tela" else "Imagens do artigo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(item.screenshots, key = { it }) { screenshot ->
                            SubcomposeAsyncImage(
                                model = screenshot,
                                contentDescription = AppI18n.translate(LocalContext.current, "Captura de tela de ${item.title}"),
                                modifier = Modifier.width(275.dp).height(160.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentScale = ContentScale.Crop
                            ) {
                                if (painter.state is AsyncImagePainter.State.Success) {
                                    SubcomposeAsyncImageContent()
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.VideogameAsset, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
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
                    if (item.url != null) Button(onClick = onOpenVideo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Assistir no app") }
                    OutlinedButton(onClick = onOpenSite, modifier = Modifier.fillMaxWidth()) { Text("Visitar Deadsmile Games") }
                }
            }
        }
    }
}

@Composable
private fun GameAvailabilityCard(item: FeedItem) {
    val context = LocalContext.current
    var now by remember(item.releaseDate, item.status) { mutableLongStateOf(System.currentTimeMillis()) }
    val target = remember(item.releaseDate) { releaseTargetMillis(item.releaseDate) }
    LaunchedEffect(target, item.status) {
        val needsCountdown = item.status.equals("in_development", true) || item.status.equals("announced", true)
        while (needsCountdown && target != null && System.currentTimeMillis() < target) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = target?.minus(now)
    val countdown = if (remaining != null && remaining > 0L) {
        val total = remaining / 1000L
        val days = total / 86_400L
        val hours = (total % 86_400L) / 3_600L
        val minutes = (total % 3_600L) / 60L
        val seconds = total % 60L
        listOfNotNull(
            days.takeIf { it > 0 }?.let { "${it}d" },
            hours.takeIf { days > 0 || it > 0 }?.let { "${it}h" },
            minutes.takeIf { days > 0 || hours > 0 || it > 0 }?.let { "${it}m" },
            seconds.takeIf { days == 0L && hours == 0L && minutes == 0L }?.let { "${it}s" }
        ).joinToString(" ")
    } else null
    val released = item.status.equals("released", true)
    val inDevelopment = item.status.equals("in_development", true)
    val announced = item.status.equals("announced", true)
    val availabilityMismatch = if (released) item.launcherAvailable != item.downloadAvailable else item.launcherAvailable || item.downloadAvailable
    val availableInLauncher = released && item.launcherAvailable && item.downloadAvailable
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(when {
                released && item.launcherAvailable -> Icons.Default.VideogameAsset
                inDevelopment || announced -> Icons.Default.Notifications
                else -> Icons.Default.Article
            }, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(when {
                    released && availabilityMismatch -> "Publicado · disponibilidade divergente"
                    availableInLauncher -> "Publicado · disponível no launcher para Windows"
                    released -> "Publicado · download ainda não configurado"
                    availabilityMismatch -> "Verificação de publicação necessária"
                    inDevelopment -> "Em desenvolvimento"
                    announced -> "Jogo anunciado"
                    else -> "Estado do jogo"
                }, fontWeight = FontWeight.SemiBold)
                Text(when {
                    released && availabilityMismatch -> "O status de publicação não confere com os indicadores do launcher. Consulte o catálogo oficial antes de considerar o jogo disponível."
                    availableInLauncher -> "O catálogo confirma que o jogo está publicado. Para instalar ou jogar, use o launcher no PC."
                    released -> "O jogo está marcado como publicado, mas a API não confirma um download configurado. Consulte os canais oficiais."
                    availabilityMismatch -> "O catálogo indica disponibilidade no launcher, mas o status ainda não confirma lançamento. Confira a página oficial do jogo."
                    inDevelopment && countdown != null -> "Lançamento previsto em $countdown."
                    inDevelopment && item.releaseDate.isNotBlank() && target != null && target <= now -> "A data prevista passou, mas o catálogo ainda marca o jogo como em desenvolvimento."
                    inDevelopment && item.releaseDate.isNotBlank() -> "Data prevista: ${formatDisplayDate(item.releaseDate, context)}."
                    inDevelopment -> "Ainda não há uma data de lançamento publicada."
                    announced && countdown != null -> "Anunciado. Lançamento previsto em $countdown."
                    announced && target != null && target <= now -> "A data prevista passou, mas o catálogo continua marcando o jogo como anunciado."
                    announced && item.releaseDate.isNotBlank() -> "Data prevista: ${formatDisplayDate(item.releaseDate, context)}."
                    announced -> "O jogo foi anunciado, mas a data de lançamento ainda não foi divulgada."
                    else -> "Não há dados suficientes para confirmar a disponibilidade deste jogo. Consulte o site oficial."
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.accessType.equals("paid", true)) Text("Acesso pago", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                else if (item.accessType.equals("free", true)) Text("Gratuito", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (item.platforms.isNotEmpty()) Text("Plataformas: ${item.platforms.joinToString(" · ")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDisplayDate(value: String, context: Context): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""
    val locale = runCatching { context.resources.configuration.locales[0] }.getOrNull() ?: java.util.Locale.getDefault()
    val date = runCatching { java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull()
        ?: return raw
    return date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))
}

private fun releaseTargetMillis(value: String): Long? {
    val raw = value.trim()
    if (raw.isBlank()) return null
    runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
    runCatching { java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
    val date = runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull() ?: return null
    return date.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
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
                        Box(Modifier.size(86.dp, 112.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            if (game.image != null) {
                                SubcomposeAsyncImage(model = game.image, contentDescription = game.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
                                    if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                                    else Icon(Icons.Default.VideogameAsset, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else Icon(Icons.Default.VideogameAsset, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Box(Modifier.size(82.dp, 96.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            if (game.image != null) {
                                SubcomposeAsyncImage(model = game.image, contentDescription = game.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
                                    if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                                    else Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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

    var profileBio by rememberSaveable { mutableStateOf("") }
    var websiteUrl by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf("") }
    var avatarUrlInput by rememberSaveable { mutableStateOf("") }
    var avatarPickerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val avatarPrefs = remember { context.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE) }
    var localAvatarPath by rememberSaveable { mutableStateOf(avatarPrefs.getString("local_avatar_path", null)) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = runCatching { copyProfileImage(context, uri) }.getOrNull()
        if (saved == null) avatarPickerMessage = "Não foi possível ler essa foto. Tente uma imagem JPG, PNG ou WebP."
        else {
            localAvatarPath = saved.absolutePath
            avatarPrefs.edit().putString("local_avatar_path", saved.absolutePath).apply()
            avatarPickerMessage = "Foto aplicada neste dispositivo. Não precisa salvar o perfil."
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
            username = profile.username
            profileBio = profile.bio
            websiteUrl = profile.websiteUrl
            location = profile.location
            avatarUrl = profile.avatarUrl
            avatarUrlInput = profile.avatarUrl.takeUnless { it.startsWith("data:image/") }.orEmpty()
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
                        AccountAvatar(localAvatarPath?.let { Uri.fromFile(File(it)).toString() } ?: profile?.avatarUrl, Modifier.size(68.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(profile?.fullName?.ifBlank { state.user.username } ?: state.user.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("@" + (profile?.username?.ifBlank { state.user.username } ?: state.user.username), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(profile?.email?.ifBlank { state.user.email } ?: state.user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.accountLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (editingProfile) {
                        HorizontalDivider()
                        OutlinedTextField(username, { username = it.take(24) }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(profileBio, { profileBio = it.take(500) }, label = { Text("Biografia") }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(websiteUrl, { websiteUrl = it.take(2000) }, label = { Text("Site pessoal") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(location, { location = it.take(120) }, label = { Text("Localização") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = avatarUrlInput,
                            onValueChange = { avatarUrlInput = it.take(5000); avatarUrl = avatarUrlInput },
                            label = { Text("URL da foto (opcional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = { avatarPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Escolher foto do dispositivo")
                        }
                        if (localAvatarPath != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AccountAvatar(Uri.fromFile(File(localAvatarPath!!)).toString(), Modifier.size(54.dp))
                                Text("Foto salva neste dispositivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { File(localAvatarPath!!).delete(); localAvatarPath = null; avatarPrefs.edit().remove("local_avatar_path").apply(); avatarPickerMessage = "Foto local removida." }) { Text("Remover") }
                            }
                        }
                        if (avatarUrl.startsWith("data:image/")) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AccountAvatar(avatarUrl, Modifier.size(54.dp))
                                Text("Foto local selecionada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { avatarUrl = ""; avatarUrlInput = "" }) { Text("Remover") }
                            }
                        }
                        avatarPickerMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(
                            onClick = { vm.saveProfile(username, profileBio, websiteUrl, location, avatarUrlInput); editingProfile = false },
                            enabled = !state.busy && username.trim().length in 3..24,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) { Text(if (state.busy) "Salvando…" else "Salvar perfil") }
                        TextButton(onClick = { editingProfile = false; profile?.let {
                            username = it.username
                            profileBio = it.bio
                            websiteUrl = it.websiteUrl
                            location = it.location
                            avatarUrl = it.avatarUrl
                            avatarUrlInput = it.avatarUrl.takeUnless { value -> value.startsWith("data:image/") }.orEmpty()
                        } }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
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

    val images = remember(state.games) {
        val abby = state.games.firstOrNull { it.title.contains("abby", true) && it.title.contains("restless", true) }
        (abby?.screenshots.orEmpty().ifEmpty { listOfNotNull(abby?.image) }).distinct().take(12)
    }
    var imageIndex by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        while (images.isNotEmpty()) {
            delay(3000)
            imageIndex = (imageIndex + 1) % images.size
        }
    }

    Box(Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background)) {
        if (images.isNotEmpty()) {
            AnimatedContent(
                targetState = imageIndex.coerceIn(0, images.lastIndex),
                transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(650)) },
                label = "login-background"
            ) { index ->
                SubcomposeAsyncImage(
                    model = images.getOrNull(index),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                ) {
                    if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                    else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        Box(Modifier.fillMaxSize().background(BrushVerticalShade()))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
        }
    }
}

@Composable
private fun BrushVerticalShade(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xAA000000), Color(0x33000000), Color(0xDD000000))
    )


@Composable
private fun AccountAvatar(url: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(url) { decodeProfileImage(url) }
    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = AppI18n.translate(LocalContext.current, "Foto do perfil"),
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        !url.isNullOrBlank() -> SubcomposeAsyncImage(
            model = url,
            contentDescription = "Foto do perfil",
            modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentScale = ContentScale.Crop
        ) {
            if (painter.state is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountCircle, null, Modifier.fillMaxSize().padding(4.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
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

private fun copyProfileImage(context: Context, uri: Uri): File? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val scaled = if (decoded.width > 768 || decoded.height > 768) {
        val ratio = minOf(768f / decoded.width, 768f / decoded.height)
        Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
    } else decoded
    val destination = File(context.filesDir, "profile-avatar.jpg")
    val ok = runCatching { destination.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) } }.getOrDefault(false)
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    return destination.takeIf { ok && it.isFile && it.length() > 0L }
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
    pad: PaddingValues,
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    dynamicColors: Boolean,
    onDynamicColors: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    appLanguage: String,
    onAppLanguage: (String) -> Unit,
    onOpenUpdates: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
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
                    Text("Idioma", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("O idioma é aplicado em todo o app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("pt-BR" to "Português", "en" to "English", "es" to "Español").forEach { (code, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onAppLanguage(code) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = appLanguage == code, onClick = { onAppLanguage(code) })
                            Text(label, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aparência", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    listOf("Seguir o sistema", "Claro", "Escuro", "OLED (preto puro)").forEachIndexed { index, label ->
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
                    Text("Deadsmile Games Companion for Android", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Atualizações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Consulte o histórico de versões e os APKs publicados no GitHub.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onOpenUpdates, modifier = Modifier.fillMaxWidth()) { Text("Ver histórico de atualizações") }
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
        val uri = Uri.parse(value)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo.isNullOrEmpty())
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}


private fun youtubeVideoId(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val candidate = when {
        host == "youtu.be" -> uri.lastPathSegment
        host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") ->
            if (uri.path == "/watch") uri.getQueryParameter("v") else {
                val segments = uri.pathSegments
                when (segments.firstOrNull()) { "embed", "shorts", "live" -> segments.getOrNull(1); else -> null }
            }
        else -> null
    }?.trim()
    return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
}

@Composable
private fun VideoPlayerScreen(item: FeedItem, pad: PaddingValues) {
    val context = LocalContext.current
    val videoId = remember(item.url) { item.url?.let(::youtubeVideoId) }
    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (videoId == null) {
            Empty("Este conteúdo não oferece um player do YouTube compatível.")
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl("https://www.youtube-nocookie.com/embed/" + videoId + "?playsinline=1&rel=0")
                    }
                },
                update = { view ->
                    val expected = "https://www.youtube-nocookie.com/embed/" + videoId + "?playsinline=1&rel=0"
                    if (view.url != expected) view.loadUrl(expected)
                }
            )
            Text("Reprodução integrada ao Companion", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LanguageSelectionOverlay(onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.76f)).padding(20.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 420.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escolha seu idioma", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Você pode mudar isso depois nas configurações.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onSelect("pt-BR") }, modifier = Modifier.fillMaxWidth()) { Text("Português (Brasil)") }
                OutlinedButton(onClick = { onSelect("en") }, modifier = Modifier.fillMaxWidth()) { Text("English") }
                OutlinedButton(onClick = { onSelect("es") }, modifier = Modifier.fillMaxWidth()) { Text("Español") }
            }
        }
    }
}

@Composable
private fun GameCatalogScreen(content: List<FeedItem>, loading: Boolean, pad: PaddingValues, open: (FeedItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(content, query) {
        val q = query.trim()
        if (q.isBlank()) content else content.filter {
            it.title.contains(q, true) || it.summary.contains(q, true) || it.genres.any { g -> g.contains(q, true) }
        }
    }
    if (loading && content.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize().padding(pad)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text("Buscar jogos") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpar busca") } }
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) { Empty(if (content.isEmpty()) "O catálogo não está disponível agora. Tente atualizar em alguns instantes." else "Nenhum jogo corresponde à busca.") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(filtered, key = { it.id }) { game ->
                    Card(
                        modifier = Modifier.fillMaxWidth().height(276.dp).clickable { open(game) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().height(156.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                if (game.image != null) SubcomposeAsyncImage(
                                    model = game.image, contentDescription = game.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                ) {
                                    if (painter.state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                                    else Icon(Icons.Default.VideogameAsset, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else Icon(Icons.Default.VideogameAsset, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(game.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(game.summary.ifBlank { game.genres.take(2).joinToString(" · ") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                Text("Ver detalhes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(pad: PaddingValues) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(games.deadsmile.mobile.data.LocalNotificationStore.load(context)) }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notificações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Salvas neste dispositivo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { games.deadsmile.mobile.data.LocalNotificationStore.clear(context); notifications = emptyList() }, enabled = notifications.isNotEmpty()) { Text("Limpar") }
        }
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Empty("Você ainda não tem notificações. As novidades aparecerão aqui.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(notifications, key = { it.id }) { notice ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(notice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(notice.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(notice.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}
