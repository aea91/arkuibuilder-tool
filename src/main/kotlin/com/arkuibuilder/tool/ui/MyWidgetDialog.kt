package com.arkuibuilder.tool.ui

import com.arkuibuilder.tool.model.MyWidgetsStore
import com.arkuibuilder.tool.model.WidgetSnippet
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Creates or edits one of the user's own widgets. [existing] null means "new"; [initialCode]
 * pre-fills the code (e.g. from the editor selection).
 */
class MyWidgetDialog(
    private val project: Project?,
    private val existing: WidgetSnippet? = null,
    initialCode: String = "",
    categories: List<String> = emptyList(),
) : DialogWrapper(project) {
    private val nameField = JBTextField(existing?.title.orEmpty())
    private val platformCombo = ComboBox(PLATFORMS.toTypedArray()).apply {
        isEditable = true
        selectedItem = existing?.mainCategory?.ifBlank { null } ?: PLATFORMS.first()
    }
    private val categoryCombo = ComboBox(categories.distinct().sorted().toTypedArray()).apply {
        isEditable = true
        selectedItem = existing?.category.orEmpty()
    }
    private val descriptionField = JBTextField(existing?.description.orEmpty())
    private val tagsField = JBTextField(existing?.tags.orEmpty().joinToString(", ")).apply {
        emptyText.text = "Comma separated, e.g. button, form"
    }
    private val previewField = TextFieldWithBrowseButton().apply {
        text = existing?.gifUrl.orEmpty()
        addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileDescriptor()
                    .withFileFilter { it.extension?.lowercase() in IMAGE_EXTENSIONS }
                    .withTitle("Choose Preview Image")
                    .withDescription("GIF, PNG or JPG"),
                project,
            ),
        )
    }
    private val codeArea = JTextArea(existing?.code ?: initialCode).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        tabSize = 2
        margin = JBUI.insets(6)
    }

    init {
        title = if (existing == null) "Save to My Widgets" else "Edit My Widget"
        setOKButtonText(if (existing == null) "Save" else "Update")
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            insets = Insets(0, 0, 6, 8)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        }
        fun row(label: String, field: JComponent, grow: Boolean = false) {
            c.gridx = 0
            c.weightx = 0.0
            c.weighty = 0.0
            c.fill = GridBagConstraints.HORIZONTAL
            panel.add(JBLabel(label), c)
            c.gridx = 1
            c.weightx = 1.0
            if (grow) {
                c.weighty = 1.0
                c.fill = GridBagConstraints.BOTH
            }
            panel.add(field, c)
            c.gridy++
        }
        c.gridy = 0
        row("Name", nameField)
        row("Platform", platformCombo)
        row("Category", categoryCombo)
        row("Description", descriptionField)
        row("Tags", tagsField)
        row("Preview image", previewField)
        row("ArkTS code", JBScrollPane(codeArea).apply { preferredSize = Dimension(560, 300) }, grow = true)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("Enter a name.", nameField)
        if (codeArea.text.isBlank()) return ValidationInfo("Code cannot be empty.", codeArea)
        val preview = previewField.text.trim()
        if (preview.isNotEmpty() && preview != existing?.gifUrl && !File(preview).isFile) {
            return ValidationInfo("Preview image not found.", previewField.textField)
        }
        return null
    }

    override fun doOKAction() {
        val snippet = WidgetSnippet(
            id = existing?.id.orEmpty(),
            title = nameField.text.trim(),
            mainCategory = (platformCombo.editor.item as? String).orEmpty().trim(),
            category = (categoryCombo.editor.item as? String).orEmpty().trim(),
            description = descriptionField.text.trim(),
            code = codeArea.text.trimEnd(),
            tags = tagsField.text.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
        try {
            saved = MyWidgetsStore.save(snippet, previewSource = previewField.text.trim())
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Could not save the widget:\n${e.message}", "ArkUIBuilder")
            return
        }
        super.doOKAction()
    }

    /** The stored widget after OK, or null if cancelled. */
    var saved: WidgetSnippet? = null
        private set

    private companion object {
        val PLATFORMS = listOf("Mobile", "Wearable", "Light Wearable", "PC (2in1)")
        val IMAGE_EXTENSIONS = setOf("gif", "png", "jpg", "jpeg")
    }
}
