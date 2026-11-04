package app.emojistudio.pro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SavedTemplate(
    val id: String,
    val name: String,
    val sourceName: String,
    val json: String,
    val savedAt: Long
)

/**
 * File-backed project/template library.
 * Projects are stored in the app's private files directory, not in a
 * size-limited SharedPreferences string. Writes use a temporary file and
 * rename so an interrupted save does not replace a good project.
 */
class TemplateStore(context: Context) {
    private val directory = File(context.filesDir, "emoji_studio/projects")
    private val legacyPrefs = context.getSharedPreferences("emoji_studio_templates", Context.MODE_PRIVATE)

    init {
        if (!directory.exists()) directory.mkdirs()
        migrateLegacyItems()
    }

    @Synchronized
    fun all(): List<SavedTemplate> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file -> runCatching { decode(file) }.getOrNull() }
            ?.sortedByDescending { it.savedAt }
            ?: emptyList()
    }

    @Synchronized
    fun save(name: String, sourceName: String, json: String): SavedTemplate {
        require(name.isNotBlank()) { "نام پروژه را وارد کن" }
        LottieDocument.parse(json)
        val item = SavedTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sourceName = sourceName.ifBlank { "emoji.tgs" },
            json = json,
            savedAt = System.currentTimeMillis()
        )
        write(item)
        return item
    }

    @Synchronized
    fun update(id: String, name: String, sourceName: String, json: String): SavedTemplate {
        require(name.isNotBlank()) { "نام پروژه را وارد کن" }
        LottieDocument.parse(json)
        val current = findFile(id)
        require(current.exists()) { "پروژه پیدا نشد" }
        val old = decode(current)
        val item = old.copy(name = name.trim(), sourceName = sourceName, json = json, savedAt = System.currentTimeMillis())
        write(item)
        return item
    }

    @Synchronized
    fun delete(id: String) {
        val file = findFile(id)
        if (file.exists() && !file.delete()) error("حذف پروژه انجام نشد")
    }

    @Synchronized
    fun rename(id: String, name: String) {
        require(name.isNotBlank()) { "نام پروژه خالی است" }
        val file = findFile(id)
        require(file.exists()) { "پروژه پیدا نشد" }
        val item = decode(file).copy(name = name.trim(), savedAt = System.currentTimeMillis())
        write(item)
    }

    private fun write(item: SavedTemplate) {
        if (!directory.exists() && !directory.mkdirs()) error("ساخت پوشه پروژه‌ها ناموفق بود")
        val target = findFile(item.id)
        val temp = File(directory, "${item.id}.tmp")
        val payload = JSONObject()
            .put("id", item.id)
            .put("name", item.name)
            .put("sourceName", item.sourceName)
            .put("json", item.json)
            .put("savedAt", item.savedAt)
            .toString()
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (target.exists() && !target.delete()) error("جایگزینی نسخه قبلی ناموفق بود")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun decode(file: File): SavedTemplate {
        val item = JSONObject(file.readText(Charsets.UTF_8))
        val json = item.getString("json")
        return SavedTemplate(
            id = item.optString("id", file.nameWithoutExtension),
            name = item.optString("name", "پروژه بدون نام"),
            sourceName = item.optString("sourceName", "emoji.tgs"),
            json = json,
            savedAt = item.optLong("savedAt")
        )
    }

    private fun findFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "شناسه پروژه نامعتبر است" }
        return File(directory, "$id.json")
    }

    private fun migrateLegacyItems() {
        if (directory.listFiles()?.any { it.extension == "json" } == true) return
        val raw = legacyPrefs.getString("items", "[]") ?: "[]"
        val old = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            val json = item.optString("json")
            if (json.isBlank()) continue
            val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
            runCatching {
                write(SavedTemplate(
                    id = id,
                    name = item.optString("name", "قالب واردشده"),
                    sourceName = item.optString("sourceName", "emoji.tgs"),
                    json = json,
                    savedAt = item.optLong("savedAt", System.currentTimeMillis())
                ))
            }
        }
    }
}
