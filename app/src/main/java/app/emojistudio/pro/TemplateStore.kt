package app.emojistudio.pro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedTemplate(
    val id: String,
    val name: String,
    val sourceName: String,
    val json: String,
    val savedAt: Long
)

class TemplateStore(context: Context) {
    private val prefs = context.getSharedPreferences("emoji_studio_templates", Context.MODE_PRIVATE)

    fun all(): List<SavedTemplate> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val json = item.optString("json")
                if (json.isBlank()) continue
                add(SavedTemplate(
                    id = item.optString("id"),
                    name = item.optString("name", "قالب بدون نام"),
                    sourceName = item.optString("sourceName", "emoji.tgs"),
                    json = json,
                    savedAt = item.optLong("savedAt")
                ))
            }
        }.sortedByDescending { it.savedAt }
    }

    fun save(name: String, sourceName: String, json: String): SavedTemplate {
        require(name.isNotBlank()) { "نام قالب را وارد کن" }
        val item = SavedTemplate(UUID.randomUUID().toString(), name.trim(), sourceName, json, System.currentTimeMillis())
        val items = all().toMutableList()
        items.add(item)
        persist(items)
        return item
    }

    fun delete(id: String) = persist(all().filterNot { it.id == id })

    fun rename(id: String, name: String) {
        require(name.isNotBlank()) { "نام قالب خالی است" }
        persist(all().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    private fun persist(items: List<SavedTemplate>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("sourceName", item.sourceName)
                .put("json", item.json)
                .put("savedAt", item.savedAt))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
