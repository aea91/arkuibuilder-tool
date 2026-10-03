package com.arkuibuilder.tool.actions

import com.arkuibuilder.tool.model.MyWidgetsStore
import com.arkuibuilder.tool.ui.MyWidgetDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/** Editor context menu: save the selected code to My Widgets. */
class SaveSelectionAsWidgetAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val code = editor.selectionModel.selectedText?.trimIndent() ?: return
        MyWidgetDialog(
            e.project,
            initialCode = code,
            categories = MyWidgetsStore.widgets().map { it.category }.filter { it.isNotBlank() },
        ).show()
    }
}
