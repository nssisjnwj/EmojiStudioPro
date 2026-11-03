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
        opacity.put("a", 0)
        opacity.put("k", opacityPercent.coerceIn(0, 100))
    }

    /** Changes non-keyframed solid fill colors in the selected layer and its shape groups. */
    fun setStaticFillColor(path: String, argb: Int): Int {
        val layer = resolveLayer(path)
        val red = ((argb shr 16) and 0xff) / 255.0
        val green = ((argb shr 8) and 0xff) / 255.0
        val blue = (argb and 0xff) / 255.0
        var changed = 0

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    if (value.optString("ty") == "fl") {
                        val color = value.optJSONObject("c")
                        val key = color?.opt("k")
                        if (key is JSONArray && key.length() >= 3 && key.opt(0) is Number) {
                            key.put(0, red)
                            key.put(1, green)
                            key.put(2, blue)
                            changed++
                        }
                    }
                    val keys = value.keys()
                    val names = mutableListOf<String>()
                    while (keys.hasNext()) names.add(keys.next())
                    names.forEach { walk(value.opt(it)) }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
            }
        }

        walk(layer.optJSONArray("shapes"))
        return changed
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
