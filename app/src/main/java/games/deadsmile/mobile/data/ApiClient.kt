package games.deadsmile.mobile.data

import android.content.Context
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
    fun clearSession() { prefs.edit().remove("cookies").apply(); csrf = null }

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
        return try { execute(method, path, body, csrfHeader) }
        catch (e: ApiFailure) {
            if (e.status == 403 && e.code == "CSRF_VALIDATION_FAILED" && csrfHeader != null)
                execute(method, path, body, token(force = true))
            else throw e
        }
    }
    private fun execute(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        val selectedLanguage = appContext.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE)
            .getString("app_language", "").orEmpty()
        val builder = Request.Builder().url(BASE + path).header("Accept", "application/json")
            .header("User-Agent", "DeadsmileGamesCompanion/1.1.0")
        when (selectedLanguage.lowercase(java.util.Locale.ROOT)) {
            "pt-br", "pt" -> builder.header("Accept-Language", "pt-BR")
            "en" -> builder.header("Accept-Language", "en")
            "es" -> builder.header("Accept-Language", "es")
        }
        if (token != null) builder.header("X-CSRF-Token", token)
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
