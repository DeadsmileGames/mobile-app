package games.deadsmile.mobile.data

import android.Manifest
import android.app.NotificationChannel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Periodically checks the same public feeds rendered by the app. */
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
        val streams = listOf(
            Stream("news", "Notícias novas") { api.get("/newswire?limit=1&offset=0").feed("items", "news").firstOrNull() },
            Stream("videos", "Novo vídeo") { api.get("/videos?limit=1&offset=0").feed("items", "videos").firstOrNull() },
            Stream("games", "Jogo no catálogo") { api.get("/games?page=1&limit=1").feed("items", "games").firstOrNull() }
        )
        var succeeded = 0
        var failures = 0
        for (stream in streams) {
            val item = runCatching { stream.load() }.onFailure { failures++ }.getOrNull() ?: continue
            succeeded++
            val key = "notification_last_" + stream.key
            val previous = preferences.getString(key, null)
            preferences.edit().putString(key, item.id).apply()
            if (previous != null && previous != item.id) {
                sendNotification(context, stream.key.hashCode(), stream.title, item.title)
            }
        }
        if (succeeded == 0 && failures > 0) Result.retry() else Result.success()
    }

    private data class Stream(val key: String, val title: String, val load: () -> FeedItem?)

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
