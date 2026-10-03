package com.arkuibuilder.tool.actions

import com.arkuibuilder.tool.api.CatalogCache
import com.arkuibuilder.tool.importer.WidgetImporter
import com.arkuibuilder.tool.importer.WidgetIndex
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

/**
 * Searchable popup of every widget (My Widgets first); Enter adds the chosen one at the caret.
 * Bound to a shortcut and the editor's Generate menu, so the tool window is never needed.
 */
class AddWidgetAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        CatalogCache.ensureLoaded()
        val entries = WidgetIndex.entries()
        val title = if (CatalogCache.current().isEmpty()) {
            "Add ArkUIBuilder Widget (catalog still loading, reopen to see it)"
        } else {
            "Add ArkUIBuilder Widget"
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(entries)
            .setTitle(title)
            .setVisibleRowCount(14)
            .setRenderer(object : ColoredListCellRenderer<WidgetIndex.Entry>() {
                override fun customizeCellRenderer(
                    list: JList<out WidgetIndex.Entry>,
                    value: WidgetIndex.Entry,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    icon = if (value.mine) AllIcons.Nodes.Favorite else AllIcons.Toolwindows.ToolWindowPalette
                    append(value.snippet.title)
                    append("  ${value.lookupName}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    val where = listOf(value.platform, value.snippet.category).filter { it.isNotBlank() }
                    if (where.isNotEmpty()) append("  · ${where.joinToString(" · ")}", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
                }
            })
            .setNamerForFiltering { entry ->
                listOf(entry.snippet.title, entry.lookupName, entry.snippet.category, entry.platform)
                    .plus(entry.snippet.tags)
                    .joinToString(" ")
            }
            .setItemChosenCallback { entry -> WidgetImporter.import(project, editor, entry.snippet) }
            .createPopup()
            .showInBestPositionFor(editor)
    }
}
