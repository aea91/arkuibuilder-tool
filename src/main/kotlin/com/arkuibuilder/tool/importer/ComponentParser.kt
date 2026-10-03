package com.arkuibuilder.tool.importer

/** What the importer needs to know about a widget's `@Component struct`. */
data class ComponentInfo(
    val name: String,
    val exported: Boolean,
    /** Members the caller must pass, in declaration order. */
    val requiredParams: List<Param>,
) {
    data class Param(val name: String, val decorator: String, val type: String?) {
        /** Starting value for the call site; the user tabs through and adjusts it. */
        val placeholder: String
            get() = when {
                decorator == "Link" || decorator == "ObjectLink" -> "this.$name"
                type == null -> "this.$name"
                type == "number" -> "0"
                type == "string" -> "''"
                type == "boolean" -> "false"
                type == "ResourceColor" || type == "ResourceStr" -> "''"
                type.endsWith("[]") || type.startsWith("Array<") -> "[]"
                else -> "this.$name"
            }
    }

    /** The call, e.g. `ColorStudio({ step: this.step })`. */
    fun callText(): String =
        if (requiredParams.isEmpty()) {
            "$name()"
        } else {
            "$name({ ${requiredParams.joinToString(", ") { "${it.name}: ${it.placeholder}" }} })"
        }
}

/**
 * Light-weight reader for ArkTS component source. It is not a full parser: it finds the exported
 * (or first) `@Component`/`@ComponentV2` struct and its top-level members, which is all the
 * importer needs for catalog-style widgets.
 */
object ComponentParser {
    private val structRegex = Regex("""@Component(?:V2)?\b[\s\S]*?\b(export\s+)?struct\s+([A-Za-z_]\w*)""")
    private val decoratorRegex = Regex("""@(\w+)(?:\([^)]*\))?""")
    private val memberRegex = Regex(
        """^(?:(?:private|public|protected|readonly|static)\s+)*([A-Za-z_]\w*)\s*[?!]?\s*(?::\s*((?:[^=;]|=>)+?))?\s*(=(?!>).*)?;?$""",
    )

    /** Decorators whose members are passed by the caller. */
    private val alwaysRequired = setOf("Link", "ObjectLink")
    private val requiredWithoutInitializer = setOf("Prop", "Require", "Param", "BuilderParam")

    fun parse(code: String): ComponentInfo? {
        val source = stripComments(code)
        val matches = structRegex.findAll(source).toList()
        if (matches.isEmpty()) return null
        val match = matches.firstOrNull { it.groupValues[1].isNotBlank() } ?: matches.first()
        val name = match.groupValues[2]
        val bodyStart = source.indexOf('{', match.range.last)
        if (bodyStart < 0) return null
        return ComponentInfo(
            name = name,
            exported = match.groupValues[1].isNotBlank(),
            requiredParams = requiredParams(topLevelStatements(source, bodyStart)),
        )
    }

    /** Makes `struct Name` importable by adding `export` when the snippet left it out. */
    fun ensureExported(code: String, info: ComponentInfo): String {
        if (info.exported) return code
        return code.replaceFirst(Regex("""(^|\s)struct\s+${info.name}\b"""), "$1export struct ${info.name}")
    }

    private fun requiredParams(statements: List<String>): List<ComponentInfo.Param> {
        val params = mutableListOf<ComponentInfo.Param>()
        for (statement in statements) {
            val decorators = decoratorRegex.findAll(statement).map { it.groupValues[1] }.toList()
            if (decorators.isEmpty()) continue
            val rest = statement.replace(decoratorRegex, "").trim()
            val m = memberRegex.find(rest) ?: continue
            val hasInitializer = m.groupValues[3].isNotBlank()
            // @Require makes even an initialised @Prop/@BuilderParam mandatory.
            val required = decorators.any { it in alwaysRequired } || "Require" in decorators ||
                (!hasInitializer && decorators.any { it in requiredWithoutInitializer })
            if (!required) continue
            val type = m.groupValues[2].trim().ifEmpty { null }
            val decorator = decorators.firstOrNull { it != "Require" } ?: "Require"
            params += ComponentInfo.Param(m.groupValues[1], decorator, type)
        }
        return params
    }

    /**
     * Splits the struct body (starting at its `{`) into the member declarations at depth 1.
     * Decorators on their own line are joined with the member below them.
     */
    private fun topLevelStatements(source: String, bodyStart: Int): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = bodyStart
        var quote: Char? = null
        while (i < source.length) {
            val ch = source[i]
            if (quote != null) {
                // String contents are dropped so text like '@Link x' inside them is not read as code.
                if (ch == '\\') {
                    i += 2
                    continue
                }
                if (ch == quote) {
                    quote = null
                    if (depth == 1) current.append(ch)
                }
                i++
                continue
            }
            when (ch) {
                '\'', '"', '`' -> {
                    quote = ch
                    if (depth == 1) current.append(ch)
                }
                '{', '(', '[' -> {
                    if (depth == 1 && ch == '{') flush(current, statements)
                    depth++
                    // Keep `()`/`[]` on members (e.g. `number[]`) but not what is inside them.
                    if (depth == 2 && ch != '{') current.append(ch)
                }
                '}', ')', ']' -> {
                    depth--
                    if (depth == 0) {
                        flush(current, statements)
                        return statements
                    }
                    if (depth == 1 && ch != '}') current.append(ch)
                }
                '\n', ';' -> if (depth == 1) {
                    // A line holding only decorators belongs to the member on the next line.
                    if (current.trim().let { it.isNotEmpty() && it.replace(decoratorRegex, "").isBlank() }) {
                        current.append(' ')
                    } else {
                        flush(current, statements)
                    }
                }
                else -> if (depth == 1) current.append(ch)
            }
            i++
        }
        flush(current, statements)
        return statements
    }

    private fun flush(current: StringBuilder, into: MutableList<String>) {
        val text = current.toString().trim()
        if (text.isNotEmpty()) into += text
        current.setLength(0)
    }

    /** Removes // and /* */ comments while leaving string contents alone. */
    private fun stripComments(code: String): String {
        val out = StringBuilder(code.length)
        var i = 0
        var quote: Char? = null
        while (i < code.length) {
            val ch = code[i]
            if (quote != null) {
                out.append(ch)
                if (ch == '\\' && i + 1 < code.length) {
                    out.append(code[i + 1])
                    i += 2
                    continue
                }
                if (ch == quote) quote = null
                i++
                continue
            }
            if (ch == '/' && i + 1 < code.length && code[i + 1] == '/') {
                while (i < code.length && code[i] != '\n') i++
                continue
            }
            if (ch == '/' && i + 1 < code.length && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2)
                i = if (end < 0) code.length else end + 2
                continue
            }
            if (ch == '\'' || ch == '"' || ch == '`') quote = ch
            out.append(ch)
            i++
        }
        return out.toString()
    }
}
