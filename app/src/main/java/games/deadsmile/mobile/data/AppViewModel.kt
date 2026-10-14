package games.deadsmile.mobile.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class UiState(
    val news: List<FeedItem> = emptyList(), val videos: List<FeedItem> = emptyList(),
    val games: List<FeedItem> = emptyList(), val library: List<FeedItem> = emptyList(), val wishlist: List<FeedItem> = emptyList(),
    val user: UserAccount? = null, val loading: Boolean = true, val busy: Boolean = false,
    val twoFactor: Boolean = false, val message: String? = null, val error: String? = null,
    val detail: FeedItem? = null, val detailLoading: Boolean = false,
    val accountProfile: AccountProfile? = null,
    val accountLoading: Boolean = false,
    val privacy: PrivacyOptions = PrivacyOptions(),
    val sessions: List<ActiveSession> = emptyList(),
    val accountTwoFactorEnabled: Boolean = false,
    val twoFactorSetupSecret: String = "",
    val twoFactorSetupUri: String = ""
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient(app.applicationContext)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()

    private var reloadGeneration = 0
    private val realtimeClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    @Volatile private var realtimeActive = false
    @Volatile private var realtimeSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var realtimeRefreshJob: Job? = null
    @Volatile private var reconnectDelayMs = 1_000L

    init { reload(); account() }

    fun connectRealtime() {
        if (realtimeActive) return
        realtimeActive = true
        openRealtimeSocket()
    }

    fun disconnectRealtime() {
        realtimeActive = false
        reconnectJob?.cancel()
        reconnectJob = null
        realtimeSocket?.close(1000, "activity stopped")
        realtimeSocket = null
    }

    private fun openRealtimeSocket() {
        if (!realtimeActive || realtimeSocket != null) return
        val request = Request.Builder()
            .url("wss://api-ust8.onrender.com/api/live")
            .header("Origin", "deadsmile-app://launcher")
            .build()
        val socket = realtimeClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!realtimeActive) { webSocket.close(1000, "activity stopped"); return }
                realtimeSocket = webSocket
                reconnectDelayMs = 1_000L
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (realtimeActive) handleRealtimeMessage(text)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (realtimeSocket === webSocket) realtimeSocket = null
                scheduleRealtimeReconnect()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (realtimeSocket === webSocket) realtimeSocket = null
                scheduleRealtimeReconnect()
            }
        })
        if (realtimeActive) realtimeSocket = socket else socket.cancel()
    }

    private fun scheduleRealtimeReconnect() {
        if (!realtimeActive || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2L).coerceAtMost(30_000L)
            if (realtimeActive && realtimeSocket == null) openRealtimeSocket()
        }
    }

    private fun handleRealtimeMessage(raw: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = event.optString("event_type").ifBlank { event.optString("type") }.trim()
        if (type.isBlank() || type.equals("connected", true)) return
        RealtimeNotificationDispatcher.dispatch(getApplication(), event)
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = viewModelScope.launch {
            delay(250L)
            refreshFirstPages(event, type)
            if (type.startsWith("wishlist.", true) || type.startsWith("account.", true)) account()
        }
    }

    private suspend fun refreshFirstPages(event: JSONObject, type: String) {
        val responses = withContext(Dispatchers.IO) {
            coroutineScope {
                val news = async { runCatching { api.get("/newswire?limit=24&offset=0") }.getOrNull() }
                val videos = async { runCatching { api.get("/videos?limit=24&offset=0") }.getOrNull() }
                val games = async { runCatching { api.get("/games?page=1&limit=48") }.getOrNull() }
                Triple(news.await(), videos.await(), games.await())
            }
        }
        val freshNews = responses.first?.feed("items", "news")
        val freshVideos = responses.second?.feed("items", "videos")
        val freshGames = responses.third?.feed("items", "games")
        val entityId = event.optString("entity_id")
        val deleted = type.endsWith(".deleted", true)
        fun mergeFresh(fresh: List<FeedItem>?, existing: List<FeedItem>): List<FeedItem> {
            val prior = if (deleted && entityId.isNotBlank()) existing.filterNot { it.id.equals(entityId, true) } else existing
            return if (fresh == null) prior else (fresh + prior).distinctBy { it.id }.take(10_000)
        }
        mutable.value = mutable.value.copy(
            news = mergeFresh(freshNews, mutable.value.news),
            videos = mergeFresh(freshVideos, mutable.value.videos),
            games = mergeFresh(freshGames, mutable.value.games)
        )
        val detail = mutable.value.detail
        if (detail != null && (detail.id.equals(entityId, true) || detail.slug.equals(event.optString("slug"), true))) {
            val matchingGame = type.startsWith("game.", true)
            loadDetails(detail, matchingGame)
        }
    }

    override fun onCleared() {
        disconnectRealtime()
        realtimeRefreshJob?.cancel()
        super.onCleared()
    }

    fun reload() = viewModelScope.launch {
        val generation = ++reloadGeneration
        mutable.value = mutable.value.copy(loading = true, error = null)
        val initial = withContext(Dispatchers.IO) {
            coroutineScope {
                val news = async { runCatching { api.get("/newswire?limit=24&offset=0") }.getOrNull() }
                val videos = async { runCatching { api.get("/videos?limit=24&offset=0") }.getOrNull() }
                val games = async { runCatching { api.get("/games?page=1&limit=48") }.getOrNull() }
                Triple(news.await(), videos.await(), games.await())
            }
        }
        if (generation != reloadGeneration) return@launch
        val initialNews = initial.first?.feed("items", "news") ?: emptyList()
        val initialVideos = initial.second?.feed("items", "videos") ?: emptyList()
        val initialGames = initial.third?.feed("items", "games") ?: emptyList()
        mutable.value = mutable.value.copy(news = initialNews, videos = initialVideos, games = initialGames, loading = false)

        // First-page content is visible immediately; the rest of the catalog fills in
        // quietly in the background so the mobile UI is never held behind pagination.
        val all = withContext(Dispatchers.IO) {
            coroutineScope {
                val newsPages = initial.first?.optJSONObject("pagination")
                    ?.optInt("totalPages", if (initialNews.size >= 24) 40 else 1)?.coerceIn(1, 400)
                    ?: if (initialNews.size >= 24) 40 else 1
                val videoPages = initial.second?.optJSONObject("pagination")
                    ?.optInt("totalPages", if (initialVideos.size >= 24) 40 else 1)?.coerceIn(1, 400)
                    ?: if (initialVideos.size >= 24) 40 else 1
                val news = async { completeOffsetFeed(initialNews, 24, newsPages) { offset ->
                    api.get("/newswire?limit=24&offset=" + offset).feed("items", "news")
                } }
                val videos = async { completeOffsetFeed(initialVideos, 24, videoPages) { offset ->
                    api.get("/videos?limit=24&offset=" + offset).feed("items", "videos")
                } }
                val totalPages = initial.third?.optJSONObject("pagination")
                    ?.optInt("totalPages", 1)?.coerceIn(1, 209) ?: 1
                val games = async { completePagedGames(initialGames, totalPages) { page ->
                    api.get("/games?page=" + page + "&limit=48").feed("items", "games")
                } }
                Triple(news.await(), videos.await(), games.await())
            }
        }
        if (generation != reloadGeneration) return@launch
        mutable.value = mutable.value.copy(news = all.first, videos = all.second, games = all.third)
    }

    private suspend fun completeOffsetFeed(
        first: List<FeedItem>,
        pageSize: Int,
        totalPages: Int,
        fetch: suspend (offset: Int) -> List<FeedItem>
    ): List<FeedItem> {
        if (totalPages <= 1) return first
        val gate = Semaphore(4)
        val remaining = coroutineScope {
            (1 until totalPages).map { page ->
                async {
                    gate.withPermit {
                        runCatching { fetch(page * pageSize) }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll()
        }
        return (first + remaining.flatten()).distinctBy { it.id }.take(10_000)
    }

    private suspend fun completePagedGames(
        first: List<FeedItem>,
        totalPages: Int,
        fetch: suspend (page: Int) -> List<FeedItem>
    ): List<FeedItem> {
        if (totalPages <= 1) return first
        val gate = Semaphore(4)
        val remaining = coroutineScope {
            (2..totalPages).map { page ->
                async {
                    gate.withPermit { runCatching { fetch(page) }.getOrDefault(emptyList()) }
                }
            }.awaitAll()
        }
        return (first + remaining.flatten()).distinctBy { it.id }.take(10_000)
    }
    fun loadDetails(item: FeedItem, isGame: Boolean) = viewModelScope.launch {
        mutable.value = mutable.value.copy(detail = item, detailLoading = true)
        val slug = item.slug.trim()
        if (!slug.matches(Regex("[A-Za-z0-9-]{1,160}"))) {
            mutable.value = mutable.value.copy(detailLoading = false)
            return@launch
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val route = if (isGame) "/games/" + slug else "/newswire/" + slug
                api.get(route).detailItem(item)
            }.getOrNull()
        }
        mutable.value = mutable.value.copy(
            detail = loaded ?: item,
            detailLoading = false,
            message = if (loaded == null && item.body.isBlank()) "Não foi possível carregar o conteúdo completo agora. Tente novamente." else null
        )
    }

    fun account() = viewModelScope.launch {
        val user = withContext(Dispatchers.IO) { runCatching { api.get("/auth/me").userAccount() }.getOrNull() }
        mutable.value = mutable.value.copy(user = user)
        if (user != null) {
            library()
            loadWishlist()
            loadAccountSettings()
        } else {
            mutable.value = mutable.value.copy(accountProfile = null, sessions = emptyList(), privacy = PrivacyOptions(), accountTwoFactorEnabled = false, twoFactorSetupSecret = "", twoFactorSetupUri = "")
        }
    }
    fun loadAccountSettings() = viewModelScope.launch {
        val user = mutable.value.user ?: return@launch
        mutable.value = mutable.value.copy(accountLoading = true)
        val fallback = AccountProfile(username = user.username, email = user.email, bio = user.bio)
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                val profile = async { runCatching { api.get("/account").accountProfile(fallback) }.getOrNull() }
                val privacy = async { runCatching { api.get("/account/privacy").privacyOptions() }.getOrNull() }
                val totp = async { runCatching { api.get("/account/totp/status") }.getOrNull() }
                val sessions = async { runCatching { api.get("/account/sessions").activeSessions() }.getOrNull() }
                listOf(profile.await(), privacy.await(), totp.await(), sessions.await())
            }
        }
        val profile = loaded[0] as? AccountProfile
        val privacy = loaded[1] as? PrivacyOptions
        val totp = loaded[2] as? JSONObject
        @Suppress("UNCHECKED_CAST")
        val sessions = loaded[3] as? List<ActiveSession>
        mutable.value = mutable.value.copy(
            accountProfile = profile ?: mutable.value.accountProfile ?: fallback,
            privacy = privacy ?: mutable.value.privacy,
            sessions = sessions ?: mutable.value.sessions,
            accountTwoFactorEnabled = totp?.optBoolean("enabled", false) ?: mutable.value.accountTwoFactorEnabled,
            accountLoading = false
        )
    }

    fun saveProfile(username: String, bio: String, websiteUrl: String, location: String, avatarUrl: String) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        val user = mutable.value.user ?: return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            val fallback = mutable.value.accountProfile ?: AccountProfile(username = user.username, email = user.email, bio = user.bio)
            val result = withContext(Dispatchers.IO) {
                api.patch("/account", JSONObject()
                    .put("username", username.trim())
                    .put("bio", bio)
                    .put("websiteUrl", websiteUrl.trim())
                    .put("location", location.trim())
                    .put("avatarUrl", avatarUrl.trim()))
            }
            mutable.value = mutable.value.copy(
                accountProfile = result.accountProfile(fallback),
                message = "Perfil atualizado."
            )
            account()
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun changeEmail(email: String, password: String) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) {
                api.post("/account/email/change", JSONObject().put("email", email.trim().lowercase()).put("password", password))
            }
            mutable.value = mutable.value.copy(message = "Enviamos um link de confirmação. Seu e-mail atual continua ativo até a confirmação.")
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun savePrivacy(options: PrivacyOptions) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            val result = withContext(Dispatchers.IO) {
                api.put("/account/privacy", JSONObject()
                    .put("shareGameActivity", options.shareGameActivity)
                    .put("sharePlaytime", options.shareGameActivity && options.sharePlaytime)
                    .put("shareAchievements", options.shareAchievements))
            }
            mutable.value = mutable.value.copy(privacy = result.privacyOptions(), message = "Preferências de privacidade salvas.")
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun revokeSession(id: String) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) { api.delete("/account/sessions/" + android.net.Uri.encode(id)) }
            mutable.value = mutable.value.copy(message = "Dispositivo desconectado.")
            loadAccountSettings()
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun startTwoFactorSetup() = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null, twoFactorSetupSecret = "", twoFactorSetupUri = "")
        try {
            val result = withContext(Dispatchers.IO) { api.post("/account/totp/setup", JSONObject()) }
            val secret = result.optString("secret")
            if (secret.isBlank()) throw ApiFailure(0, "INVALID_RESPONSE", "Não foi possível iniciar a verificação em duas etapas.")
            val uri = result.optString("otpauthUrl").ifBlank {
                result.optString("otpauth_uri").ifBlank { result.optString("provisioningUri") }
            }
            mutable.value = mutable.value.copy(twoFactorSetupSecret = secret, twoFactorSetupUri = uri, message = "Agora adicione a chave ao seu aplicativo autenticador.")
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun confirmTwoFactor(token: String, disable: Boolean = false) = viewModelScope.launch {
        if (mutable.value.busy || token.isBlank()) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) {
                if (disable) api.delete("/account/totp/disable", JSONObject().put("token", token.trim()))
                else api.post("/account/totp/enable", JSONObject().put("token", token.trim()))
            }
            mutable.value = mutable.value.copy(
                accountTwoFactorEnabled = !disable,
                twoFactorSetupSecret = "",
                twoFactorSetupUri = "",
                message = if (disable) "Verificação em duas etapas desativada." else "Verificação em duas etapas ativada."
            )
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun loadWishlist() = viewModelScope.launch {
        if (mutable.value.user == null) {
            mutable.value = mutable.value.copy(wishlist = emptyList())
            return@launch
        }
        val list = withContext(Dispatchers.IO) {
            runCatching { api.get("/wishlist").feed("items", "wishlist", "games") }.getOrDefault(emptyList())
        }
        mutable.value = mutable.value.copy(wishlist = list)
    }

    fun toggleWishlist(item: FeedItem) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        if (mutable.value.user == null) {
            mutable.value = mutable.value.copy(message = "Entre na conta para usar sua lista de desejos.")
            return@launch
        }
        val id = item.id
        if (!id.matches(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))) {
            mutable.value = mutable.value.copy(error = "Não foi possível identificar este jogo para sua lista de desejos.")
            return@launch
        }
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            val inWishlist = mutable.value.wishlist.any { it.id.equals(id, ignoreCase = true) }
            withContext(Dispatchers.IO) {
                if (inWishlist) api.delete("/wishlist/" + id)
                else api.post("/wishlist", JSONObject().put("gameId", id))
            }
            mutable.value = mutable.value.copy(
                wishlist = if (inWishlist) mutable.value.wishlist.filterNot { it.id.equals(id, true) }
                    else (mutable.value.wishlist + item).distinctBy { it.id },
                message = if (inWishlist) "Jogo removido da lista de desejos." else "Jogo adicionado à lista de desejos."
            )
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun removeWishlist(id: String) = viewModelScope.launch {
        if (mutable.value.busy || mutable.value.user == null) return@launch
        if (!id.matches(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) { api.delete("/wishlist/" + id) }
            mutable.value = mutable.value.copy(wishlist = mutable.value.wishlist.filterNot { it.id.equals(id, true) }, message = "Jogo removido da lista de desejos.")
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    fun library() = viewModelScope.launch {
        if (mutable.value.user == null) return@launch
        val list = withContext(Dispatchers.IO) { runCatching { api.get("/library").feed("items", "games", "library") }.getOrDefault(emptyList()) }
        mutable.value = mutable.value.copy(library = list)
    }
    fun login(email: String, password: String) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        try {
            val result = withContext(Dispatchers.IO) { api.post("/auth/mobile-login", JSONObject().put("email", email.trim()).put("password", password)) }
            if (result.optBoolean("requiresTwoFactor")) mutable.value = mutable.value.copy(twoFactor = true, message = "Digite o código de autenticação em duas etapas.")
            else { mutable.value = mutable.value.copy(message = "Login realizado.", twoFactor = false); account() }
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }
    fun verify(code: String) = viewModelScope.launch {
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) { api.post("/auth/verify-2fa", JSONObject().put("token", code.trim())) }
            mutable.value = mutable.value.copy(twoFactor = false, message = "Autenticação concluída.")
            account()
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }
    fun register(username: String, email: String, password: String) = viewModelScope.launch {
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        try {
            withContext(Dispatchers.IO) { api.post("/auth/mobile-register", JSONObject().put("username", username.trim()).put("email", email.trim()).put("password", password)) }
            mutable.value = mutable.value.copy(message = "Conta criada. Confira seu e-mail para concluir a verificação.")
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }
    fun rename(username: String) = viewModelScope.launch {
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) { api.patch("/account", JSONObject().put("username", username.trim())) }
            mutable.value = mutable.value.copy(message = "Perfil atualizado.")
            account()
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = safe(e)) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }
    fun logout() = viewModelScope.launch {
        mutable.value = mutable.value.copy(busy = true)
        try { withContext(Dispatchers.IO) { api.post("/auth/logout", JSONObject()) } } catch (_: Exception) { }
        withContext(Dispatchers.IO) { api.clearSession() }
        mutable.value = mutable.value.copy(user = null, accountProfile = null, library = emptyList(), wishlist = emptyList(), sessions = emptyList(), busy = false, twoFactor = false, accountTwoFactorEnabled = false, twoFactorSetupSecret = "", twoFactorSetupUri = "", message = "Você saiu da conta.")
    }
    fun dismiss() { mutable.value = mutable.value.copy(message = null, error = null) }
    private fun safe(e: Exception): String {
        if (e !is ApiFailure) return "Não foi possível conectar. Confira sua internet e tente novamente."
        return when (e.status) {
            400 -> "Confira os dados informados e tente novamente."
            401 -> "E-mail ou senha não conferem."
            403 -> "Não foi possível autorizar esta ação. Confira sua conta e tente novamente."
            404 -> "Este recurso não está disponível no momento."
            409 -> "Esses dados já estão em uso. Confira as informações."
            422 -> "Algumas informações precisam ser corrigidas."
            429 -> "Muitas tentativas em sequência. Aguarde um pouco e tente novamente."
            in 500..599 -> "O serviço está indisponível agora. Tente novamente mais tarde."
            else -> "Não foi possível concluir agora. Tente novamente."
        }
    }
}
