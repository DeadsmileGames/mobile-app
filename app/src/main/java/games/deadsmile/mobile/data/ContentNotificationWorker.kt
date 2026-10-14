package games.deadsmile.mobile.data

import android.Manifest
import android.app.NotificationChannel
import android.content.SharedPreferences
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import games.deadsmile.mobile.MainActivity
import games.deadsmile.mobile.R
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Checks the app's public feeds periodically; Android controls exact run timing to preserve battery. */
class ContentNotificationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val preferences = context.getSharedPreferences("deadsmile-ui", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("notifications_enabled", false)) return@withContext Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@withContext Result.success()

        createChannel(context)
        val api = ApiClient(context)
        pollPlatformEvents(context, api, preferences)
        val streams = buildList {
            if (preferences.getBoolean("notify_news", true)) {
                add(Stream("news", "Notícia nova na Deadsmile") { api.get("/newswire?limit=24&offset=0").feed("items", "news") })
            }
            if (preferences.getBoolean("notify_videos", true)) {
                add(Stream("videos", "Novo vídeo") { api.get("/videos?limit=24&offset=0").feed("items", "videos") })
            }
            if (preferences.getBoolean("notify_games", true)) {
                add(Stream("games", "Novo jogo no catálogo") { api.get("/games?page=1&limit=48").feed("items", "games") })
            }
        }
        var succeeded = 0
        var failures = 0
        for (stream in streams) {
            val items = runCatching { stream.load().distinctBy { it.id } }
                .onFailure { failures++ }.getOrNull().orEmpty()
            if (items.isEmpty()) continue
            succeeded++
            val key = "notification_last_" + stream.key
            val latestId = items.first().id
            val previous = preferences.getString(key, null)
            // Save a baseline the first time, instead of notifying about old content.
            preferences.edit().putString(key, latestId).apply()
            if (previous == null || previous == latestId) continue

            // Notify about several new entries when the app has been asleep for a while.
            val newer = items.takeWhile { it.id != previous }.take(3)
                .ifEmpty { listOf(items.first()) }
            newer.forEach { entry ->
                val summary = entry.summary.takeIf { it.isNotBlank() } ?: entry.title
                sendNotification(context, (stream.key + entry.id).hashCode(), stream.title, summary)
            }
        }
        if (succeeded == 0 && failures > 0) Result.retry() else Result.success()
    }


    /**
     * Mirrors the event cursor used by the Windows launcher. A new install establishes
     * a baseline first; subsequent runs notify about meaningful platform events.
     */
    private fun pollPlatformEvents(context: Context, api: ApiClient, preferences: SharedPreferences) {
        val establishBaseline = !preferences.getBoolean("notification_event_initialized", false)
        var cursor = preferences.getLong("notification_event_cursor", 0L)
        val newEvents = mutableListOf<JSONObject>()
        var caughtUp = false
        try {
            for (page in 0 until 10) {
                val data = api.get("/platform/events?after=" + cursor + "&limit=100")
                val items = data.optJSONArray("items") ?: break
                val before = cursor
                for (i in 0 until items.length()) {
                    val event = items.optJSONObject(i) ?: continue
                    val id = event.optLong("id", -1L)
                    if (id <= cursor) continue
                    cursor = id
                    newEvents.add(JSONObject(event.toString()))
                }
                if (items.length() < 100 || cursor == before) {
                    caughtUp = true
                    break
                }
            }
            preferences.edit().putLong("notification_event_cursor", cursor).apply()
            if (caughtUp) preferences.edit().putBoolean("notification_event_initialized", true).apply()
            // Do not notify about historical events while the first baseline catches up.
            if (establishBaseline || !caughtUp) return
            newEvents.forEach { event ->
                val type = event.optString("event_type").ifBlank { event.optString("type") }
                val prefKey = when {
                    type.startsWith("game.", ignoreCase = true) -> "notify_games"
                    type.startsWith("news.", ignoreCase = true) -> "notify_news"
                    type.startsWith("video.", ignoreCase = true) -> "notify_videos"
                    else -> "notify_platform"
                }
                if (!preferences.getBoolean(prefKey, true)) return@forEach
                val notification = eventNotification(type, event) ?: return@forEach
                sendNotification(
                    context,
                    ("event:" + event.optString("id")).hashCode(),
                    notification.first,
                    notification.second
                )
            }
        } catch (_: Exception) {
            // Event feeds are optional when signed out; public-feed checks can still run.
        }
    }

    private fun eventNotification(type: String, event: JSONObject): Pair<String, String>? {
        val title = event.optString("title").ifBlank {
            event.optString("game_title").ifBlank { event.optString("gameTitle") }
        }
        val fallbackMessage = event.optString("message").ifBlank {
            event.optString("description").ifBlank { title }
        }
        val label = when (type.lowercase()) {
            "game.published" -> "Novo jogo publicado"
            "game.available" -> "Jogo disponível"
            "game.purchasable" -> "Jogo disponível na loja"
            "game.redeemable" -> "Jogo resgatável"
            "game.released" -> "Lançamento de jogo"
            "game.updated", "game.version.updated" -> "Jogo atualizado"
            "game.deleted" -> "Alteração no catálogo"
            "news.updated", "news.published" -> "Notícia nova na Deadsmile"
            "video.updated", "video.published" -> "Novo vídeo na Deadsmile"
            "news.deleted" -> "Atualização de notícias"
            "launcher.updated", "launcher.released" -> "Launcher Deadsmile atualizado"
            "achievement.unlocked", "platform.achievement.unlocked" -> "Conquista desbloqueada"
            "wishlist.updated" -> "Lista de desejos atualizada"
            else -> return null
        }
        return label to fallbackMessage.ifBlank { "Abra Deadsmile Games para ver os detalhes." }
    }

    private data class Stream(val key: String, val title: String, val load: () -> List<FeedItem>)

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Novidades Deadsmile", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Novos jogos, notícias e vídeos."
                enableLights(true)
                lightColor = Color.rgb(129, 115, 255)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendNotification(context: Context, id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_deadsmile)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(Color.rgb(129, 115, 255))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object { private const val CHANNEL_ID = "deadsmile_content_updates" }
}
