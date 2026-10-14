package games.deadsmile.mobile.data

import org.json.JSONArray
import org.json.JSONObject

data class FeedItem(
    val id: String,
    val title: String,
    val summary: String,
    val image: String?,
    val body: String,
    val slug: String,
    val url: String?
)

data class UserAccount(val id: String, val username: String, val email: String, val bio: String)

fun JSONObject.feed(vararg keys: String): List<FeedItem> {
    val arr = when {
        opt("value") is JSONArray -> optJSONArray("value")
        keys.any { opt(it) is JSONArray } -> keys.firstNotNullOfOrNull { optJSONArray(it) }
        opt("items") is JSONArray -> optJSONArray("items")
        opt("results") is JSONArray -> optJSONArray("results")
        else -> null
    } ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            fun text(vararg names: String): String {
                for (n in names) {
                    val v = o.opt(n)
                    if (v is String && v.isNotBlank() && v != "null") return v
                    if (v is Number) return v.toString()
                }
                return ""
            }
            val image = text("image", "cover_image", "coverImage", "thumbnail", "thumbnail_url", "heroImage")
                .takeIf { it.startsWith("https://", true) }
            val url = text("video_url", "videoUrl", "url", "externalUrl").takeIf { it.startsWith("https://", true) }
            add(FeedItem(text("id", "slug").ifBlank { "$i" }, text("title", "name", "gameTitle").ifBlank { "Sem título" },
                text("excerpt", "summary", "category", "game_title", "description"), image,
                text("body", "content", "description", "excerpt"), text("slug"), url))
        }
    }
}

fun JSONObject.userAccount(): UserAccount? {
    val id = optString("id")
    val username = optString("username")
    if (id.isBlank() || username.isBlank()) return null
    return UserAccount(id, username, optString("email"), optString("bio"))
}
