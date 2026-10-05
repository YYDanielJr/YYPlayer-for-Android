package yy.daniel.yyplayerforandroid.audio.json

/**
 * Tiny read-only JSON parser for the native control bridge.
 *
 * The bridge only produces small, flat, schema-known documents, so this is deliberately not a
 * general parser: it handles objects, arrays, strings, integers, floating point numbers and
 * booleans, refuses malformed input instead of guessing, and never keeps a second copy of the text.
 */
internal class JsonValue private constructor(
    private val kind: Kind,
    private val scalar: Any?,
    private val members: Map<String, JsonValue>?,
    private val elements: List<JsonValue>?,
) {
    private enum class Kind { OBJECT, ARRAY, STRING, NUMBER, BOOL, NULL }

    val isObject: Boolean get() = kind == Kind.OBJECT
    val isArray: Boolean get() = kind == Kind.ARRAY

    /**
     * Resolves a dotted member path such as "route.profiles". A bare name addresses a direct member,
     * which matches how the native core reads the same documents.
     */
    fun at(path: String): JsonValue? {
        var current: JsonValue = this
        for (name in path.split('.')) {
            if (name.isEmpty()) return null
            current = current.members?.get(name) ?: return null
        }
        return current
    }

    fun string(path: String): String? = at(path)?.let { it.scalar as? String }
    fun bool(path: String): Boolean? = at(path)?.let { it.scalar as? Boolean }
    fun int(path: String): Long? = at(path)?.let { member ->
        when (val value = member.scalar) {
            is Long -> value
            is Double -> value.toLong()
            else -> null
        }
    }

    fun array(path: String): List<JsonValue>? = at(path)?.elements

    companion object {
        /** Last parse failure, kept for tests and diagnostics; never used to make decisions. */
        @Volatile
        var lastParseError: String? = null
            private set

        fun parse(text: String?): JsonValue? {
            if (text == null) return null
            return runCatching {
                val value = Reader(text).parseDocument()
                // Every document on this bridge is an object; a bare array or scalar means the text
                // is not the answer that was asked for, so it is refused rather than half-used.
                require(value.isObject) { "root is not an object" }
                value
            }.getOrElse { error ->
                // The bridge is the only producer of these documents, so a parse failure is a real
                // contract break and must be visible rather than silently becoming "no answer".
                lastParseError = error.message
                null
            }
        }

        private class Reader(private val text: String) {
            private var index = 0

            fun parseDocument(): JsonValue {
                skipWhitespace()
                val value = parseValue()
                skipWhitespace()
                require(index == text.length) { "trailing content at $index" }
                return value
            }

            private fun parseValue(): JsonValue {
                skipWhitespace()
                require(index < text.length) { "unexpected end of input" }
                return when (val c = text[index]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> JsonValue(Kind.STRING, parseString(), null, null)
                    't', 'f' -> parseBool()
                    'n' -> parseNull()
                    else -> if (c == '-' || c.isDigit()) parseNumber() else error("unexpected '$c' at $index")
                }
            }

            private fun parseObject(): JsonValue {
                expect('{')
                val members = LinkedHashMap<String, JsonValue>()
                skipWhitespace()
                if (peek() == '}') {
                    index++
                    return JsonValue(Kind.OBJECT, null, members, null)
                }
                while (true) {
                    skipWhitespace()
                    val name = parseString()
                    skipWhitespace()
                    expect(':')
                    members[name] = parseValue()
                    skipWhitespace()
                    when (val c = next()) {
                        ',' -> continue
                        '}' -> return JsonValue(Kind.OBJECT, null, members, null)
                        else -> error("unexpected '$c' in object at ${index - 1}")
                    }
                }
            }

            private fun parseArray(): JsonValue {
                expect('[')
                val elements = ArrayList<JsonValue>()
                skipWhitespace()
                if (peek() == ']') {
                    index++
                    return JsonValue(Kind.ARRAY, null, null, elements)
                }
                while (true) {
                    elements += parseValue()
                    skipWhitespace()
                    when (val c = next()) {
                        ',' -> continue
                        ']' -> return JsonValue(Kind.ARRAY, null, null, elements)
                        else -> error("unexpected '$c' in array at ${index - 1}")
                    }
                }
            }

            private fun parseString(): String {
                expect('"')
                val builder = StringBuilder()
                while (true) {
                    require(index < text.length) { "unterminated string" }
                    when (val c = text[index++]) {
                        '"' -> return builder.toString()
                        '\\' -> {
                            require(index < text.length) { "unterminated escape" }
                            when (val escape = text[index++]) {
                                '"', '\\', '/' -> builder.append(escape)
                                'b' -> builder.append('\b')
                                'f' -> builder.append('\u000C')
                                'n' -> builder.append('\n')
                                'r' -> builder.append('\r')
                                't' -> builder.append('\t')
                                'u' -> {
                                    require(index + 4 <= text.length) { "short unicode escape" }
                                    val code = text.substring(index, index + 4).toInt(16)
                                    builder.append(code.toChar())
                                    index += 4
                                }
                                else -> error("bad escape '\\$escape'")
                            }
                        }
                        else -> builder.append(c)
                    }
                }
            }

            private fun parseBool(): JsonValue {
                if (text.startsWith("true", index)) {
                    index += 4
                    return JsonValue(Kind.BOOL, true, null, null)
                }
                if (text.startsWith("false", index)) {
                    index += 5
                    return JsonValue(Kind.BOOL, false, null, null)
                }
                error("bad literal at $index")
            }

            private fun parseNull(): JsonValue {
                require(text.startsWith("null", index)) { "bad literal at $index" }
                index += 4
                return JsonValue(Kind.NULL, null, null, null)
            }

            private fun parseNumber(): JsonValue {
                val start = index
                if (peek() == '-') index++
                while (index < text.length && (text[index].isDigit() || text[index] == '.' ||
                        text[index] == 'e' || text[index] == 'E' || text[index] == '+' ||
                        text[index] == '-')
                ) {
                    index++
                }
                val slice = text.substring(start, index)
                require(slice.isNotEmpty() && slice != "-") { "bad number at $start" }
                val value: Any = if (slice.contains('.') || slice.contains('e') || slice.contains('E')) {
                    slice.toDouble()
                } else {
                    slice.toLong()
                }
                return JsonValue(Kind.NUMBER, value, null, null)
            }

            private fun skipWhitespace() {
                while (index < text.length && text[index].isWhitespace()) index++
            }

            private fun peek(): Char? = if (index < text.length) text[index] else null

            private fun next(): Char {
                require(index < text.length) { "unexpected end of input" }
                return text[index++]
            }

            private fun expect(c: Char) {
                val actual = next()
                require(actual == c) { "expected '$c' but found '$actual' at ${index - 1}" }
            }
        }
    }
}
