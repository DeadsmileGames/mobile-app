package games.deadsmile.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import games.deadsmile.mobile.data.AppViewModel
import games.deadsmile.mobile.data.FeedItem
import games.deadsmile.mobile.data.UiState

private enum class Page(val label: String) { HOME("Início"), NEWS("Notícias"), VIDEOS("Vídeos"), LIBRARY("Biblioteca"), ACCOUNT("Conta") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) { Surface(Modifier.fillMaxSize()) { DeadsmileRoot() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadsmileRoot(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var selected by remember { mutableStateOf<FeedItem?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let { snackbar.showSnackbar(it) }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("Deadsmile", fontWeight = FontWeight.Bold); Text(page.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            actions = { IconButton(onClick = { if (page == Page.ACCOUNT) vm.account() else if (page == Page.LIBRARY) vm.library() else vm.reload() }) { Icon(Icons.Default.Refresh, "Atualizar") } }) },
        bottomBar = { NavigationBar { Page.entries.forEach { p ->
            val icon = when (p) { Page.HOME -> Icons.Default.Home; Page.NEWS -> Icons.Default.Article; Page.VIDEOS -> Icons.Default.Videocam; Page.LIBRARY -> Icons.Default.CollectionsBookmark; Page.ACCOUNT -> Icons.Default.AccountCircle }
            NavigationBarItem(selected = page == p, onClick = { page = p }, icon = { Icon(icon, p.label) }, label = { Text(p.label) })
        } } },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        when (page) {
            Page.HOME -> Home(state, pad, { page = Page.NEWS }, { page = Page.VIDEOS }, { selected = it })
            Page.NEWS -> FeedScreen("Últimas notícias", state.news, state.loading, pad) { selected = it }
            Page.VIDEOS -> FeedScreen("Vídeos", state.videos, state.loading, pad) {
                val url = it.url
                if (url != null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } else selected = it
            }
            Page.LIBRARY -> LibraryScreen(state, pad) { page = Page.ACCOUNT }
            Page.ACCOUNT -> AccountScreen(state, pad, vm)
        }
    }
    selected?.let { item ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(item.title) }, text = {
            Column {
                item.image?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.height(10.dp)) }
                Text(item.body.ifBlank { item.summary.ifBlank { "Sem detalhes adicionais." } })
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } })
    }
}

@Composable
private fun Home(state: UiState, pad: PaddingValues, news: () -> Unit, videos: () -> Unit, open: (FeedItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(22.dp)) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(10.dp))
                Text("Seu universo Deadsmile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Notícias, vídeos e sua biblioteca de jogos.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = news) { Text("Notícias") }; OutlinedButton(onClick = videos) { Text("Vídeos") } }
            }
        } }
        item { Section("Notícias", "Ver todas", news) }
        if (state.news.isEmpty() && !state.loading) item { Empty("Nenhuma notícia disponível agora.") }
        if (state.news.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.news.take(8), key = { it.id }) { FeedCard(it, Modifier.width(280.dp)) { open(it) } } } }
        item { Section("Vídeos", "Ver todos", videos) }
        if (state.videos.isEmpty() && !state.loading) item { Empty("Nenhum vídeo disponível agora.") }
        if (state.videos.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.videos.take(8), key = { it.id }) { FeedCard(it, Modifier.width(240.dp)) { open(it) } } } }
        item { Section("Jogos", "Ver catálogo", {}) }
        if (state.games.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.games.take(8), key = { it.id }) { FeedCard(it, Modifier.width(210.dp)) { open(it) } } } }
    }
}

@Composable
private fun FeedScreen(title: String, items: List<FeedItem>, loading: Boolean, pad: PaddingValues, open: (FeedItem) -> Unit) {
    if (loading && items.isEmpty()) { Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    if (items.isEmpty()) { Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) { Empty("Não há conteúdo para exibir agora.") }; return }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(items, key = { it.id }) { FeedCard(it, Modifier.fillMaxWidth()) { open(it) } }
    }
}

@Composable
private fun FeedCard(item: FeedItem, modifier: Modifier = Modifier, open: () -> Unit) {
    Card(modifier.clickable(onClick = open), shape = RoundedCornerShape(22.dp)) {
        Column {
            if (item.image != null) AsyncImage(item.image, item.title, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(34.dp)) }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                if (item.summary.isNotBlank()) Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: UiState, pad: PaddingValues, goAccount: () -> Unit) {
    if (state.user == null) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(36.dp))
                Text("Entre para ver sua biblioteca", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Esta tela é somente para consultar os jogos da sua conta. Jogar pelo celular não está disponível.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = goAccount) { Text("Entrar") }
            } }
        }
    } else if (state.library.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) { Empty("Sua biblioteca está vazia ou ainda não foi carregada.") }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Sua biblioteca", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Somente visualização · jogos para PC", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.library, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.image != null) AsyncImage(item.image, item.title, Modifier.size(82.dp, 96.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(82.dp, 96.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Icons.Default.CollectionsBookmark, null) }
                    Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); if (item.summary.isNotBlank()) Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant); AssistChip(onClick = {}, label = { Text("Na biblioteca") }) }
                } }
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
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(shape = RoundedCornerShape(26.dp)) { Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AccountCircle, null, Modifier.size(56.dp))
                Text(state.user.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                if (editing) {
                    OutlinedTextField(username, { username = it }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.rename(username); editing = false }, enabled = !state.busy && username.length in 3..24, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
                } else OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Editar nome de usuário") }
                Button(onClick = vm::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sair da conta") }
            } }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (state.twoFactor) "Verificação em duas etapas" else if (registerMode) "Criar conta" else "Bem-vindo de volta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Acesse seu perfil e consulte a biblioteca Deadsmile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.twoFactor) {
            OutlinedTextField(code, { code = it.take(10) }, label = { Text("Código de autenticação") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.verify(code) }, enabled = !state.busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Confirmar código") }
        } else {
            if (registerMode) OutlinedTextField(username, { username = it }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (registerMode) vm.register(username, email, password) else vm.login(email, password) }, enabled = !state.busy && email.contains("@") && password.isNotBlank() && (!registerMode || username.length in 3..24), modifier = Modifier.fillMaxWidth()) { if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (registerMode) "Criar conta" else "Entrar") }
            TextButton(onClick = { registerMode = !registerMode }) { Text(if (registerMode) "Já tenho uma conta" else "Criar uma conta") }
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://deadsmilegames.vercel.app"))) } }) { Text("Ajuda e recuperação de conta no site") }
        }
        Text("As credenciais são enviadas somente à API oficial por HTTPS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable private fun Section(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClick) { Text(action) }
    }
}
@Composable private fun Empty(message: String) {
    Card(shape = RoundedCornerShape(20.dp)) { Text(message, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
