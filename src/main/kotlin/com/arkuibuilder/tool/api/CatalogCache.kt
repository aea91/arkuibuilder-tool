package com.arkuibuilder.tool.api

import com.arkuibuilder.tool.model.WidgetSnippet
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Firebase catalog, fetched once per IDE session and shared by the tool window, code
 * completion and the quick picker.
 */
object CatalogCache {
    @Volatile
    private var widgets: List<WidgetSnippet>? = null

    @Volatile
    private var loading = false
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Whatever is loaded so far; empty until the first fetch finishes. Never blocks. */
    fun current(): List<WidgetSnippet> = widgets.orEmpty()

    /** Starts a background fetch unless the catalog is loaded or already loading. */
    fun ensureLoaded() {
        if (widgets == null) refreshAsync()
    }

    /** Fetches on a pooled thread; listeners run there when it finishes. */
    @Synchronized
    fun refreshAsync() {
        if (loading) return
        loading = true
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                fetch()
            } catch (_: Exception) {
                // Callers that need the error use fetch() directly.
            }
        }
    }

    /** Blocking fetch; throws on network or HTTP errors. Call off the EDT. */
    fun fetch(): List<WidgetSnippet> {
        try {
            val fresh = CatalogApi.fetchWidgets()
            widgets = fresh
            listeners.forEach { it() }
            return fresh
        } finally {
            loading = false
        }
    }

    fun addListener(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}
