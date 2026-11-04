package app.emojistudio.pro

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Small, loss-preserving editor for the JSON document inside a Lottie/TGS file.
 * Unknown Lottie properties are kept untouched.
 */
data class LottieLayerNode(
    val path: String,
    val name: String,
    val type: String,
    val hidden: Boolean,
    val children: List<LottieLayerNode> = emptyList()
)

class LottieDocument private constructor(private val root: JSONObject) {

    fun json(): String = root.toString()

    fun layerTree(): List<LottieLayerNode> {
        val assets = root.optJSONArray("assets") ?: JSONArray()
        return buildNodes(root.optJSONArray("layers") ?: JSONArray(), "root", assets, 0)
    }

    fun renameLayer(path: String, newName: String) {
        require(newName.isNotBlank()) { "Layer name cannot be empty" }
        resolveLayer(path).put("nm", newName.trim())
    }

    fun setLayerHidden(path: String, hidden: Boolean) {
        resolveLayer(path).put("hd", hidden)
    }

    fun setLayerOpacity(path: String, opacityPercent: Int) {
        val layer = resolveLayer(path)
        val ks = layer.optJSONObject("ks") ?: JSONObject().also { layer.put("ks", it) }
        val opacity = ks.optJSONObject("o") ?: JSONObject().also { ks.put("o", it) }
        updateScalarProperty(opacity, opacityPercent.coerceIn(0, 100).toDouble())
    }

    fun readLayerOpacity(path: String): Int {
        val property = resolveLayer(path).optJSONObject("ks")?.optJSONObject("o") ?: return 100
        return readScalarProperty(property)?.toInt()?.coerceIn(0, 100) ?: 100
    }

    fun readLayerPosition(path: String): Pair<Double, Double>? {
        val property = resolveLayer(path).optJSONObject("ks")?.optJSONObject("p") ?: return null
        val values = readVectorProperty(property) ?: return null
        if (values.size < 2) return null
        return values[0] to values[1]
    }

    fun readLayerRotation(path: String): Double? {
        val ks = resolveLayer(path).optJSONObject("ks") ?: return null
        val property = ks.optJSONObject("r") ?: ks.optJSONObject("rz") ?: return null
        return readScalarProperty(property)
    }

    fun readLayerScale(path: String): Double? {
        val property = resolveLayer(path).optJSONObject("ks")?.optJSONObject("s") ?: return null
        return readVectorProperty(property)?.firstOrNull()
    }

