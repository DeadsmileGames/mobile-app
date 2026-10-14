package games.deadsmile.mobile.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UiState(
    val news: List<FeedItem> = emptyList(), val videos: List<FeedItem> = emptyList(),
    val games: List<FeedItem> = emptyList(), val library: List<FeedItem> = emptyList(),
    val user: UserAccount? = null, val loading: Boolean = true, val busy: Boolean = false,
    val twoFactor: Boolean = false, val message: String? = null, val error: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient(app.applicationContext)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()

    init { reload(); account() }
    fun reload() = viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, error = null)
        val content = withContext(Dispatchers.IO) {
            Triple(
                runCatching { api.get("/newswire?limit=24&offset=0").feed("items", "news") }.getOrDefault(emptyList()),
                runCatching { api.get("/videos?limit=24&offset=0").feed("items", "videos") }.getOrDefault(emptyList()),
                runCatching { api.get("/games?page=1&limit=48").feed("items", "games") }.getOrDefault(emptyList())
            )
        }
        mutable.value = mutable.value.copy(news = content.first, videos = content.second, games = content.third, loading = false)
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
    private fun safe(e: Exception) = if (e is ApiFailure) e.message.take(240) else "Não foi possível conectar. Verifique sua internet e tente novamente."
}
