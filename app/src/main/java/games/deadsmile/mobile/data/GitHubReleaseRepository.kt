package games.deadsmile.mobile.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val id: Long,
    val tagName: String,
    val version: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long
)

object GitHubReleaseRepository {
    private const val OWNER = "DeadsmileGames"
    private const val REPOSITORY = "mobile-app"
    private const val RELEASES_API = "https://api.github.com/repos/$OWNER/$REPOSITORY/releases"
    private const val MAX_APK_BYTES = 350L * 1024L * 1024L
    private const val USER_AGENT = "DeadsmileGamesCompanion-Android"

    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            require(request.url.isHttps && request.url.host in setOf(
                "github.com",
                "release-assets.githubusercontent.com",
                "objects.githubusercontent.com",
                "github-releases.githubusercontent.com"
            )) { "O download foi redirecionado para um host não autorizado." }
            chain.proceed(request)
        }
        .build()

    suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val found = ArrayList<GitHubRelease>()
        for (page in 1..20) {
            val request = Request.Builder()
                .url("$RELEASES_API?per_page=100&page=$page")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val pageItems = apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("O GitHub retornou HTTP ${response.code} ao consultar releases.")
                val bytes = response.body?.string().orEmpty()
                val array = JSONArray(bytes)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val tag = item.optString("tag_name").trim()
                        val version = normalizeVersion(tag) ?: continue
                        val assets = item.optJSONArray("assets") ?: JSONArray()
                        var apkName: String? = null
                        var apkUrl: String? = null
                        var apkSize = 0L
                        for (assetIndex in 0 until assets.length()) {
                            val asset = assets.optJSONObject(assetIndex) ?: continue
                            val name = asset.optString("name")
                            val url = asset.optString("browser_download_url")
                            if (!name.endsWith(".apk", ignoreCase = true) || !isTrustedApkUrl(url)) continue
                            val nameVersion = Regex("""(?i)(?:companion|mobile)[-_ ]v?(\d+(?:\.\d+){1,3})""")
                                .find(name)?.groupValues?.getOrNull(1)
                            if (nameVersion != null && compareVersions(nameVersion, version) != 0) continue
                            apkName = name
                            apkUrl = url
                            apkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                            break
                        }
                        add(
                            GitHubRelease(
                                id = item.optLong("id"),
                                tagName = tag,
                                version = version,
                                name = item.optString("name").ifBlank { "Deadsmile Games Companion $version" },
                                body = item.optString("body"),
                                publishedAt = item.optString("published_at"),
                                htmlUrl = item.optString("html_url").takeIf(::isHttpsGithubUrl).orEmpty(),
                                prerelease = item.optBoolean("prerelease", false),
                                draft = item.optBoolean("draft", false),
                                apkName = apkName,
                                apkUrl = apkUrl,
                                apkSize = apkSize
                            )
                        )
                    }
                }
            }
            found += pageItems
            if (pageItems.size < 100) break
        }
        found.distinctBy { it.id }.sortedWith { a, b -> compareVersions(b.version, a.version) }
    }

    fun latestNewerRelease(releases: List<GitHubRelease>, installedVersion: String): GitHubRelease? =
        releases.asSequence()
            .filter { !it.draft && !it.prerelease && it.apkUrl != null }
            .filter { compareVersions(it.version, installedVersion) > 0 }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }

    internal fun normalizeVersion(value: String): String? {
        val normalized = value.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val core = normalized.substringBefore('-')
        if (!core.matches(Regex("""\d+\.\d+\.\d+"""))) return null
        return core.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }?.joinToString(".")
    }

    internal fun compareVersions(first: String, second: String): Int {
        val a = normalizeVersion(first) ?: return -1
        val b = normalizeVersion(second) ?: return 1
        val av = a.split('.').map { it.toInt() }
        val bv = b.split('.').map { it.toInt() }
        for (index in 0 until maxOf(av.size, bv.size)) {
            val left = av.getOrElse(index) { 0 }
            val right = bv.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        // Stable release wins over the same numeric prerelease tag.
        val aStable = first.substringAfter('-', "").isEmpty()
        val bStable = second.substringAfter('-', "").isEmpty()
        return aStable.compareTo(bStable)
    }

    internal fun isTrustedApkUrl(value: String): Boolean {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        val path = uri.path.orEmpty()
        return uri.scheme.equals("https", true) &&
            uri.host.equals("github.com", true) &&
            uri.rawUserInfo.isNullOrEmpty() &&
            uri.port == -1 &&
            uri.rawQuery.isNullOrEmpty() &&
            uri.rawFragment.isNullOrEmpty() &&
            path.startsWith("/$OWNER/$REPOSITORY/releases/download/") &&
            path.endsWith(".apk", ignoreCase = true)
    }

    private fun isHttpsGithubUrl(value: String): Boolean {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) && uri.host.equals("github.com", true) &&
            uri.rawUserInfo.isNullOrEmpty() && uri.port == -1
    }

    fun cachedFile(context: Context, release: GitHubRelease): File? {
        val file = updateFile(context, release.version)
        return file.takeIf { it.isFile && it.length() in 1..MAX_APK_BYTES && isCompatibleApk(context, it, release.version) }
    }

    suspend fun download(context: Context, release: GitHubRelease, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val url = release.apkUrl ?: throw IOException("Esta release não contém um APK.")
            if (!isTrustedApkUrl(url)) throw IOException("O link do APK não pertence às releases oficiais do Companion.")
            if (release.apkSize > MAX_APK_BYTES) throw IOException("O APK ultrapassa o tamanho máximo permitido.")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val target = updateFile(context, release.version)
            val temp = File(target.parentFile, target.name + ".part")
            target.parentFile?.mkdirs()
            temp.delete()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("O download falhou (HTTP ${response.code}).")
                if (!response.request.url.isHttps) throw IOException("O download não permaneceu em HTTPS.")
                val body = response.body ?: throw IOException("O GitHub não retornou conteúdo para o APK.")
                val expectedBytes = body.contentLength()
                if (expectedBytes > MAX_APK_BYTES) throw IOException("O APK ultrapassa o tamanho máximo permitido.")
                var received = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > MAX_APK_BYTES) throw IOException("O APK ultrapassa o tamanho máximo permitido.")
                            output.write(buffer, 0, count)
                            val percent = if (expectedBytes > 0L) ((received * 100L) / expectedBytes).toInt().coerceIn(0, 100) else 0
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (!temp.isFile || temp.length() == 0L) throw IOException("O arquivo de atualização ficou vazio.")
            if (!isCompatibleApk(context, temp, release.version)) {
                temp.delete()
                throw IOException("O APK não corresponde ao app instalado ou foi assinado com outra chave. A atualização foi bloqueada.")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IOException("Não foi possível guardar o arquivo de atualização.")
            target
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, file: File) {
        if (!isCompatibleApk(context, file, null)) throw IOException("O APK não passou na verificação de pacote e assinatura.")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun updateFile(context: Context, version: String): File {
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.filesDir, "updates").apply { mkdirs() }, "companion-$safeVersion.apk")
    }

    private fun isCompatibleApk(context: Context, file: File, expectedVersion: String?): Boolean {
        if (!file.isFile || file.length() !in 1..MAX_APK_BYTES) return false
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = runCatching { manager.getPackageArchiveInfo(file.absolutePath, flags) }.getOrNull() ?: return false
        if (archive.packageName != context.packageName) return false
        if (expectedVersion != null && normalizeVersion(archive.versionName.orEmpty()) != normalizeVersion(expectedVersion)) return false
        val installed = runCatching { manager.getPackageInfo(context.packageName, flags) }.getOrNull() ?: return false
        val archiveCode = packageVersionCode(archive)
        val installedCode = packageVersionCode(installed)
        if (archiveCode <= installedCode) return false
        val archiveSigners = signerFingerprints(archive)
        val installedSigners = signerFingerprints(installed)
        return archiveSigners.isNotEmpty() && archiveSigners == installedSigners
    }

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun signerFingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP)
        }.toSet()
    }
}
