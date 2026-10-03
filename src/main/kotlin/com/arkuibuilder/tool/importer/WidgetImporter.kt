package com.arkuibuilder.tool.importer

import com.arkuibuilder.tool.model.WidgetSnippet
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager

/**
 * Adds a widget to the user's code the way ArkTS expects:
 *
 * 1. the `@Component struct` goes into its own file, `<module>/src/main/ets/components/<Name>.ets`
 * 2. the current file gets `import { Name } from '<relative path>'`
 * 3. only the call, e.g. `Name({ step: this.step })`, is inserted at the caret, with each required
 *    parameter as a Tab stop
 *
 * Snippets that are not a component (or an editor outside an ArkTS module) fall back to inserting
 * the code itself, re-indented to the caret line.
 */
object WidgetImporter {
    private const val COMPONENTS_DIR = "components"
    private const val TITLE = "ArkUIBuilder"

    enum class Mode {
        /** File + import + call with Tab stops at the caret. */
        FULL,

        /** File + import only; the call text is already in the document (e.g. after a drop). */
        FILE_AND_IMPORT,
    }

    fun selectedEditor(project: Project): Editor? = FileEditorManager.getInstance(project).selectedTextEditor

    /** Entry point for the panel, quick picker and completion. Must run on the EDT. */
    fun import(project: Project, editor: Editor, snippet: WidgetSnippet, mode: Mode = Mode.FULL) {
        val info = ComponentParser.parse(snippet.code)
        val currentFile = FileDocumentManager.getInstance().getFile(editor.document)
        val etsRoot = currentFile?.let { findEtsRoot(it) }

        if (info == null || currentFile == null || etsRoot == null || currentFile.extension != "ets") {
            if (mode == Mode.FULL) insertRaw(project, editor, snippet)
            if (info != null && etsRoot == null) {
                val what = if (mode == Mode.FULL) {
                    "Inserted the full component code."
                } else {
                    "Added the call, but <b>${info.name}</b> was not created or imported."
                }
                notify(
                    project,
                    "$what Open a file under <code>src/main/ets</code> of a HarmonyOS module to have " +
                        "the component saved as its own file and imported.",
                    NotificationType.INFORMATION,
                )
            }
            return
        }

        val componentCode = ComponentParser.ensureExported(snippet.code, info).trimEnd() + "\n"
        val existing = etsRoot.findChild(COMPONENTS_DIR)?.findChild("${info.name}.ets")
        if (existing != null && existing == currentFile) {
            notify(project, "You are editing <b>${info.name}.ets</b> itself; nothing to import.", NotificationType.INFORMATION)
            return
        }
        val writeFile = when {
            existing == null -> true
            sameCode(VfsUtilCore.loadText(existing), componentCode) -> false
            else -> when (
                Messages.showDialog(
                    project,
                    "${COMPONENTS_DIR}/${info.name}.ets already exists with different code.\n" +
                        "Replace it with the ArkUIBuilder version, or keep yours?",
                    TITLE,
                    arrayOf("Keep Existing", "Replace", "Cancel"),
                    0,
                    Messages.getQuestionIcon(),
                )
            ) {
                0 -> false
                1 -> true
                else -> return
            }
        }

        var componentFile: VirtualFile? = existing
        WriteCommandAction.runWriteCommandAction(project, "Import ${info.name}", null, {
            if (writeFile) {
                val dir = VfsUtil.createDirectoryIfMissing(etsRoot, COMPONENTS_DIR)
                    ?: error("Cannot create $COMPONENTS_DIR in ${etsRoot.path}")
                val file = dir.findChild("${info.name}.ets") ?: dir.createChildData(this, "${info.name}.ets")
                VfsUtil.saveText(file, componentCode)
                componentFile = file
            }
            addImport(editor, info.name, importPath(currentFile, componentFile!!))
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        })

        if (mode == Mode.FULL) insertCall(project, editor, info)

        val created = if (writeFile) "Created <b>$COMPONENTS_DIR/${info.name}.ets</b> and imported it." else null
        val missing = ResourceChecker.missingResources(snippet.code, etsRoot)
        when {
            missing.isNotEmpty() -> notify(
                project,
                listOfNotNull(
                    created,
                    "<b>${info.name}</b> uses resources this module does not have yet:<br>" +
                        missing.joinToString("<br>") { "• <code>$it</code>" } +
                        "<br>Add them under <code>src/main/resources</code> or replace the references.",
                ).joinToString("<br><br>"),
                NotificationType.WARNING,
            )
            created != null -> notify(project, created, NotificationType.INFORMATION)
        }
    }

