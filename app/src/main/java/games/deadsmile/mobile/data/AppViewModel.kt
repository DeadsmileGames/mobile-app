package games.deadsmile.mobile.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
    init { reload(); account() }

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
                val news = async { completeOffsetFeed(initialNews, 24) { offset ->
                    api.get("/newswire?limit=24&offset=" + offset).feed("items", "news")
                } }
                val videos = async { completeOffsetFeed(initialVideos, 24) { offset ->
                    api.get("/videos?limit=24&offset=" + offset).feed("items", "videos")
                } }
                val totalPages = initial.third?.optJSONObject("pagination")
                    ?.optInt("totalPages", 1)?.coerceIn(1, 999) ?: 1
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
        fetch: suspend (offset: Int) -> List<FeedItem>
    ): List<FeedItem> {
        val collected = first.toMutableList()
        var offset = first.size
        var pages = 1
        while (first.size >= pageSize && pages < 40) {
            val page = runCatching { fetch(offset) }.getOrDefault(emptyList())
            if (page.isEmpty()) break
            collected.addAll(page)
            offset += page.size
            pages++
            if (page.size < pageSize) break
        }
        return collected.distinctBy { it.id }
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
        return (first + remaining.flatten()).distinctBy { it.id }
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

    fun saveProfile(fullName: String, username: String, bio: String, websiteUrl: String, location: String, avatarUrl: String) = viewModelScope.launch {
        if (mutable.value.busy) return@launch
        val user = mutable.value.user ?: return@launch
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            val fallback = mutable.value.accountProfile ?: AccountProfile(username = user.username, email = user.email, bio = user.bio)
            val result = withContext(Dispatchers.IO) {
                api.patch("/account", JSONObject()
                    .put("fullName", fullName.trim())
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
