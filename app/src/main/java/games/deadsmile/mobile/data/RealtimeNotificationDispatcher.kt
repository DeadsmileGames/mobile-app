package games.deadsmile.mobile.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import games.deadsmile.mobile.AppI18n
import games.deadsmile.mobile.MainActivity
import games.deadsmile.mobile.R
import org.json.JSONArray
import org.json.JSONObject

object RealtimeNotificationDispatcher {
    private const val CHANNEL_ID = "deadsmile_content_updates"
    private const val PREFS = "deadsmile-ui"

    fun dispatch(context: Context, event: JSONObject) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean("notifications_enabled", false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val type = event.optString("event_type").ifBlank { event.optString("type") }.lowercase()
        val streamKey = when {
            type.startsWith("game.") -> "games"
            type.startsWith("news.") -> "news"
            type.startsWith("video.") -> "videos"
            type.startsWith("wishlist.") || type.startsWith("achievement.") || type.startsWith("account.") -> "platform"
            else -> return
        }
        val prefKey = when (streamKey) {
            "games" -> "notify_games"
            "news" -> "notify_news"
            "videos" -> "notify_videos"
            else -> "notify_platform"
        }
        if (!preferences.getBoolean(prefKey, true)) return

        val payload = event.optJSONObject("payload") ?: event.optJSONObject("data")
            ?: runCatching { JSONObject(event.optString("payload")) }.getOrNull() ?: JSONObject()
        val entityId = event.optString("entity_id").ifBlank {
            payload.optString("id").ifBlank { payload.optString("gameId") }
        }
        val eventId = event.optString("id").ifBlank { "$type:$entityId:${event.optString("created_at")}" }
        if (eventId.isBlank()) return
        val seen = runCatching {
            val array = JSONArray(preferences.getString("realtime_notified_ids", "[]"))
            buildSet { for (idx in 0 until array.length()) array.optString(idx).takeIf { it.isNotBlank() }?.let(::add) }
        }.getOrDefault(emptySet())
        if (eventId in seen) return

        val label = when {
            type.startsWith("game.") -> when (type) {
                "game.published" -> "Novo jogo publicado"
                "game.released" -> "Lançamento de jogo"
                "game.updated", "game.version.updated" -> "Jogo atualizado"
                else -> "Atualização do catálogo de jogos"
            }
            type.startsWith("news.") -> "Notícia nova na Deadsmile"
            type.startsWith("video.") -> "Novo vídeo"
            else -> "Atualização da conta"
        }
        val title = listOf(payload.optString("title"), payload.optString("game_title"), payload.optString("gameTitle"), event.optString("title"))
            .firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
        val detail = listOf(payload.optString("excerpt"), payload.optString("summary"), payload.optString("message"),
            payload.optString("description"), event.optString("message"))
            .firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
        val message = listOf(title, detail).filter { it.isNotBlank() }.joinToString("\n").ifBlank { title.ifBlank { label } }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, AppI18n.translate(context, "Novidades do catálogo"), NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = AppI18n.translate(context, "Novos jogos, notícias e vídeos.") }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        LocalNotificationStore.add(context, label, message)
        val requestCode = eventId.hashCode()
        val pending = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val localizedTitle = AppI18n.translate(context, label)
        val localizedMessage = AppI18n.translate(context, message)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_deadsmile)
            .setContentTitle(localizedTitle)
            .setContentText(localizedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedMessage))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(context).notify(requestCode, notification) }

        preferences.edit().putString("realtime_notified_ids", JSONArray((seen + eventId).toList().takeLast(160)).toString()).apply()
        if (entityId.isNotBlank() && streamKey in setOf("games", "news", "videos")) {
            val key = "notification_seen_ids_$streamKey"
            val existing = runCatching {
                val array = JSONArray(preferences.getString(key, "[]"))
                buildList { for (idx in 0 until array.length()) array.optString(idx).takeIf { it.isNotBlank() }?.let(::add) }
            }.getOrDefault(emptyList())
            preferences.edit().putString(key, JSONArray((listOf(entityId) + existing).distinct().take(144)).toString()).apply()
        }
    }
}