    /** `<module>/src/main/ets` containing [file], or null when the file is not in an ArkTS module. */
    fun findEtsRoot(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = file.parent
        while (dir != null) {
            if (dir.name == "ets" && dir.parent?.name == "main" && dir.parent?.parent?.name == "src") return dir
            dir = dir.parent
        }
        return null
    }

    /** Module path for an import, e.g. `../components/Foo` (no extension, always starts with `.`). */
    fun importPath(from: VirtualFile, target: VirtualFile): String {
        val relative = VfsUtilCore.findRelativePath(from, target, '/')
            ?: return "./${target.nameWithoutExtension}"
        val noExt = relative.removeSuffix(".${target.extension}")
        return if (noExt.startsWith(".")) noExt else "./$noExt"
    }

    private fun sameCode(a: String, b: String) =
        StringUtil.convertLineSeparators(a).trim() == StringUtil.convertLineSeparators(b).trim()

    /** Adds the import after the file's last top-level import, unless the name is imported already. */
    private fun addImport(editor: Editor, name: String, path: String) {
        val document = editor.document
        val text = document.text
        if (Regex("""import\s*\{[^}]*\b$name\b[^}]*}\s*from""").containsMatchIn(text)) return
        val statement = "import { $name } from '$path'\n"
        // Up to the first quoted module path, so multi-line `import {\n a,\n b\n} from 'x'` counts as one.
        val lastImport = Regex("""^import\b[\s\S]*?['"][^'"\n]+['"];?""", RegexOption.MULTILINE)
            .findAll(text).lastOrNull()
        if (lastImport != null) {
            document.insertString(lastImport.range.last + 1, "\n" + statement.trimEnd())
        } else {
            val separator = if (text.startsWith("\n")) "" else "\n"
            document.insertString(0, statement + separator)
        }
    }

    /** Inserts the call as a live template: Tab moves through the parameter values. */
    private fun insertCall(project: Project, editor: Editor, info: ComponentInfo) {
        val manager = TemplateManager.getInstance(project)
        val template = manager.createTemplate("", "")
        template.isToReformat = false
        if (info.requiredParams.isEmpty()) {
            template.addTextSegment("${info.name}()")
        } else {
            template.addTextSegment("${info.name}({ ")
            info.requiredParams.forEachIndexed { i, p ->
                if (i > 0) template.addTextSegment(", ")
                template.addTextSegment("${p.name}: ")
                template.addVariable("P$i", ConstantNode(p.placeholder), ConstantNode(p.placeholder), true)
            }
            template.addTextSegment(" })")
        }
        template.addEndVariable()
        WriteCommandAction.runWriteCommandAction(project, "Insert ${info.name}", null, {
            manager.startTemplate(editor, template)
        })
    }

    /** Old behaviour for plain snippets: the code itself, indented to match the caret line. */
    fun insertRaw(project: Project, editor: Editor, snippet: WidgetSnippet) {
        WriteCommandAction.runWriteCommandAction(project, "Insert ${snippet.title}", null, {
            val document = editor.document
            val caret = editor.caretModel.offset
            val text = reindent(snippet.code, lineIndentBefore(document.charsSequence, caret))
            document.insertString(caret, text)
            editor.caretModel.moveToOffset(caret + text.length)
        })
    }

    /** Whitespace before the caret when the caret line is blank up to it, else that line's indent. */
    private fun lineIndentBefore(text: CharSequence, offset: Int): String {
        var start = offset
        while (start > 0 && text[start - 1] != '\n') start--
        var end = start
        while (end < text.length && (text[end] == ' ' || text[end] == '\t')) end++
        return text.subSequence(start, end).toString()
    }

    /**
     * The first line goes at the caret as-is (the caret line already has its indentation); every
     * following line gets [indent] in front of its own, common-indent-stripped, indentation.
     */
    fun reindent(code: String, indent: String): String {
        val lines = code.trimIndent().trimEnd().lines()
        return lines.mapIndexed { i, line ->
            if (i == 0 || line.isBlank()) line else indent + line
        }.joinToString("\n") + "\n"
    }

    fun notify(project: Project, html: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ArkUIBuilder")
            .createNotification(TITLE, html, type)
            .notify(project)
    }
}
