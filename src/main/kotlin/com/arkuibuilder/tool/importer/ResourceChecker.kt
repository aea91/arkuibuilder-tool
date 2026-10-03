package com.arkuibuilder.tool.importer

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Finds `$r('app.<type>.<name>')` and `$rawfile('<path>')` references a widget makes that the
 * target module cannot resolve, so the user hears about them before the build fails.
 */
object ResourceChecker {
    private val resourceRef = Regex("""\${'$'}r\(\s*['"]app\.(\w+)\.([\w.]+)['"]\s*\)""")
    private val rawfileRef = Regex("""\${'$'}rawfile\(\s*['"]([^'"]+)['"]\s*\)""")

    /** Element types that live as entries in `resources/<qualifier>/element/<type>.json`. */
    private val elementTypes = setOf("color", "string", "float", "integer", "boolean", "plural", "strarray", "intarray", "pattern")

    /** Human-readable list of the references [etsRoot]'s module is missing. */
    fun missingResources(code: String, etsRoot: VirtualFile): List<String> {
        val resources = etsRoot.parent?.findChild("resources") ?: return emptyList()
        val qualifierDirs = resources.children.filter { it.isDirectory && it.name != "rawfile" }
        val missing = mutableListOf<String>()

        resourceRef.findAll(code).map { it.groupValues[1] to it.groupValues[2] }.distinct().forEach { (type, name) ->
            val found = when {
                type == "media" || type == "profile" -> qualifierDirs.any { dir ->
                    dir.findChild(type)?.children?.any { it.nameWithoutExtension == name } == true
                }
                type in elementTypes -> qualifierDirs.any { dir ->
                    dir.findChild("element")?.findChild("$type.json")?.let { definesName(it, name) } == true
                }
                else -> true // Unknown type: do not guess.
            }
            if (!found) missing += "\$r('app.$type.$name')"
        }

        rawfileRef.findAll(code).map { it.groupValues[1] }.distinct().forEach { path ->
            if (resources.findFileByRelativePath("rawfile/$path") == null) missing += "\$rawfile('$path')"
        }
        return missing
    }

    private fun definesName(file: VirtualFile, name: String): Boolean =
        try {
            Regex(""""name"\s*:\s*"${Regex.escape(name)}"""").containsMatchIn(VfsUtilCore.loadText(file))
        } catch (_: Exception) {
            true // Unreadable: do not raise a false alarm.
        }
}
