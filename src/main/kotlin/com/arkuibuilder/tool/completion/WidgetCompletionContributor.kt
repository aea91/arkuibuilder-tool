package com.arkuibuilder.tool.completion

import com.arkuibuilder.tool.api.CatalogCache
import com.arkuibuilder.tool.importer.WidgetImporter
import com.arkuibuilder.tool.importer.WidgetIndex
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware

/**
 * In `.ets` files, offers every ArkUIBuilder widget by component name (e.g. `ColorStu…`).
 * Choosing one runs the same import as the tool window: file, import line and call.
 */
class WidgetCompletionContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.originalFile.virtualFile?.extension != "ets") return
        CatalogCache.ensureLoaded()
        // Only once the user has typed something, so the list does not flood empty completions.
        if (result.prefixMatcher.prefix.length < 2) return

        for (entry in WidgetIndex.entries()) {
            val element = LookupElementBuilder.create(entry, entry.lookupName)
                .withLookupString(entry.snippet.title)
                .withPresentableText(entry.lookupName)
                .withTailText("  ${entry.snippet.title}", true)
                .withTypeText(if (entry.mine) "My Widgets" else "ArkUIBuilder · ${entry.platform}", true)
                .withIcon(if (entry.mine) AllIcons.Nodes.Favorite else AllIcons.Toolwindows.ToolWindowPalette)
                .withCaseSensitivity(false)
                .withInsertHandler { context, _ ->
                    // Replace the typed name with the real import once completion has finished.
                    context.document.deleteString(context.startOffset, context.tailOffset)
                    context.commitDocument()
                    val editor = context.editor
                    val project = context.project
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && !editor.isDisposed) {
                            WidgetImporter.import(project, editor, entry.snippet)
                        }
                    }
                }
            result.addElement(element)
        }
    }
}
