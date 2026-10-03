package com.arkuibuilder.tool.ui

import com.arkuibuilder.tool.api.CatalogApi
import com.arkuibuilder.tool.model.SampleCatalog
import com.arkuibuilder.tool.model.WidgetSnippet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Image
import java.awt.Insets
import java.awt.MediaTracker
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.ImageObserver
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ArkuiBuilderPanel(private val project: Project) : JPanel(BorderLayout()) {
    private var all: List<WidgetSnippet> = emptyList()
    private val statusLabel = JBLabel("Loading Firebase catalog…")
    private val gallery = GalleryPanel { loadVisibleCards() }
    private val galleryScroll = JBScrollPane(gallery)
    private val codeTitle = JBLabel().apply {
        font = font.deriveFont(Font.BOLD)
    }
    private val codePreview = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = JBUI.insets(8)
        rows = 10
    }
    private val codePanel = JPanel(BorderLayout(0, 6)).apply {
        border = JBUI.Borders.emptyTop(6)
        add(codeTitle, BorderLayout.NORTH)
        add(JBScrollPane(codePreview), BorderLayout.CENTER)
    }
    private val splitter = JBSplitter(true, 0.6f).apply {
        firstComponent = galleryScroll
    }
    private val search = SearchTextField()
    private val refreshButton = JButton("Refresh")
    private val platformCombo = ComboBox<String>()
    private val categoryCombo = ComboBox<String>()
    private var suppressEvents = false
    private var selectedCard: WidgetCard? = null

    /** Bumped whenever the gallery is rebuilt so queued loads for discarded cards are skipped. */
    @Volatile
    private var galleryGeneration = 0

    init {
        border = JBUI.Borders.empty(8)

        val filters = JPanel(GridBagLayout()).apply {
            val c = GridBagConstraints().apply {
                insets = Insets(0, 0, 6, 8)
                fill = GridBagConstraints.HORIZONTAL
                weighty = 0.0
            }
            c.gridx = 0
            c.gridy = 0
            c.weightx = 0.0
            add(JBLabel("Platform"), c)
            c.gridx = 1
            c.weightx = 1.0
            add(platformCombo, c)

            c.gridx = 0
            c.gridy = 1
            c.weightx = 0.0
            add(JBLabel("Category"), c)
            c.gridx = 1
            c.weightx = 1.0
            add(categoryCombo, c)

            c.gridx = 0
            c.gridy = 2
            c.weightx = 0.0
            add(JBLabel("Search"), c)
            c.gridx = 1
            c.weightx = 1.0
            add(search, c)
        }

        val header = JPanel(BorderLayout(8, 8)).apply {
            add(JBLabel("ArkUIBuilder · Firebase"), BorderLayout.WEST)
            add(refreshButton, BorderLayout.EAST)
            add(filters, BorderLayout.SOUTH)
        }

        val actions = JPanel(BorderLayout()).apply {
            add(
                JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    add(JButton("Insert into editor").also { it.addActionListener { insertSelected() } })
                    add(JButton("Copy code").also { it.addActionListener { copySelected() } })
                },
                BorderLayout.WEST,
            )
            add(statusLabel, BorderLayout.EAST)
        }

        add(header, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)

        platformCombo.isEnabled = false
        categoryCombo.isEnabled = false
        search.isEnabled = false

        platformCombo.addActionListener {
            if (suppressEvents) return@addActionListener
            onPlatformChanged()
        }
        categoryCombo.addActionListener {
            if (suppressEvents) return@addActionListener
            applyFilters()
        }

        search.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onSearchChanged()
            override fun removeUpdate(e: DocumentEvent?) = onSearchChanged()
            override fun changedUpdate(e: DocumentEvent?) = onSearchChanged()
        })

        // Lazy loading: previews are fetched only for cards scrolled into (or near) view.
        galleryScroll.viewport.addChangeListener { loadVisibleCards() }

        refreshButton.addActionListener { loadCatalog() }
        loadCatalog()
    }

    private fun loadCatalog() {
        statusLabel.text = "Loading Firebase catalog…"
        refreshButton.isEnabled = false
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val widgets = CatalogApi.fetchWidgets()
                SwingUtilities.invokeLater {
                    all = widgets
                    rebuildPlatforms(selectFirst = true)
                    statusLabel.text = "${widgets.size} widgets from Firebase"
                    refreshButton.isEnabled = true
                    if (widgets.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "Catalog is empty. Check Firestore `widgets` collection.",
                            "ArkUIBuilder",
                        )
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    all = SampleCatalog.widgets
                    rebuildPlatforms(selectFirst = true)
                    statusLabel.text = "Offline fallback · ${e.message?.take(60) ?: "error"}"
                    refreshButton.isEnabled = true
                    Messages.showErrorDialog(
                        project,
                        "Firebase catalog failed:\n${e.message}\n\nShowing offline samples.",
                        "ArkUIBuilder",
                    )
                }
            }
        }
    }

    /** Platform label shown in the tool; Firestore's "Smart Wearable" is displayed as "Wearable". */
    private fun platformOf(w: WidgetSnippet): String {
        val platform = w.mainCategory.ifBlank { "Other" }
        return if (platform == "Smart Wearable") "Wearable" else platform
    }

    private fun rebuildPlatforms(selectFirst: Boolean) {
        val preferredOrder = listOf("Mobile", "Wearable", "Light Wearable", "PC (2in1)", "PC", "Phone")
        val platforms = all.map { platformOf(it) }.distinct()
            .sortedWith(
                compareBy<String> { preferredOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy { it.lowercase() },
            )

        suppressEvents = true
        val platformItems = mutableListOf("Select platform…")
        platformItems.addAll(platforms)
        platformCombo.model = DefaultComboBoxModel(platformItems.toTypedArray())
        platformCombo.isEnabled = platforms.isNotEmpty()
        platformCombo.selectedIndex = if (selectFirst && platforms.size == 1) 1 else 0
        suppressEvents = false
        onPlatformChanged()
    }

    private fun selectedPlatform(): String? {
        val value = platformCombo.selectedItem as? String ?: return null
        if (value.isBlank() || value.startsWith("Select platform")) return null
        return value
    }

    /** Null means "All categories". */
    private fun selectedCategory(): String? {
        if (categoryCombo.selectedIndex <= 0) return null
        return categoryCombo.selectedItem as? String
    }

    private fun onPlatformChanged() {
        val platform = selectedPlatform()
        val categories = if (platform == null) {
            emptyList()
        } else {
            all.filter { platformOf(it) == platform }
                .map { it.category.ifBlank { "Other" } }
                .distinct()
                .sortedBy { it.lowercase() }
        }

        suppressEvents = true
        val categoryItems = mutableListOf("All categories")
        categoryItems.addAll(categories)
        categoryCombo.model = DefaultComboBoxModel(categoryItems.toTypedArray())
        categoryCombo.isEnabled = platform != null && categories.isNotEmpty()
        categoryCombo.selectedIndex = 0
        search.text = ""
        search.isEnabled = platform != null
        suppressEvents = false

        applyFilters()
    }

    private fun onSearchChanged() {
        if (suppressEvents) return
        applyFilters()
    }

    private fun applyFilters() {
        val platform = selectedPlatform()
        if (platform == null) {
            showGallery(emptyList(), hint = "Select a platform")
            statusLabel.text = "Select a platform"
            return
        }
        val category = selectedCategory()

        val q = search.text.trim().lowercase()
        val filtered = all.filter { w ->
            platformOf(w) == platform &&
                (category == null || w.category.ifBlank { "Other" } == category) &&
                (
                    q.isEmpty() ||
                        w.title.lowercase().contains(q) ||
                        w.description.lowercase().contains(q) ||
                        w.tags.any { tag -> tag.lowercase().contains(q) }
                    )
        }

        showGallery(filtered, hint = "No widgets match")
        statusLabel.text = "$platform · ${category ?: "All categories"} · ${filtered.size} widgets"
    }

    private fun showGallery(widgets: List<WidgetSnippet>, hint: String) {
        galleryGeneration++
        selectCard(null)
        gallery.removeAll()
        gallery.emptyText = hint
        widgets.forEach { gallery.add(WidgetCard(it)) }
        gallery.revalidate()
        gallery.repaint()
        galleryScroll.viewport.viewPosition = Point(0, 0)
    }

    private fun loadVisibleCards() {
        val visible = gallery.visibleRect
        if (visible.isEmpty) return
        // Prefetch a little beyond the viewport so scrolling feels seamless.
        val margin = JBUI.scale(PREFETCH_MARGIN)
        val area = Rectangle(visible.x, visible.y - margin, visible.width, visible.height + 2 * margin)
        for (c in gallery.components) {
            val card = c as? WidgetCard ?: continue
            if (card.bounds.intersects(area)) card.startLoading()
        }
    }

    private fun selectCard(card: WidgetCard?) {
        selectedCard?.setSelected(false)
        selectedCard = card
        if (card == null) {
            codePreview.text = ""
            splitter.secondComponent = null
            return
        }
        card.setSelected(true)
        val snippet = card.snippet
        codeTitle.text = listOf(snippet.title, snippet.category).filter { it.isNotBlank() }.joinToString(" · ")
        codePreview.text = snippet.code
        codePreview.caretPosition = 0
        if (splitter.secondComponent == null) splitter.secondComponent = codePanel
    }

    private fun selected(): WidgetSnippet? = selectedCard?.snippet

    private fun insertSelected() {
        val snippet = selected() ?: run {
            Messages.showInfoMessage(project, "Select a widget first.", "ArkUIBuilder")
            return
        }
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            Messages.showInfoMessage(
                project,
                "Open an .ets / text file first, then insert.",
                "ArkUIBuilder",
            )
            return
        }
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val caret = editor.caretModel.offset
            document.insertString(caret, snippet.code + "\n")
            editor.caretModel.moveToOffset(caret + snippet.code.length + 1)
        }
    }

    private fun copySelected() {
        val snippet = selected() ?: return
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(snippet.code), null)
        Messages.showInfoMessage(project, "Copied “${snippet.title}” to clipboard.", "ArkUIBuilder")
    }

    private inner class WidgetCard(val snippet: WidgetSnippet) : JPanel(BorderLayout(0, 4)) {
        private val preview = PreviewImage().apply {
            text = if (snippet.gifUrl.isBlank()) "No preview" else ""
        }
        private var loadStarted = false

        init {
            toolTipText = snippet.description.ifBlank { snippet.title }
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(preview, BorderLayout.CENTER)
            add(JBLabel(snippet.title, SwingConstants.CENTER), BorderLayout.SOUTH)
            setSelected(false)
            val click = object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = selectCard(this@WidgetCard)
            }
            addMouseListener(click)
            preview.addMouseListener(click)
        }

        fun setSelected(selected: Boolean) {
            border = JBUI.Borders.compound(
                if (selected) {
                    JBUI.Borders.customLine(JBUI.CurrentTheme.Focus.focusColor(), 2)
                } else {
                    JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1)
                },
                JBUI.Borders.empty(4),
            )
        }

        fun startLoading() {
            if (loadStarted || snippet.gifUrl.isBlank()) return
            loadStarted = true
            val mediaUrl = CatalogApi.resolveMediaUrl(snippet.gifUrl) ?: run {
                preview.text = "No preview"
                return
            }
            imageCache[mediaUrl]?.let {
                preview.image = it
                return
            }
            preview.text = "Loading…"
            val generation = galleryGeneration
            imageLoader.execute {
                // Skip requests queued for a gallery that has since been replaced.
                if (generation != galleryGeneration) return@execute
                val image = try {
                    loadImage(mediaUrl)
                } catch (_: Exception) {
                    null
                }
                SwingUtilities.invokeLater {
                    if (image != null) {
                        preview.image = image
                    } else {
                        preview.text = "Preview failed"
                    }
                }
            }
        }
    }

    private companion object {
        const val PREFETCH_MARGIN = 200

        /** At most a few previews download at once, however fast the user scrolls. */
        val imageLoader = AppExecutorUtil.createBoundedApplicationPoolExecutor("ArkUIBuilder previews", 4)
        val imageCache = ConcurrentHashMap<String, Image>()

        fun loadImage(mediaUrl: String): Image? {
            val connection = (URI.create(mediaUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val bytes = try {
                if (connection.responseCode !in 200..299) return null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
            val image = Toolkit.getDefaultToolkit().createImage(bytes)
            val tracker = MediaTracker(JPanel())
            tracker.addImage(image, 0)
            tracker.waitForID(0, 20_000)
            if (tracker.isErrorID(0) || image.getWidth(null) <= 0) return null
            imageCache[mediaUrl] = image
            return image
        }
    }
}

/**
 * Wrapping grid of equally sized cards that follows the viewport width. [onLayout] runs after the
 * cards have their bounds, which is the earliest point visibility checks give correct answers.
 */
private class GalleryPanel(private val onLayout: () -> Unit) : JPanel(null), Scrollable {
    var emptyText: String = ""

    private val cellWidth get() = JBUI.scale(150)
    private val cellHeight get() = JBUI.scale(150)
    private val gap get() = JBUI.scale(8)

    private fun columns(width: Int) = maxOf(1, (width - gap) / (cellWidth + gap))

    override fun doLayout() {
        val cols = columns(width)
        components.forEachIndexed { i, c ->
            c.setBounds(
                gap + (i % cols) * (cellWidth + gap),
                gap + (i / cols) * (cellHeight + gap),
                cellWidth,
                cellHeight,
            )
        }
        onLayout()
    }

    override fun getPreferredSize(): Dimension {
        val w = parent?.width?.takeIf { it > 0 } ?: (cellWidth + 2 * gap)
        val cols = columns(w)
        val rows = (componentCount + cols - 1) / cols
        return Dimension(w, gap + rows * (cellHeight + gap))
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (componentCount == 0 && emptyText.isNotEmpty()) {
            g.color = UIUtil.getInactiveTextColor()
            val fm = g.fontMetrics
            g.drawString(emptyText, (width - fm.stringWidth(emptyText)) / 2, JBUI.scale(40))
        }
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
        visibleRect.height
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

/**
 * Draws the preview scaled to fit its current bounds at paint time. Scaling on paint (with the
 * component as ImageObserver) keeps animated GIFs animating, unlike Image.getScaledInstance.
 */
private class PreviewImage : JBLabel("No preview", SwingConstants.CENTER) {
    var image: Image? = null
        set(value) {
            field = value
            if (value != null) text = null
            repaint()
        }

    /**
     * JLabel.imageUpdate ignores any image that is not its icon, so decoded frames would never
     * repaint us. Handle our own image; when offscreen, stop animating (the next paint resumes it).
     */
    override fun imageUpdate(img: Image?, infoflags: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (img == null || img !== image) return super.imageUpdate(img, infoflags, x, y, w, h)
        if (!isShowing) return false
        if (infoflags and (ImageObserver.FRAMEBITS or ImageObserver.ALLBITS or ImageObserver.SOMEBITS) != 0) repaint()
        return infoflags and (ImageObserver.ALLBITS or ImageObserver.ABORT) == 0
    }

    override fun paintComponent(g: Graphics) {
        val img = image ?: return super.paintComponent(g)
        val w = img.getWidth(this)
        val h = img.getHeight(this)
        if (w <= 0 || h <= 0) return
        val ins = insets
        val availW = width - ins.left - ins.right
        val availH = height - ins.top - ins.bottom
        if (availW <= 0 || availH <= 0) return
        val scale = minOf(availW.toDouble() / w, availH.toDouble() / h, 1.0)
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        val x = ins.left + (availW - dw) / 2
        val y = ins.top + (availH - dh) / 2
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.drawImage(img, x, y, dw, dh, this)
        } finally {
            g2.dispose()
        }
    }
}
