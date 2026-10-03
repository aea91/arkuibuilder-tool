package com.arkuibuilder.tool.importer

import com.arkuibuilder.tool.api.CatalogCache
import com.arkuibuilder.tool.model.MyWidgetsStore
import com.arkuibuilder.tool.model.WidgetSnippet

/** My Widgets plus the catalog, each with its parsed component, for completion and the quick picker. */
object WidgetIndex {
    data class Entry(val snippet: WidgetSnippet, val info: ComponentInfo?, val mine: Boolean) {
        /** What the user types to find it: the struct name, or the title without spaces for plain snippets. */
        val lookupName: String = info?.name ?: snippet.title.filter { it.isLetterOrDigit() || it == '_' }

        val platform: String
            get() = if (mine) MyWidgetsStore.PLATFORM else snippet.mainCategory.let { if (it == "Smart Wearable") "Wearable" else it }
    }

    private var lastMine: List<WidgetSnippet>? = null
    private var lastCatalog: List<WidgetSnippet>? = null
    private var cached: List<Entry> = emptyList()

    /** My Widgets first, then the catalog. Parsing is redone only when either list changes. */
    @Synchronized
    fun entries(): List<Entry> {
        val mine = MyWidgetsStore.widgets()
        val catalog = CatalogCache.current()
        if (mine !== lastMine || catalog !== lastCatalog) {
            cached = mine.sortedBy { it.title.lowercase() }.map { Entry(it, ComponentParser.parse(it.code), mine = true) } +
                catalog.sortedBy { it.title.lowercase() }.map { Entry(it, ComponentParser.parse(it.code), mine = false) }
            cached = cached.filter { it.lookupName.isNotEmpty() }
            lastMine = mine
            lastCatalog = catalog
        }
        return cached
    }
}
