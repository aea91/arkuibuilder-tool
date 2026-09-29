package com.arkuibuilder.tool.model

data class WidgetSnippet(
    val id: String,
    val title: String,
    val category: String,
    val mainCategory: String = "",
    val description: String,
    val code: String,
    val gifUrl: String = "",
    val tags: List<String> = emptyList(),
)

object SampleCatalog {
    /** Offline fallback when Firebase is unreachable. */
    val widgets: List<WidgetSnippet> = listOf(
        WidgetSnippet(
            id = "button-basic",
            title = "Basic Button",
            category = "Buttons",
            mainCategory = "Mobile",
            description = "Simple ArkTS Button sample (offline)",
            code = """
                Button('Click me')
                  .type(ButtonType.Capsule)
                  .backgroundColor('#E11D48')
                  .fontColor(Color.White)
                  .onClick(() => {
                    console.info('ArkUIBuilder button clicked')
                  })
            """.trimIndent(),
        ),
    )
}
