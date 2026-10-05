package yy.daniel.yyplayerforandroid.ui

import org.json.JSONObject

internal data class LyricStyle(
    val activeSize: Float = 28f, val inactiveSize: Float = 28f,
    val activeWeight: Int = 700, val inactiveWeight: Int = 700,
    val activeColor: Long? = null, val inactiveColor: Long? = null,
    val lineSpacing: Float = 14f,
) {
    fun validated(): LyricStyle {
        require(activeSize.isFinite() && activeSize in 14f..48f && inactiveSize.isFinite() && inactiveSize in 14f..48f)
        require(activeWeight in Weights && inactiveWeight in Weights)
        require(lineSpacing.isFinite() && lineSpacing in 0f..40f)
        require(listOf(activeColor, inactiveColor).all { it == null || it in 0..0xffffffffL })
        return this
    }
    fun encode(): String = JSONObject().apply {
        put("version", 1); put("activeSize", activeSize); put("inactiveSize", inactiveSize)
        put("activeWeight", activeWeight); put("inactiveWeight", inactiveWeight)
        put("activeColor", activeColor ?: JSONObject.NULL); put("inactiveColor", inactiveColor ?: JSONObject.NULL)
        put("lineSpacing", lineSpacing)
    }.toString()
    companion object {
        val Weights = listOf(400, 500, 600, 700, 800, 900)
        val WeightNames = listOf("常规", "中等", "半粗", "粗体", "特粗", "黑体")
        fun decode(text: String?): LyricStyle {
            if (text == null) return LyricStyle()
            require(text.toByteArray().size <= 4096)
            val json = JSONObject(text)
            fun number(key: String) = (json.get(key) as? Number ?: error("Invalid $key")).toDouble().also { require(it.isFinite()) }
            fun integer(key: String): Long = number(key).let { require(it == kotlin.math.floor(it) && it in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()); it.toLong() }
            require(integer("version") == 1L)
            fun color(key: String): Long? { require(json.has(key)); return if (json.isNull(key)) null else integer(key) }
            return LyricStyle(number("activeSize").toFloat(), number("inactiveSize").toFloat(),
                integer("activeWeight").also { require(it in 400..900) }.toInt(), integer("inactiveWeight").also { require(it in 400..900) }.toInt(), color("activeColor"), color("inactiveColor"),
                number("lineSpacing").toFloat()).validated()
        }
        fun parseColor(text: String): Long? {
            val value = text.trim().removePrefix("#")
            if (!value.matches(Regex("[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8}"))) return null
            return value.toLong(16).let { if (value.length == 6) it or 0xff000000L else it }
        }
    }
}
