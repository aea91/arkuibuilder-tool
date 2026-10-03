package com.arkuibuilder.tool.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentParserTest {
    @Test
    fun `finds exported struct and required members`() {
        val info = ComponentParser.parse(
            """
            @Component
            export struct ColorStudio {
              @Link step: number
              @Prop title: string
              @Prop color: ResourceColor = Color.Red
              @State private open: boolean = false
              private size: number = 500

              build() {
                Column() { Text(this.title) }
              }
            }
            """.trimIndent(),
        )!!
        assertEquals("ColorStudio", info.name)
        assertTrue(info.exported)
        assertEquals(listOf("step", "title"), info.requiredParams.map { it.name })
        assertEquals("ColorStudio({ step: this.step, title: '' })", info.callText())
    }

    @Test
    fun `decorator on its own line and Require`() {
        val info = ComponentParser.parse(
            """
            @Component
            struct Card {
              @Require @Prop
              label: string = 'x'
              @ObjectLink
              item: Item
              @Watch('onChange') @Link count: number
              @BuilderParam content: () => void
              build() {}
            }
            """.trimIndent(),
        )!!
        assertFalse(info.exported)
        assertEquals(listOf("label", "item", "count", "content"), info.requiredParams.map { it.name })
        assertEquals("this.item", info.requiredParams[1].placeholder)
    }

    @Test
    fun `no params gives empty call`() {
        val info = ComponentParser.parse("@Component\nexport struct Plain {\n  build() { Text('a{b}') }\n}")!!
        assertEquals("Plain()", info.callText())
    }

    @Test
    fun `prefers the exported struct over helpers`() {
        val info = ComponentParser.parse(
            """
            @Component
            struct Helper { build() {} }
            @Component
            export struct Main { @Prop n: number
              build() {} }
            """.trimIndent(),
        )!!
        assertEquals("Main", info.name)
        assertEquals("Main({ n: 0 })", info.callText())
    }

    @Test
    fun `comments and strings do not confuse it`() {
        val info = ComponentParser.parse(
            """
            // @Component struct Fake {}
            @Component
            export struct Real {
              /* @Link nope: number */
              private s: string = '}{ @Link x: number'
              @Link yes: number
              build() {}
            }
            """.trimIndent(),
        )!!
        assertEquals("Real", info.name)
        assertEquals(listOf("yes"), info.requiredParams.map { it.name })
    }

    @Test
    fun `plain snippet is not a component`() {
        assertNull(ComponentParser.parse("Button('Click me')\n  .onClick(() => {})"))
    }

    @Test
    fun `ensureExported adds export once`() {
        val code = "@Component\nstruct Card {\n  build() {}\n}"
        val info = ComponentParser.parse(code)!!
        val exported = ComponentParser.ensureExported(code, info)
        assertTrue(exported.contains("export struct Card"))
        assertTrue(ComponentParser.parse(exported)!!.exported)
    }

    /** Set CATALOG_JSON to a saved getWidgetCatalog response to parse every catalog widget. */
    @Test
    fun `parses the whole catalog`() {
        val path = System.getenv("CATALOG_JSON") ?: return
        val json = com.google.gson.JsonParser.parseString(java.io.File(path).readText()).asJsonObject
        val widgets = json.getAsJsonArray("widgets")
        var parsed = 0
        for (w in widgets) {
            val o = w.asJsonObject
            val code = o.get("code")?.takeIf { !it.isJsonNull }?.asString ?: continue
            val info = ComponentParser.parse(code)
            if (info == null) {
                println("NOT PARSED: ${o.get("id")}")
                continue
            }
            parsed++
            println("${info.callText()}")
        }
        println("parsed $parsed / ${widgets.size()}")
    }
}
