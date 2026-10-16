package games.deadsmile.mobile.data

import android.net.Uri
import android.text.Html
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

private const val WEBSITE_ORIGIN = "https://deadsmilegames.vercel.app"
private const val API_ORIGIN = "https://api-ust8.onrender.com"

object MobileContentLanguage {
    @Volatile
    var selected = "pt-BR"
        private set

    fun update(language: String) {
        selected = when (language.lowercase(java.util.Locale.ROOT)) {
            "en" -> "en"
            "es" -> "es"
            else -> "pt-BR"
        }
    }
}

data class FeedItem(
    val id: String,
    val title: String,
    val summary: String,
    val image: String?,
    val body: String,
    val slug: String,
    val url: String?,
    val publishedAt: String = "",
    val author: String = "",
    val developer: String = "",
    val publisher: String = "",
    val genres: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val relatedGames: List<FeedItem> = emptyList(),
    val associatedGameIds: List<String> = emptyList(),
    val associatedGameSlugs: List<String> = emptyList(),
    val status: String = "",
    val releaseDate: String = "",
    val accessType: String = "",
    val purchaseUrl: String? = null,
    val itchUrl: String? = null,
    val microsoftStoreUrl: String? = null,
    val downloadAvailable: Boolean = false,
    val launcherAvailable: Boolean = false,
    val featured: Boolean = false,
    val platforms: List<String> = emptyList(),
    val ageRating: String = "",
    val posterImage: String? = null,
    val ratingSystem: String = "",
    val ratingDescription: String = "",
    val ratingDescriptors: List<String> = emptyList(),
    val ratingDisclaimers: List<String> = emptyList(),
    val ratingImageUrl: String? = null,
    val engine: String = "",
    val version: String = "",
    val trailerUrl: String? = null,
    val savePathTemplate: String = "",
    val cloudSavesEnabled: Boolean = false,
    val supportedLanguages: List<String> = emptyList(),
    val features: List<String> = emptyList()
)

data class UserAccount(val id: String, val username: String, val email: String, val bio: String)

data class AccountProfile(
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val websiteUrl: String = "",
    val location: String = "",
    val avatarUrl: String = ""
)

data class PrivacyOptions(
    val shareGameActivity: Boolean = false,
    val sharePlaytime: Boolean = false,
    val shareAchievements: Boolean = false
)

data class ActiveSession(
    val id: String,
    val client: String,
    val platform: String,
    val isCurrent: Boolean
)

private fun JSONObject.firstText(vararg names: String): String {
    names.forEach { name ->
        val value = opt(name)
        when (value) {
            is String -> if (value.isNotBlank() && value != "null") return value
            is Number -> return value.toString()
        }
    }
    return ""
}

fun resolveAssetUrl(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
    if (raw.startsWith("//") || raw.any { it.isISOControl() } || raw.contains('\\')) return null
    if (raw.startsWith("data:image/", ignoreCase = true)) {
        val validDataImage = Regex("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$", RegexOption.IGNORE_CASE)
        return raw.takeIf { it.length <= 500_000 && validDataImage.matches(it) }
    }
    if (raw.startsWith("https://", ignoreCase = true)) {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        return raw.takeIf { uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo.isNullOrEmpty() }
    }
    if (raw.startsWith("http://", ignoreCase = true)) return null
    val origin = if (raw.startsWith("/")) API_ORIGIN else WEBSITE_ORIGIN
    val normalized = if (raw.startsWith("/")) raw else "/" + raw.removePrefix("./")
    return origin + normalized
}

private fun displayHtml(source: String): String {
    if (source.isBlank()) return ""
    val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT).toString()
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(source).toString()
    }
    return decoded.replace('\u00A0', ' ').replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n").trim()
}

