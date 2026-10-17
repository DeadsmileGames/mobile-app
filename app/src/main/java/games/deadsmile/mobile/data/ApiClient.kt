package games.deadsmile.mobile.data

import android.content.Context
import games.deadsmile.mobile.BuildConfig
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiFailure(val status: Int, val code: String, override val message: String) : IOException(message)

class ApiClient(context: Context) {
    companion object {
        const val BASE = "https://api-ust8.onrender.com/api"
        private const val HOST = "api-ust8.onrender.com"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        context, "deadsmile-session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val jar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (url.host != HOST) return emptyList()
            val arr = runCatching { JSONArray(prefs.getString("cookies", "[]")) }.getOrDefault(JSONArray())
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val c = runCatching {
                        val b = Cookie.Builder().name(o.getString("name")).value(o.getString("value")).path(o.optString("path", "/"))
                        if (o.optBoolean("hostOnly")) b.hostOnlyDomain(o.getString("domain")) else b.domain(o.getString("domain"))
                        if (o.optBoolean("secure")) b.secure()
                        if (o.optBoolean("httpOnly")) b.httpOnly()
                        b.expiresAt(o.optLong("expiresAt", System.currentTimeMillis() + 86_400_000L)).build()
                    }.getOrNull()
                    if (c != null && c.expiresAt > System.currentTimeMillis() && c.matches(url)) add(c)
                }
            }
        }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (url.host != HOST) return
            val arr = runCatching { JSONArray(prefs.getString("cookies", "[]")) }.getOrDefault(JSONArray())
            val map = linkedMapOf<String, JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                map["${o.optString("name")}|${o.optString("domain")}|${o.optString("path")}"] = o
            }
            cookies.filter { it.domain == HOST }.forEach { c ->
                val key = "${c.name}|${c.domain}|${c.path}"
                if (c.expiresAt <= System.currentTimeMillis()) map.remove(key)
                else map[key] = JSONObject().put("name", c.name).put("value", c.value).put("domain", c.domain)
                    .put("path", c.path).put("expiresAt", c.expiresAt).put("hostOnly", c.hostOnly)
                    .put("secure", c.secure).put("httpOnly", c.httpOnly)
            }
            prefs.edit().putString("cookies", JSONArray(map.values).toString()).apply()
        }
    }
    private val client = OkHttpClient.Builder().cookieJar(jar).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    @Volatile private var csrf: String? = null

    fun get(path: String) = request("GET", path)
    fun post(path: String, body: JSONObject) = request("POST", path, body)
    fun patch(path: String, body: JSONObject) = request("PATCH", path, body)
    fun put(path: String, body: JSONObject) = request("PUT", path, body)
    fun delete(path: String, body: JSONObject? = null) = request("DELETE", path, body)
    fun clearSession() { prefs.edit().remove("cookies").remove("access_token").apply(); csrf = null }

    @Synchronized private fun token(force: Boolean = false): String {
        if (!force) csrf?.let { return it }
        val result = execute("GET", "/csrf", null, null).optString("token")
        if (result.isBlank() || result.length > 256) throw ApiFailure(0, "CSRF_INIT_FAILED", "Não foi possível iniciar uma sessão segura.")
        csrf = result
        return result
    }
    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains("..") && !path.contains('#'))
        val csrfHeader = if (method in setOf("POST", "PUT", "PATCH", "DELETE")) token() else null
        val tokenEndpoint = path == "/auth/mobile-token"
        val sessionEstablishing = path in setOf("/auth/mobile-login", "/auth/mobile-register", "/auth/verify-2fa")
        if (!tokenEndpoint && !sessionEstablishing) runCatching { refreshAccessTokenIfNeeded(false) }
        val bearer = if (tokenEndpoint || sessionEstablishing) null else prefs.getString("access_token", null)
        return try {
            val result = try {
                execute(method, path, body, csrfHeader, bearer)
            } catch (e: ApiFailure) {
                if (e.status == 403 && e.code == "CSRF_VALIDATION_FAILED" && csrfHeader != null) {
                    execute(method, path, body, token(force = true), bearer)
                } else {
                    throw e
                }
            }
            if ((path == "/auth/mobile-login" && !result.optBoolean("requiresTwoFactor")) || path == "/auth/verify-2fa") {
                runCatching { refreshAccessTokenIfNeeded(true) }
            }
            result
        } catch (e: ApiFailure) {
            if (bearer != null && e.status == 401 && e.code in setOf("ACCESS_TOKEN_EXPIRED", "INVALID_ACCESS_TOKEN", "SESSION_EXPIRED")) {
                prefs.edit().remove("access_token").apply()
                runCatching { refreshAccessTokenIfNeeded(true) }
                val renewed = prefs.getString("access_token", null)
                if (!renewed.isNullOrBlank()) execute(method, path, body, csrfHeader, renewed) else throw e
            } else {
                throw e
            }
        }
    }
    private fun refreshAccessTokenIfNeeded(force: Boolean) {
        val existing = prefs.getString("access_token", null)
        val expiration = existing?.let(::tokenExpirationMillis)
        if (!force && expiration != null && expiration > System.currentTimeMillis() + 60_000L) return
        if (!hasSessionCookie()) {
            if (expiration == null || expiration <= System.currentTimeMillis()) prefs.edit().remove("access_token").apply()
            return
        }
        val csrfHeader = token(force = force)
        val response = try {
            execute("POST", "/auth/mobile-token", JSONObject(), csrfHeader, null)
        } catch (error: ApiFailure) {
            if (error.status == 403 && error.code == "CSRF_VALIDATION_FAILED") {
                execute("POST", "/auth/mobile-token", JSONObject(), token(force = true), null)
            } else {
                throw error
            }
        }
        val fresh = response.optString("accessToken").takeIf { it.isNotBlank() && it.length <= 4096 }
            ?: throw ApiFailure(0, "INVALID_ACCESS_TOKEN", "Não foi possível renovar a sessão.")
        prefs.edit().putString("access_token", fresh).apply()
    }
    private fun hasSessionCookie(): Boolean {
        val cookies = runCatching { JSONArray(prefs.getString("cookies", "[]")) }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        for (i in 0 until cookies.length()) {
            val item = cookies.optJSONObject(i) ?: continue
            if (item.optString("name") == "deadsmile.sid" &&
                item.optString("domain") == HOST &&
                item.optLong("expiresAt", 0L) > now &&
                item.optString("value").isNotBlank()
            ) return true
        }
        return false
    }
    private fun tokenExpirationMillis(token: String): Long? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        return runCatching {
            val payload = JSONObject(String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)))
            payload.optLong("exp").takeIf { it > 0 }?.times(1000L)
        }.getOrNull()
    }
    private fun execute(method: String, path: String, body: JSONObject?, token: String?, bearer: String? = null): JSONObject {
        val selectedLanguage = appContext.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE)
            .getString("app_language", "").orEmpty()
        val builder = Request.Builder().url(BASE + path).header("Accept", "application/json")
            .header("User-Agent", "DeadsmileGamesCompanion/" + BuildConfig.VERSION_NAME)
        when (selectedLanguage.lowercase(java.util.Locale.ROOT)) {
            "pt-br", "pt" -> builder.header("Accept-Language", "pt-BR")
            "en" -> builder.header("Accept-Language", "en")
            "es" -> builder.header("Accept-Language", "es")
        }
        if (token != null) builder.header("X-CSRF-Token", token)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer " + bearer)
        builder.method(method, if (body != null) body.toString().toRequestBody(JSON) else null)
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val payload = runCatching { JSONObject(raw) }.getOrElse {
                throw ApiFailure(response.code, "INVALID_RESPONSE", "A API retornou uma resposta inválida.")
            }
            if (!response.isSuccessful || !payload.optBoolean("success", false)) {
                val err = payload.optJSONObject("error")
                val code = err?.optString("code").orEmpty().ifBlank { "UNKNOWN_ERROR" }
                val message = err?.optString("message").orEmpty().ifBlank {
                    when (response.code) { 401 -> "Entre na sua conta para continuar."; 403 -> "A solicitação não foi autorizada."; 429 -> "Muitas tentativas. Aguarde."; else -> "Não foi possível concluir a solicitação." }
                }
                throw ApiFailure(response.code, code, message)
            }
            val data = payload.opt("data")
            return when (data) { is JSONObject -> data; is JSONArray -> JSONObject().put("items", data); else -> JSONObject().put("value", data) }
        }
    }
}
