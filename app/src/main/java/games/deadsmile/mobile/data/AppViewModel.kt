package games.deadsmile.mobile.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UiState(
    val news: List<FeedItem> = emptyList(), val videos: List<FeedItem> = emptyList(),
    val games: List<FeedItem> = emptyList(), val library: List<FeedItem> = emptyList(),
    val user: UserAccount? = null, val loading: Boolean = true, val busy: Boolean = false,
    val twoFactor: Boolean = false, val message: String? = null, val error: String? = null,
    val detail: FeedItem? = null, val detailLoading: Boolean = false
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
                    ?.optInt("totalPages", 1)?.coerceIn(1, 100) ?: 1
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
        val collected = first.toMutableList()
        for (page in 2..totalPages) {
            val batch = runCatching { fetch(page) }.getOrDefault(emptyList())
            if (batch.isEmpty()) break
            collected.addAll(batch)
        }
        return collected.distinctBy { it.id }
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
        if (user != null) library()
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
        mutable.value = mutable.value.copy(user = null, library = emptyList(), busy = false, twoFactor = false, message = "Você saiu da conta.")
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