    /** Updates fill and stroke colors in the selected layer, including color keyframes. */
    fun setStaticFillColor(path: String, argb: Int): Int {
        val layer = resolveLayer(path)
        val rgba = listOf(
            ((argb shr 16) and 0xff) / 255.0,
            ((argb shr 8) and 0xff) / 255.0,
            (argb and 0xff) / 255.0
        )
        var changed = 0

        fun updateColorFrame(frame: JSONObject, key: String) {
            val values = frame.optJSONArray(key) ?: return
            if (values.length() < 3 || values.opt(0) !is Number) return
            for (i in 0..2) values.put(i, rgba[i])
            changed++
        }

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val type = value.optString("ty")
                    if (type == "fl" || type == "st") {
                        val color = value.optJSONObject("c")
                        val property = color?.opt("k")
                        if (property is JSONArray) {
                            if (property.length() >= 3 && property.opt(0) is Number) {
                                for (i in 0..2) property.put(i, rgba[i])
                                changed++
                            } else {
                                for (i in 0 until property.length()) {
                                    val frame = property.optJSONObject(i) ?: continue
                                    updateColorFrame(frame, "s")
                                    if (frame.has("e")) updateColorFrame(frame, "e")
                                }
                            }
                        }
                    }
                    val iterator = value.keys()
                    val keys = mutableListOf<String>()
                    while (iterator.hasNext()) keys += iterator.next()
                    keys.forEach { walk(value.opt(it)) }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
            }
        }

        walk(layer.optJSONArray("shapes"))
        return changed
    }

    fun layerDescription(path: String): String {
        val layer = resolveLayer(path)
        val type = when (layer.optInt("ty", -1)) {
            0 -> "پوشه / Precomp"
            1 -> "لایه رنگی"
            2 -> "تصویر"
            3 -> "لایه کنترلی"
            4 -> "شکل برداری"
            5 -> "متن"
            else -> "لایه"
        }
        val text = readText(layer)
        val shapeCount = layer.optJSONArray("shapes")?.length() ?: 0
        return buildString {
            append(type)
            if (text != null) append(" • متن: ").append(text)
            if (shapeCount > 0) append(" • ").append(shapeCount).append(" گروه شکل")
            if (layer.optBoolean("hd", false)) append(" • مخفی")
        }
    }

    fun readTextLayer(path: String): String? = readText(resolveLayer(path))

    fun setTextLayer(path: String, newText: String): Boolean {
        val layer = resolveLayer(path)
        val textData = layer.optJSONObject("t")?.optJSONObject("d")?.optJSONArray("k")
            ?: return false
        var changed = false
        for (i in 0 until textData.length()) {
            val keyframe = textData.optJSONObject(i) ?: continue
            val style = keyframe.optJSONObject("s") ?: continue
            if (style.has("t")) {
                style.put("t", newText)
                changed = true
            }
        }
        return changed
    }

    fun setLayerTransform(path: String, x: Double? = null, y: Double? = null,
                          rotation: Double? = null, scalePercent: Double? = null) {
        val ks = resolveLayer(path).optJSONObject("ks")
            ?: throw IllegalArgumentException("Transform این لایه موجود نیست")
        if (x != null || y != null) {
            val p = ks.optJSONObject("p") ?: throw IllegalArgumentException("موقعیت این لایه قابل ویرایش نیست")
            updateVectorProperty(p, mapOf(0 to x, 1 to y).filterValues { it != null }.mapValues { it.value!! })
        }
        if (rotation != null) {
            val r = ks.optJSONObject("r") ?: ks.optJSONObject("rz")
                ?: throw IllegalArgumentException("چرخش این لایه موجود نیست")
            updateScalarProperty(r, rotation)
        }
        if (scalePercent != null) {
            val scale = ks.optJSONObject("s") ?: throw IllegalArgumentException("اندازه این لایه موجود نیست")
            updateVectorProperty(scale, mapOf(0 to scalePercent, 1 to scalePercent))
        }
    }

    private fun readScalarProperty(property: JSONObject): Double? {
        val key = property.opt("k")
        if (property.optInt("a", 0) == 0) return (key as? Number)?.toDouble()
        val first = (key as? JSONArray)?.optJSONObject(0) ?: return null
        val value = first.opt("s")
        return when (value) {
            is JSONArray -> (value.opt(0) as? Number)?.toDouble()
            is Number -> value.toDouble()
            else -> null
        }
    }

    private fun readVectorProperty(property: JSONObject): List<Double>? {
        val key = property.opt("k")
        val values = if (property.optInt("a", 0) == 0) key as? JSONArray
            else ((key as? JSONArray)?.optJSONObject(0)?.opt("s") as? JSONArray)
        if (values == null) return null
        return (0 until values.length()).mapNotNull { (values.opt(it) as? Number)?.toDouble() }
    }

    private fun updateScalarProperty(property: JSONObject, value: Double) {
        val key = property.opt("k")
        if (property.optInt("a", 0) == 0) {
            property.put("k", value)
            return
        }
        val frames = key as? JSONArray ?: throw IllegalArgumentException("ساختار Keyframe این ویژگی معتبر نیست")
        for (i in 0 until frames.length()) {
            val frame = frames.optJSONObject(i) ?: continue
            updateFrameValue(frame, "s", value)
            if (frame.has("e")) updateFrameValue(frame, "e", value)
        }
    }

    private fun updateVectorProperty(property: JSONObject, updates: Map<Int, Double>) {
        val key = property.opt("k")
        if (property.optInt("a", 0) == 0) {
            val values = key as? JSONArray ?: throw IllegalArgumentException("این ویژگی برداری قابل ویرایش نیست")
            updates.forEach { (index, value) ->
                require(index < values.length()) { "ساختار بردار این ویژگی پشتیبانی نمی‌شود" }
                values.put(index, value)
            }
            return
        }
        val frames = key as? JSONArray ?: throw IllegalArgumentException("ساختار Keyframe این ویژگی معتبر نیست")
        for (i in 0 until frames.length()) {
            val frame = frames.optJSONObject(i) ?: continue
            updateFrameVector(frame, "s", updates)
            if (frame.has("e")) updateFrameVector(frame, "e", updates)
        }
    }

    private fun updateFrameValue(frame: JSONObject, key: String, value: Double) {
        when (val current = frame.opt(key)) {
            is JSONArray -> current.put(0, value)
            is Number -> frame.put(key, value)
        }
    }

    private fun updateFrameVector(frame: JSONObject, key: String, updates: Map<Int, Double>) {
        val values = frame.optJSONArray(key) ?: return
        updates.forEach { (index, value) -> if (index < values.length()) values.put(index, value) }
    }

    fun nearestRootLayer(x: Double, y: Double): String? {
        val layers = root.optJSONArray("layers") ?: return null
        val width = root.optDouble("w", 512.0).coerceAtLeast(1.0)
        val height = root.optDouble("h", 512.0).coerceAtLeast(1.0)
        var bestPath: String? = null
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until layers.length()) {
            val layer = layers.optJSONObject(i) ?: continue
            if (layer.optBoolean("hd", false)) continue
            val position = layer.optJSONObject("ks")?.optJSONObject("p")?.optJSONArray("k")
                ?: continue
            if (position.length() < 2 || position.opt(0) !is Number || position.opt(1) !is Number) continue
            val lx = position.optDouble(0) / width
            val ly = position.optDouble(1) / height
            val dx = lx - x
            val dy = ly - y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                bestPath = "root/l:$i"
            }
        }
        return if (bestDistance <= 0.20 * 0.20) bestPath else null
    }

    private fun readText(layer: JSONObject): String? {
        val keyframes = layer.optJSONObject("t")?.optJSONObject("d")?.optJSONArray("k") ?: return null
        for (i in 0 until keyframes.length()) {
            val style = keyframes.optJSONObject(i)?.optJSONObject("s") ?: continue
            val text = style.optString("t", "")
            if (text.isNotEmpty()) return text
        }
        return null
    }

    fun addRectangle(name: String, argb: Int) {
        val layers = root.optJSONArray("layers") ?: JSONArray().also { root.put("layers", it) }
        val width = root.optInt("w", 512).coerceAtLeast(1)
        val height = root.optInt("h", 512).coerceAtLeast(1)
        val color = JSONArray()
            .put(((argb shr 16) and 0xff) / 255.0)
            .put(((argb shr 8) and 0xff) / 255.0)
            .put((argb and 0xff) / 255.0)
            .put(1.0)
        val rectangle = JSONObject()
            .put("ty", "rc")
            .put("d", 1)
            .put("s", JSONObject().put("a", 0).put("k", JSONArray().put(width * 0.5).put(height * 0.5)))
            .put("p", JSONObject().put("a", 0).put("k", JSONArray().put(0).put(0)))
            .put("r", JSONObject().put("a", 0).put("k", 24))
        val fill = JSONObject()
            .put("ty", "fl")
            .put("c", JSONObject().put("a", 0).put("k", color))
            .put("o", JSONObject().put("a", 0).put("k", 100))
            .put("r", 1)
            .put("bm", 0)
        val shapeGroup = JSONObject()
            .put("ty", "gr")
            .put("nm", name)
            .put("it", JSONArray().put(rectangle).put(fill)
                .put(JSONObject().put("ty", "tr")
                    .put("p", JSONObject().put("a", 0).put("k", JSONArray().put(width * 0.5).put(height * 0.5)))
                    .put("a", JSONObject().put("a", 0).put("k", JSONArray().put(0).put(0)))
                    .put("s", JSONObject().put("a", 0).put("k", JSONArray().put(100).put(100)))
                    .put("r", JSONObject().put("a", 0).put("k", 0))
                    .put("o", JSONObject().put("a", 0).put("k", 100))
                    .put("sk", JSONObject().put("a", 0).put("k", 0))
                    .put("sa", JSONObject().put("a", 0).put("k", 0))))
        val layer = JSONObject()
            .put("ddd", 0)
            .put("ind", nextLayerIndex(layers))
            .put("ty", 4)
            .put("nm", name)
            .put("sr", 1)
            .put("ks", JSONObject()
                .put("o", JSONObject().put("a", 0).put("k", 100))
                .put("r", JSONObject().put("a", 0).put("k", 0))
                .put("p", JSONObject().put("a", 0).put("k", JSONArray().put(0).put(0).put(0)))
                .put("a", JSONObject().put("a", 0).put("k", JSONArray().put(0).put(0).put(0)))
                .put("s", JSONObject().put("a", 0).put("k", JSONArray().put(100).put(100).put(100))))
            .put("ao", 0)
            .put("shapes", JSONArray().put(shapeGroup))
            .put("ip", root.optDouble("ip", 0.0))
            .put("op", root.optDouble("op", 60.0))
            .put("st", 0)
            .put("bm", 0)
        layers.put(layer)
    }

    fun toTgsBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(root.toString().toByteArray(Charsets.UTF_8))
        }
        return output.toByteArray()
    }

    private fun nextLayerIndex(layers: JSONArray): Int {
        var max = 0
        for (i in 0 until layers.length()) {
            max = maxOf(max, layers.optJSONObject(i)?.optInt("ind", 0) ?: 0)
        }
        return max + 1
    }

    private fun buildNodes(
        layers: JSONArray,
        prefix: String,
        assets: JSONArray,
        depth: Int
    ): List<LottieLayerNode> {
        if (depth > 12) return emptyList()
        val result = mutableListOf<LottieLayerNode>()
        for (i in 0 until layers.length()) {
            val layer = layers.optJSONObject(i) ?: continue
            val path = "$prefix/l:$i"
            val typeCode = layer.optInt("ty", -1)
            val type = when (typeCode) {
                0 -> "Precomposition"
                1 -> "Solid"
                2 -> "Image"
                3 -> "Null"
                4 -> "Shape"
                5 -> "Text"
                13 -> "Camera"
                else -> "Layer $typeCode"
            }
            val children = if (typeCode == 0) {
                val refId = layer.optString("refId")
                val asset = findAsset(assets, refId)
                buildNodes(asset?.optJSONArray("layers") ?: JSONArray(), "$path/a:$refId", assets, depth + 1)
            } else emptyList()
            result.add(
                LottieLayerNode(
                    path = path,
                    name = layer.optString("nm", "Layer ${i + 1}"),
                    type = type,
                    hidden = layer.optBoolean("hd", false),
                    children = children
                )
            )
        }
        return result
    }

    private fun findAsset(assets: JSONArray, id: String): JSONObject? {
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("id") == id) return asset
        }
        return null
    }

    private fun resolveLayer(path: String): JSONObject {
        val tokens = path.split("/")
        require(tokens.firstOrNull() == "root") { "Invalid layer path" }
        var currentLayers = root.optJSONArray("layers")
            ?: throw IllegalArgumentException("Animation has no layers")
        var currentLayer: JSONObject? = null
        var index = 1

        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token.startsWith("l:") -> {
                    val layerIndex = token.removePrefix("l:").toInt()
                    currentLayer = currentLayers.optJSONObject(layerIndex)
                        ?: throw IllegalArgumentException("Layer not found")
                    index++
                }
                token.startsWith("a:") -> {
                    val assetId = token.removePrefix("a:")
                    val asset = findAsset(root.optJSONArray("assets") ?: JSONArray(), assetId)
                        ?: throw IllegalArgumentException("Precomposition asset not found")
                    currentLayers = asset.optJSONArray("layers")
                        ?: throw IllegalArgumentException("Precomposition has no layers")
                    currentLayer = null
                    index++
                }
                else -> throw IllegalArgumentException("Invalid layer path")
            }
        }
        return currentLayer ?: throw IllegalArgumentException("Layer not found")
    }

    companion object {
        fun parse(json: String): LottieDocument {
            val root = JSONObject(json)
            require(root.has("layers") && root.has("w") && root.has("h")) {
                "This JSON is not a Lottie animation"
            }
            return LottieDocument(root)
        }
    }
}
