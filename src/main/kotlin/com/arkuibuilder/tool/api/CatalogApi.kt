package com.arkuibuilder.tool.api

import com.arkuibuilder.tool.model.WidgetSnippet
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CatalogApi {
    const val CATALOG_URL =
        "https://us-central1-arkuibuilder.cloudfunctions.net/getWidgetCatalog"
    private const val PROXY_BASE =
        "https://us-central1-arkuibuilder.cloudfunctions.net/proxyImage"

    private val gson = Gson()

    data class CatalogResponse(
        val widgets: List<RemoteWidget> = emptyList(),
        val managers: List<RemoteManager> = emptyList(),
        val updatedAt: String? = null,
    )

    data class RemoteWidget(
        val id: String? = null,
        val title: String? = null,
        val description: String? = null,
        val mainCategory: String? = null,
        val category: String? = null,
        @SerializedName("gifUrl") val gifUrl: String? = null,
        val code: String? = null,
        val tags: List<String>? = null,
    )

    data class RemoteManager(
        val id: String? = null,
        val name: String? = null,
        val className: String? = null,
        val description: String? = null,
        val code: String? = null,
    )

    fun fetchWidgets(): List<WidgetSnippet> {
        val connection = (URI.create(CATALOG_URL).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 40_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Catalog HTTP $code: ${body.take(200)}")
            }
            val response = gson.fromJson(body, CatalogResponse::class.java)
                ?: throw IllegalStateException("Empty catalog response")
            return response.widgets.mapNotNull { remote ->
                val id = remote.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WidgetSnippet(
                    id = id,
                    title = remote.title.orEmpty().ifBlank { id },
                    category = remote.category.orEmpty(),
                    mainCategory = remote.mainCategory.orEmpty(),
                    description = remote.description.orEmpty(),
                    code = remote.code.orEmpty(),
                    gifUrl = remote.gifUrl.orEmpty(),
                    tags = remote.tags.orEmpty(),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Convert Firebase Storage URL into proxyImage URL (same as web app). */
    fun resolveMediaUrl(gifUrl: String): String? {
        if (gifUrl.isBlank()) return null
        if (!gifUrl.startsWith("http")) return gifUrl
        return try {
            if (gifUrl.contains("firebasestorage.googleapis.com") ||
                gifUrl.contains("firebasestorage.app")
            ) {
                val match = Regex("""/o/(.+?)(\?|$)""").find(gifUrl)
                if (match != null) {
                    val path = URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8)
                    val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20")
                    "$PROXY_BASE?path=$encoded"
                } else {
                    gifUrl
                }
            } else {
                gifUrl
            }
        } catch (_: Exception) {
            gifUrl
        }
    }
}