private fun htmlImageUrls(source: String): List<String> {
    val pattern = Regex("""<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    return pattern.findAll(source).mapNotNull { match ->
        resolveAssetUrl(match.groupValues.getOrNull(1))
    }.toList()
}

private fun JSONArray.strings(): List<String> = buildList {
    for (i in 0 until length()) {
        when (val entry = opt(i)) {
            is String -> if (entry.isNotBlank()) add(entry)
            is JSONObject -> entry.firstText("name", "title", "label", "slug", "url").takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun itemFromJson(o: JSONObject, fallbackId: String, includeRelated: Boolean): FeedItem {
    val title = o.firstText("title", "name", "gameTitle", "game_title").ifBlank { "Sem título" }
    val storeInfo = o.optJSONObject("microsoftStore") ?: o.optJSONObject("microsoftStoreBadge")
    val primaryRating = o.optJSONObject("primaryRating") ?: storeInfo?.optJSONObject("primaryRating")
    val storeImages = storeInfo?.optJSONArray("images") ?: JSONArray()
    val posterImage = buildList {
        for (index in 0 until storeImages.length()) {
            val candidate = storeImages.optJSONObject(index) ?: continue
            val purpose = candidate.firstText("purpose", "imageType", "image_type")
            if (!Regex("(?i)poster|boxart|cover").containsMatchIn(purpose)) continue
            val url = candidate.firstText("url", "imageUrl", "image_url")
            val resolved = resolveAssetUrl(url) ?: continue
            add(Triple(resolved, candidate.optInt("height", 0), candidate.optInt("width", 0)))
        }
    }.sortedWith(compareBy<Triple<String, Int, Int>>({ if (it.second > it.third) 0 else 1 }, { -it.second }))
        .firstOrNull()?.first
    val slug = o.firstText("slug", "newsSlug", "gameSlug")
    val id = o.firstText("id", "uuid", "gameId", "game_id").ifBlank { slug.ifBlank { fallbackId } }
    val localizedThumbnailKeys = when (MobileContentLanguage.selected) {
        "en" -> arrayOf("thumbnail_en", "thumbnailEn")
        "es" -> arrayOf("thumbnail_es", "thumbnailEs")
        else -> arrayOf("thumbnail_pt_br", "thumbnailPtBr")
    }
    val imageKeys = localizedThumbnailKeys + arrayOf(
        "coverImage", "cover_image", "image", "thumbnail", "thumbnail_url", "thumbnailUrl",
        "heroImage", "hero_image", "bannerImage", "banner_image", "poster", "cover", "icon"
    )
    val image = resolveAssetUrl(o.firstText(*imageKeys)) ?: if (title.contains("abby", ignoreCase = true) && title.contains("restless", ignoreCase = true)) {
        "https://img.itch.zone/aW1nLzMwMzI2OTk4LnBuZw==/original/yJfbvV.png"
    } else null
    val rawBody = o.firstText(
        "body", "content", "contentHtml", "content_html", "html", "articleBody",
        "article_body", "fullDescription", "full_description", "description", "excerpt"
    )
    val summary = displayHtml(o.firstText(
        "excerpt", "summary", "shortDescription", "short_description", "subtitle", "tagline",
        "caption", "synopsis", "videoDescription", "video_description", "description"
    )).ifBlank {
        val localized = o.optJSONObject("translations")?.let { translations ->
            (translations.optJSONObject("pt-BR") ?: translations.optJSONObject("pt") ?: translations.optJSONObject("en"))
                ?.firstText("excerpt", "summary", "description")
        }.orEmpty()
        displayHtml(localized).ifBlank { displayHtml(rawBody).take(220) }
    }
    val url = o.firstText("video_url", "videoUrl", "externalUrl", "external_url", "url")
        .takeIf { it.startsWith("https://", ignoreCase = true) }
    val purchaseUrl = o.firstText("purchaseUrl", "purchase_url").takeIf { it.startsWith("https://", ignoreCase = true) }
    val itchUrl = o.firstText("itchUrl", "itch_url").takeIf { it.startsWith("https://", ignoreCase = true) }
    val microsoftStoreUrl = (o.optJSONObject("microsoftStore") ?: o.optJSONObject("microsoftStoreBadge"))?.let { store ->
        store.firstText("storeUrl", "productUrl", "url", "href").takeIf { it.startsWith("https://", ignoreCase = true) }
    }
    val genres = (o.optJSONArray("genres") ?: o.optJSONArray("tags") ?: JSONArray()).strings()
    val screenArray = o.optJSONArray("screenshots") ?: o.optJSONArray("gallery") ?: JSONArray()
    val screenshots = (buildList {
        for (i in 0 until screenArray.length()) {
            val entry = screenArray.opt(i)
            val raw = when (entry) {
                is String -> entry
                is JSONObject -> entry.firstText("url", "src", "image", "imageUrl", "image_url")
                else -> ""
            }
            resolveAssetUrl(raw)?.let(::add)
        }
    } + htmlImageUrls(rawBody)).distinct()
    val relatedArrays = if (includeRelated) listOfNotNull(
        o.optJSONArray("relatedGames"), o.optJSONArray("related_games"),
        o.optJSONArray("associatedGames"), o.optJSONArray("associated_games"),
        o.optJSONArray("related"), o.optJSONArray("recommendations"), o.optJSONArray("games")
    ) else emptyList()
    val directRelatedObjects = if (includeRelated) listOfNotNull(
        o.optJSONObject("relatedGame"), o.optJSONObject("related_game"),
        o.optJSONObject("associatedGame"), o.optJSONObject("associated_game"),
        o.optJSONObject("linkedGame"), o.optJSONObject("linked_game"),
        o.optJSONObject("game")
    ) else emptyList()
    val relatedObjects = buildList {
        relatedArrays.forEach { array ->
            for (i in 0 until array.length()) array.optJSONObject(i)?.let(::add)
        }
        directRelatedObjects.forEach(::add)
    }.distinctBy { it.firstText("id", "uuid", "slug", "gameId", "game_id").ifBlank { it.firstText("title", "name") } }
    val related = relatedObjects.mapNotNull { entry ->
        if (entry.firstText("title", "name", "gameTitle", "game_title").isBlank()) null
        else itemFromJson(entry, "related-game", false)
    }
    val associatedIds = buildSet {
        listOf("gameId", "game_id", "associatedGameId", "associated_game_id", "relatedGameId", "related_game_id")
            .map { o.firstText(it) }.filter { it.isNotBlank() }.forEach(::add)
        listOf("gameIds", "game_ids", "associatedGameIds", "associated_game_ids", "relatedGameIds", "related_game_ids").forEach { key ->
            o.optJSONArray(key)?.let { array ->
                for (i in 0 until array.length()) {
                    when (val entry = array.opt(i)) {
                        is String -> entry.takeIf { it.isNotBlank() }?.let(::add)
                        is JSONObject -> entry.firstText("id", "uuid", "gameId", "game_id").takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        }
        relatedObjects.forEach { entry -> entry.firstText("id", "uuid", "gameId", "game_id").takeIf { it.isNotBlank() }?.let(::add) }
        relatedArrays.forEach { array ->
            for (i in 0 until array.length()) {
                when (val entry = array.opt(i)) {
                    is String -> entry.takeIf { it.isNotBlank() }?.let(::add)
                    is JSONObject -> entry.firstText("id", "uuid", "gameId", "game_id").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }.toList()
    val associatedSlugs = buildSet {
        listOf("gameSlug", "game_slug", "associatedGameSlug", "associated_game_slug", "relatedGameSlug", "related_game_slug")
            .map { o.firstText(it) }.filter { it.isNotBlank() }.forEach(::add)
        relatedObjects.forEach { entry -> entry.firstText("slug", "gameSlug", "game_slug").takeIf { it.isNotBlank() }?.let(::add) }
        relatedArrays.forEach { array ->
            for (i in 0 until array.length()) {
                when (val entry = array.opt(i)) {
                    is String -> entry.takeIf { it.isNotBlank() }?.let(::add)
                    is JSONObject -> entry.firstText("slug", "gameSlug", "game_slug").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }.toList()
    return FeedItem(
        id = id,
        title = title,
        summary = summary,
        image = image,
        body = displayHtml(rawBody),
        slug = slug,
        url = url,
        publishedAt = o.firstText("publishedAt", "published_at", "createdAt", "created_at", "date"),
        author = o.firstText("authorName", "author_name", "author", "byline"),
        developer = o.firstText("developer", "studio", "createdBy"),
        publisher = o.firstText("publisher", "distributor"),
        genres = genres,
        screenshots = screenshots,
        relatedGames = related,
        associatedGameIds = associatedIds,
        associatedGameSlugs = associatedSlugs,
        status = o.firstText("status", "gameStatus", "game_status").lowercase(),
        releaseDate = o.firstText("releaseDate", "release_date"),
        accessType = o.firstText("accessType", "access_type"),
        purchaseUrl = purchaseUrl,
        itchUrl = itchUrl,
        microsoftStoreUrl = microsoftStoreUrl,
        downloadAvailable = o.optBoolean("downloadAvailable", false),
        launcherAvailable = o.optBoolean("launcherAvailable", o.optBoolean("downloadAvailable", false)),
        featured = o.optBoolean("featured", false),
        platforms = (o.optJSONArray("platforms") ?: JSONArray()).strings(),
        ageRating = o.firstText("ageRating", "age_rating", "contentRating", "content_rating", "minimumAge", "minimum_age")
            .ifBlank { storeInfo?.firstText("ageRating", "age_rating", "contentRating", "content_rating", "minimumAge", "minimum_age").orEmpty() }
            .ifBlank {
                primaryRating?.optInt("age", -1)?.takeIf { it >= 0 }?.let { "$it+" }
                    ?: primaryRating?.firstText("label").orEmpty()
            },
        posterImage = posterImage,
        ratingSystem = o.firstText("ratingSystem", "rating_system").ifBlank { primaryRating?.firstText("system").orEmpty() },
        ratingDescription = o.firstText("ratingDescription", "rating_description").ifBlank { primaryRating?.firstText("description").orEmpty() },
        ratingDescriptors = (o.optJSONArray("ratingDescriptors") ?: primaryRating?.optJSONArray("descriptors") ?: JSONArray()).strings(),
        ratingDisclaimers = (o.optJSONArray("ratingDisclaimers") ?: primaryRating?.optJSONArray("disclaimers") ?: JSONArray()).strings(),
        ratingImageUrl = primaryRating?.firstText("imageUrl", "image_url").orEmpty().takeIf { it.startsWith("https://", ignoreCase = true) },
        engine = o.firstText("engine", "gameEngine", "game_engine"),
        version = o.firstText("version", "gameVersion", "game_version"),
        trailerUrl = o.firstText("trailerUrl", "trailer_url", "trailer", "backgroundVideo", "background_video")
            .takeIf { it.startsWith("https://", ignoreCase = true) },
        savePathTemplate = o.firstText("savePathTemplate", "save_path_template"),
        cloudSavesEnabled = o.optBoolean("cloudSavesEnabled", o.optBoolean("cloud_saves_enabled", false)),
        supportedLanguages = (o.optJSONArray("supportedLanguages") ?: o.optJSONArray("supported_languages") ?: o.optJSONArray("languages") ?: JSONArray()).strings(),
        features = (o.optJSONArray("features") ?: o.optJSONArray("gameModes") ?: o.optJSONArray("game_modes") ?: JSONArray()).strings()
    )
}

fun JSONObject.feed(vararg keys: String): List<FeedItem> {
    val arr = when {
        opt("value") is JSONArray -> optJSONArray("value")
        keys.any { opt(it) is JSONArray } -> keys.firstNotNullOfOrNull { optJSONArray(it) }
        opt("items") is JSONArray -> optJSONArray("items")
        opt("results") is JSONArray -> optJSONArray("results")
        opt("games") is JSONArray -> optJSONArray("games")
        opt("news") is JSONArray -> optJSONArray("news")
        else -> null
    } ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(itemFromJson(o, "$i", true))
        }
    }
}

fun JSONObject.detailItem(fallback: FeedItem, preferGame: Boolean? = null): FeedItem {
    val detail = when (preferGame) {
        true -> optJSONObject("game") ?: optJSONObject("item") ?: this
        false -> optJSONObject("article") ?: optJSONObject("news") ?: optJSONObject("item") ?: this
        null -> optJSONObject("game") ?: optJSONObject("article") ?: optJSONObject("news") ?: optJSONObject("item") ?: this
    }
    val parsed = itemFromJson(detail, fallback.id, true)
    val root = if (detail !== this) itemFromJson(this, fallback.id, true) else null
    return parsed.copy(
        id = parsed.id.ifBlank { fallback.id },
        title = parsed.title.ifBlank { fallback.title },
        slug = parsed.slug.ifBlank { fallback.slug },
        image = parsed.image ?: fallback.image,
        summary = parsed.summary.ifBlank { fallback.summary },
        body = parsed.body.ifBlank { fallback.body },
        url = parsed.url ?: fallback.url,
        developer = parsed.developer.ifBlank { fallback.developer },
        publisher = parsed.publisher.ifBlank { fallback.publisher },
        publishedAt = parsed.publishedAt.ifBlank { fallback.publishedAt },
        genres = parsed.genres.ifEmpty { fallback.genres },
        screenshots = parsed.screenshots.ifEmpty { fallback.screenshots },
        relatedGames = (parsed.relatedGames + root?.relatedGames.orEmpty() + fallback.relatedGames).distinctBy { it.id },
        associatedGameIds = (parsed.associatedGameIds + root?.associatedGameIds.orEmpty() + fallback.associatedGameIds).distinct(),
        associatedGameSlugs = (parsed.associatedGameSlugs + root?.associatedGameSlugs.orEmpty() + fallback.associatedGameSlugs).distinct(),
        status = parsed.status.ifBlank { fallback.status },
        releaseDate = parsed.releaseDate.ifBlank { fallback.releaseDate },
        accessType = parsed.accessType.ifBlank { fallback.accessType },
        purchaseUrl = parsed.purchaseUrl ?: fallback.purchaseUrl,
        itchUrl = parsed.itchUrl ?: fallback.itchUrl,
        microsoftStoreUrl = parsed.microsoftStoreUrl ?: fallback.microsoftStoreUrl,
        downloadAvailable = if (detail.has("downloadAvailable")) parsed.downloadAvailable else fallback.downloadAvailable,
        launcherAvailable = if (detail.has("launcherAvailable")) parsed.launcherAvailable else fallback.launcherAvailable,
        featured = if (detail.has("featured")) parsed.featured else fallback.featured,
        platforms = parsed.platforms.ifEmpty { fallback.platforms },
        ageRating = parsed.ageRating.ifBlank { fallback.ageRating },
        posterImage = parsed.posterImage ?: fallback.posterImage,
        ratingSystem = parsed.ratingSystem.ifBlank { fallback.ratingSystem },
        ratingDescription = parsed.ratingDescription.ifBlank { fallback.ratingDescription },
        ratingDescriptors = parsed.ratingDescriptors.ifEmpty { fallback.ratingDescriptors },
        ratingDisclaimers = parsed.ratingDisclaimers.ifEmpty { fallback.ratingDisclaimers },
        ratingImageUrl = parsed.ratingImageUrl ?: fallback.ratingImageUrl,
        engine = parsed.engine.ifBlank { fallback.engine },
        version = parsed.version.ifBlank { fallback.version },
        trailerUrl = parsed.trailerUrl ?: fallback.trailerUrl,
        savePathTemplate = parsed.savePathTemplate.ifBlank { fallback.savePathTemplate },
        cloudSavesEnabled = if (detail.has("cloudSavesEnabled") || detail.has("cloud_saves_enabled")) parsed.cloudSavesEnabled else fallback.cloudSavesEnabled,
        supportedLanguages = parsed.supportedLanguages.ifEmpty { fallback.supportedLanguages },
        features = parsed.features.ifEmpty { fallback.features }
    )
}

fun JSONObject.userAccount(): UserAccount? {
    val id = optString("id")
    val username = optString("username")
    if (id.isBlank() || username.isBlank()) return null
    return UserAccount(id, username, optString("email"), optString("bio"))
}

fun JSONObject.accountProfile(fallback: AccountProfile = AccountProfile()): AccountProfile = AccountProfile(
    fullName = firstText("fullName", "full_name").ifBlank { fallback.fullName },
    username = firstText("username").ifBlank { fallback.username },
    email = firstText("email").ifBlank { fallback.email },
    bio = firstText("bio").ifBlank { fallback.bio },
    websiteUrl = firstText("websiteUrl", "website_url").ifBlank { fallback.websiteUrl },
    location = firstText("location").ifBlank { fallback.location },
    avatarUrl = resolveAssetUrl(firstText("avatarUrl", "avatar_url")).orEmpty().ifBlank { fallback.avatarUrl }
)

fun JSONObject.privacyOptions(): PrivacyOptions = PrivacyOptions(
    shareGameActivity = optBoolean("shareGameActivity", false),
    sharePlaytime = optBoolean("sharePlaytime", false),
    shareAchievements = optBoolean("shareAchievements", false)
)

fun JSONObject.activeSessions(): List<ActiveSession> {
    val sessions = optJSONArray("sessions") ?: optJSONArray("items") ?: JSONArray()
    return buildList {
        for (i in 0 until sessions.length()) {
            val entry = sessions.optJSONObject(i) ?: continue
            val id = entry.firstText("id", "deviceId")
            if (id.isBlank()) continue
            add(ActiveSession(
                id = id,
                client = entry.firstText("client"),
                platform = entry.firstText("platform"),
                isCurrent = entry.optBoolean("isCurrent", false)
            ))
        }
    }
}
