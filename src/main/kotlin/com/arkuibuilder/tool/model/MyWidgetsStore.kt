package com.arkuibuilder.tool.model

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The user's own widgets, kept on this machine and shared by every project.
 *
 * Stored as JSON in `<IDE config>/arkuibuilder/my-widgets.json` so the file is easy to back up or
 * copy to another machine. Preview images are copied next to it under `previews/`, so moving or
 * deleting the original image does not break the widget.
 */
object MyWidgetsStore {
    /** Platform-combo entry that lists these widgets instead of the Firebase catalog. */
    const val PLATFORM = "My Widgets"

    private val log = Logger.getInstance(MyWidgetsStore::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val listType = object : TypeToken<List<SavedWidget>>() {}.type
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private val dir: Path get() = Path.of(PathManager.getConfigPath(), "arkuibuilder")
    private val file: Path get() = dir.resolve("my-widgets.json")
    private val previewsDir: Path get() = dir.resolve("previews")

    /** Field names are the JSON keys; keep them stable. */
    private data class SavedWidget(
        val id: String? = null,
        val title: String? = null,
        val platform: String? = null,
        val category: String? = null,
        val description: String? = null,
        val code: String? = null,
        val previewPath: String? = null,
        val tags: List<String>? = null,
    )

    @Volatile
    private var cache: List<WidgetSnippet>? = null

    @Synchronized
    fun widgets(): List<WidgetSnippet> = cache ?: load().also { cache = it }

    fun isMine(snippet: WidgetSnippet) = snippet.id.startsWith(ID_PREFIX)

    /**
     * Adds [snippet], or replaces the widget with the same id. [previewSource] is an image file to
     * copy in as the preview; null keeps the current one, a blank string removes it.
     */
    @Synchronized
    fun save(snippet: WidgetSnippet, previewSource: String? = null): WidgetSnippet {
        val id = if (isMine(snippet)) snippet.id else ID_PREFIX + UUID.randomUUID()
        val previous = widgets().firstOrNull { it.id == id }
        val preview = when {
            previewSource == null -> previous?.gifUrl.orEmpty()
            previewSource.isBlank() -> ""
            previewSource == previous?.gifUrl -> previewSource
            else -> copyPreview(id, Path.of(previewSource))
        }
        if (previous != null && previous.gifUrl.isNotBlank() && previous.gifUrl != preview) {
            deletePreview(previous.gifUrl)
        }
        val saved = snippet.copy(id = id, gifUrl = preview)
        val updated = widgets().filterNot { it.id == id } + saved
        write(updated)
        return saved
    }

    @Synchronized
    fun delete(id: String) {
        val target = widgets().firstOrNull { it.id == id } ?: return
        write(widgets().filterNot { it.id == id })
        if (target.gifUrl.isNotBlank()) deletePreview(target.gifUrl)
    }

    /** Called on the thread that changed the store; listeners hop to the EDT themselves. */
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun load(): List<WidgetSnippet> {
        if (!Files.exists(file)) return emptyList()
        return try {
            val json = Files.readString(file, StandardCharsets.UTF_8)
            val saved: List<SavedWidget> = gson.fromJson(json, listType) ?: emptyList()
            saved.mapNotNull { it.toSnippet() }
        } catch (e: Exception) {
            log.warn("Could not read $file", e)
            emptyList()
        }
    }

    private fun write(widgets: List<WidgetSnippet>) {
        Files.createDirectories(dir)
        val json = gson.toJson(widgets.map { it.toSaved() })
        // Write to a temp file first so a crash mid-write cannot wipe the user's widgets.
        val tmp = dir.resolve("my-widgets.json.tmp")
        Files.writeString(tmp, json, StandardCharsets.UTF_8)
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cache = widgets
        listeners.forEach { it() }
    }

    private fun copyPreview(id: String, source: Path): String {
        Files.createDirectories(previewsDir)
        val ext = source.fileName.toString().substringAfterLast('.', "png").lowercase()
        // A new name on every change, so the IDE's image cache never serves the old picture.
        val target = previewsDir.resolve("${id.removePrefix(ID_PREFIX)}-${System.currentTimeMillis()}.$ext")
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return target.toString()
    }

    private fun deletePreview(path: String) {
        val p = Path.of(path)
        // Only ever delete copies this store made.
        if (p.startsWith(previewsDir)) {
            try {
                Files.deleteIfExists(p)
            } catch (e: Exception) {
                log.warn("Could not delete preview $p", e)
            }
        }
    }

    private fun SavedWidget.toSnippet(): WidgetSnippet? {
        if (id.isNullOrBlank() || code == null) return null
        return WidgetSnippet(
            id = id,
            title = title.orEmpty().ifBlank { "Untitled" },
            category = category.orEmpty(),
            mainCategory = platform.orEmpty(),
            description = description.orEmpty(),
            code = code,
            gifUrl = previewPath.orEmpty(),
            tags = tags.orEmpty(),
        )
    }

    private fun WidgetSnippet.toSaved() = SavedWidget(
        id = id,
        title = title,
        platform = mainCategory,
        category = category,
        description = description,
        code = code,
        previewPath = gifUrl.ifBlank { null },
        tags = tags,
    )

    private const val ID_PREFIX = "local-"
}
