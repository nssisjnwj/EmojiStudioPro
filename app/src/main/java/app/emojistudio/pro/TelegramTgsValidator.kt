package app.emojistudio.pro

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

data class TgsValidationReport(
    val errors: List<String>,
    val warnings: List<String>,
    val jsonBytes: Int,
    val compressedBytes: Int
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Validates Telegram's animated-sticker constraints before export.
 * This is a static structural check; it cannot prove that every rendered
 * pixel stays inside the artboard or that a loop is visually seamless.
 */
object TelegramTgsValidator {
    private const val MAX_TGS_BYTES = 64 * 1024
    private const val MAX_DURATION_SECONDS = 3.0

    fun validate(json: String): TgsValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return TgsValidationReport(
                listOf("ساختار JSON معتبر نیست: ${e.message ?: "خطای تجزیه"}"),
                emptyList(), json.toByteArray(Charsets.UTF_8).size, 0
            )
        }

        val width = root.optInt("w", -1)
        val height = root.optInt("h", -1)
        val fps = root.optDouble("fr", -1.0)
        val inFrame = root.optDouble("ip", 0.0)
        val outFrame = root.optDouble("op", -1.0)
        val duration = if (fps > 0 && outFrame >= inFrame) (outFrame - inFrame) / fps else -1.0

        if (width != 512 || height != 512) errors += "ابعاد بوم باید دقیقاً 512×512 باشد (فعلی: ${width}×${height})."
        if (fps < 30.0 || fps > 60.0) errors += "نرخ فریم باید بین 30 تا 60 باشد (فعلی: $fps)."
        if (fps > 0 && kotlin.math.abs(fps - 60.0) > 0.001) warnings += "برای انطباق کامل با استاندارد انیمیشن TGS، نرخ 60 FPS توصیه می‌شود."
        if (duration < 0) errors += "بازه فریم یا نرخ فریم نامعتبر است."
        else if (duration > MAX_DURATION_SECONDS + 0.0001) errors += "مدت انیمیشن نباید بیشتر از 3 ثانیه باشد (فعلی: ${"%.2f".format(java.util.Locale.US, duration)} ثانیه)."

        scan(root, "root", errors, warnings)

        val raw = json.toByteArray(Charsets.UTF_8)
        val compressed = try {
            val output = ByteArrayOutputStream()
            GZIPOutputStream(output).use { it.write(raw) }
            output.toByteArray()
        } catch (_: Exception) {
            ByteArray(0)
        }
        if (compressed.isEmpty()) errors += "ساخت فایل فشرده TGS ناموفق بود."
        else if (compressed.size > MAX_TGS_BYTES) {
            errors += "حجم TGS باید حداکثر 64 کیلوبایت باشد (فعلی: ${compressed.size} بایت)."
        }
        if (root.optJSONArray("layers")?.length() == 0) warnings += "این انیمیشن هیچ لایه‌ای ندارد."
        warnings += "بررسی خروج از محدوده بوم و پیوستگی حلقه انیمیشن نیازمند بررسی بصری است."

        return TgsValidationReport(errors.distinct(), warnings.distinct(), raw.size, compressed.size)
    }

    private fun scan(value: Any?, path: String, errors: MutableList<String>, warnings: MutableList<String>) {
        when (value) {
            is JSONObject -> {
                val type = value.optString("ty")
                when (type) {
                    "1" -> errors += "$path: لایه Solid در TGS پشتیبانی نمی‌شود."
                    "2" -> errors += "$path: لایه تصویری در TGS پشتیبانی نمی‌شود."
                    "5" -> errors += "$path: لایه متنی در TGS پشتیبانی نمی‌شود."
                    "rp" -> errors += "$path: Repeater در TGS پشتیبانی نمی‌شود."
                    "gf", "gs" -> errors += "$path: Gradient Fill/Stroke در TGS پشتیبانی نمی‌شود."
                    "sr" -> errors += "$path: Star Shape در TGS پشتیبانی نمی‌شود."
                    "tm" -> errors += "$path: Time Remapping در TGS پشتیبانی نمی‌شود."
                }
                if (value.optInt("ddd", 0) != 0) errors += "$path: لایه سه‌بعدی در TGS پشتیبانی نمی‌شود."
                if (value.has("masksProperties") && (value.optJSONArray("masksProperties")?.length() ?: 0) > 0) {
                    errors += "$path: Mask در TGS پشتیبانی نمی‌شود."
                }
                if (value.has("ef") && (value.optJSONArray("ef")?.length() ?: 0) > 0) {
                    errors += "$path: Layer Effects در TGS پشتیبانی نمی‌شود."
                }
                if (value.has("x")) errors += "$path: Expression در TGS پشتیبانی نمی‌شود."
                if (value.has("sr") && value.optDouble("sr", 1.0) != 1.0) warnings += "$path: سرعت/کشش زمانی لایه را بررسی کن."
                val keys = value.keys()
                val names = mutableListOf<String>()
                while (keys.hasNext()) names += keys.next()
                names.forEach { key -> scan(value.opt(key), "$path.$key", errors, warnings) }
            }
            is JSONArray -> for (i in 0 until value.length()) scan(value.opt(i), "$path[$i]", errors, warnings)
        }
    }
}
