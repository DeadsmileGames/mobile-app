package games.deadsmile.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long
)

object LocalNotificationStore {
    private const val PREFS = "deadsmile-local-notifications"
    private const val KEY = "items"
    private const val MAX_ITEMS = 100

    @Synchronized
    fun add(context: Context, title: String, message: String) {
        val current = load(context).toMutableList()
        val now = System.currentTimeMillis()
        current.add(0, LocalNotification("$now-${title.hashCode()}", title.take(160), message.take(1200), now))
        save(context, current.distinctBy { it.id }.take(MAX_ITEMS))
    }

    @Synchronized
    fun load(context: Context): List<LocalNotification> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                    add(LocalNotification(id, title, item.optString("message"), item.optLong("timestamp", 0L)))
                }
            }.sortedByDescending { it.timestamp }.take(MAX_ITEMS)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(context: Context, values: List<LocalNotification>) {
        val array = JSONArray()
        values.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("message", item.message)
                .put("timestamp", item.timestamp))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
