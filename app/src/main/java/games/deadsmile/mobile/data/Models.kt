package games.deadsmile.mobile.data

import android.net.Uri
import android.text.Html
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

private const val WEBSITE_ORIGIN = "https://deadsmilegames.vercel.app"
private const val API_ORIGIN = "https://api-ust8.onrender.com"

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
    val relatedGames: List<FeedItem> = emptyList()
)

data class UserAccount(val id: String, val username: String, val email: String, val bio: String)

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

/** Resolves images the same way as the desktop launcher, and accepts API-served relative paths too. */
fun resolveAssetUrl(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
    if (raw.startsWith("//") || raw.any { it.isISOControl() } || raw.contains('\\')) return null
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
    val slug = o.firstText("slug", "newsSlug", "gameSlug")
    val id = o.firstText("id", "uuid").ifBlank { slug.ifBlank { fallbackId } }
    val image = resolveAssetUrl(o.firstText(
        "coverImage", "cover_image", "image", "thumbnail", "thumbnail_url", "thumbnailUrl",
        "heroImage", "hero_image", "bannerImage", "banner_image", "poster", "cover", "icon"
    ))
    val rawBody = o.firstText(
        "body", "content", "contentHtml", "content_html", "html", "articleBody",
        "article_body", "fullDescription", "full_description", "description", "excerpt"
    )
    val summary = displayHtml(o.firstText(
        "excerpt", "summary", "shortDescription", "short_description", "subtitle", "tagline", "description"
    )).ifBlank { displayHtml(rawBody).take(220) }
    val url = o.firstText("video_url", "videoUrl", "externalUrl", "external_url", "url")
        .takeIf { it.startsWith("https://", ignoreCase = true) }
    val genres = (o.optJSONArray("genres") ?: o.optJSONArray("tags") ?: JSONArray()).strings()
    val screenArray = o.optJSONArray("screenshots") ?: o.optJSONArray("gallery") ?: JSONArray()
    val screenshots = buildList {
        for (i in 0 until screenArray.length()) {
            val entry = screenArray.opt(i)
            val raw = when (entry) {
                is String -> entry
                is JSONObject -> entry.firstText("url", "src", "image", "imageUrl", "image_url")
                else -> ""
            }
            resolveAssetUrl(raw)?.let(::add)
        }
    }.distinct()
    val relatedArray = if (includeRelated) {
        o.optJSONArray("relatedGames") ?: o.optJSONArray("related_games") ?: o.optJSONArray("related") ?: o.optJSONArray("recommendations")
    } else null
    val related = buildList {
        if (relatedArray != null) for (i in 0 until relatedArray.length()) {
            val entry = relatedArray.optJSONObject(i) ?: continue
            add(itemFromJson(entry, "related-" + i, false))
        }
    }
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
        relatedGames = related
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

fun JSONObject.detailItem(fallback: FeedItem): FeedItem {
    val detail = optJSONObject("game") ?: optJSONObject("article") ?: optJSONObject("news") ?: optJSONObject("item") ?: this
    val parsed = itemFromJson(detail, fallback.id, true)
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
        relatedGames = parsed.relatedGames.ifEmpty { fallback.relatedGames }
    )
}

fun JSONObject.userAccount(): UserAccount? {
    val id = optString("id")
    val username = optString("username")
    if (id.isBlank() || username.isBlank()) return null
    return UserAccount(id, username, optString("email"), optString("bio"))
}
