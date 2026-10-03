package com.arkuibuilder.tool.importer

import com.arkuibuilder.tool.model.WidgetSnippet
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WidgetImporterTest : BasePlatformTestCase() {
    private val component = WidgetSnippet(
        id = "w1",
        title = "Color Studio",
        category = "Pickers",
        description = "",
        code = """
            @Component
            export struct ColorStudio {
              @Link step: number
              @Prop title: string
              build() {
                Image(${'$'}r('app.media.missing_icon'))
                Text(${'$'}r('app.string.app_name'))
              }
            }
        """.trimIndent(),
    )

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.addFileToProject(
            "entry/src/main/resources/base/element/string.json",
            """{ "string": [ { "name": "app_name", "value": "Demo" } ] }""",
        )
    }

    private fun openPage(text: String) {
        val file = myFixture.addFileToProject("entry/src/main/ets/pages/Index.ets", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    private fun componentFile() = myFixture.findFileInTempDir("entry/src/main/ets/components/ColorStudio.ets")

    fun `test creates file, adds import after existing imports and inserts call`() {
        openPage(
            """
            import { router } from '@kit.ArkUI'
            import {
              a,
              b
            } from './x'

            @Entry
            @Component
            struct Index {
              @State step: number = 0
              build() {
                Column() {
                  <caret>
                }
              }
            }
            """.trimIndent(),
        )
        WidgetImporter.import(project, myFixture.editor, component)

        val created = componentFile()
        assertNotNull("component file created", created)
        assertEquals(component.code.trimEnd() + "\n", VfsUtilCore.loadText(created!!))

        val text = myFixture.editor.document.text
        assertTrue(text, text.contains("} from './x'\nimport { ColorStudio } from '../components/ColorStudio'\n"))
        assertTrue(text, text.contains("ColorStudio({ step: this.step, title: '' })"))
    }

    fun `test second import reuses the file and does not duplicate the import`() {
        openPage("@Entry\n@Component\nstruct Index {\n  build() {\n    Column() {\n      <caret>\n    }\n  }\n}\n")
        WidgetImporter.import(project, myFixture.editor, component)
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("ColorStudio({"))
        WidgetImporter.import(project, myFixture.editor, component)

        val text = myFixture.editor.document.text
        assertEquals(text, 1, Regex("import \\{ ColorStudio }").findAll(text).count())
        assertEquals(text, 2, Regex("ColorStudio\\(\\{").findAll(text).count())
        assertTrue(text, text.startsWith("import { ColorStudio } from '../components/ColorStudio'\n\n@Entry"))
    }

    fun `test keep existing different file when user says so`() {
        myFixture.addFileToProject("entry/src/main/ets/components/ColorStudio.ets", "// my own version\n")
        openPage("struct Index {\n  build() {\n    <caret>\n  }\n}\n")
        TestDialogManager.setTestDialog(TestDialog { 0 }) // "Keep Existing"
        try {
            WidgetImporter.import(project, myFixture.editor, component)
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
        assertEquals("// my own version\n", VfsUtilCore.loadText(componentFile()!!))
        assertTrue(myFixture.editor.document.text.contains("import { ColorStudio }"))
    }

    fun `test non component snippet is re-indented at the caret`() {
        openPage("struct Index {\n  build() {\n    Column() {\n      <caret>\n    }\n  }\n}\n")
        val plain = component.copy(code = "Button('Hi')\n  .width(100)\n  .onClick(() => {\n    go()\n  })")
        WidgetImporter.import(project, myFixture.editor, plain)
        assertTrue(
            myFixture.editor.document.text,
            myFixture.editor.document.text.contains(
                "      Button('Hi')\n        .width(100)\n        .onClick(() => {\n          go()\n        })\n",
            ),
        )
        assertNull(componentFile())
    }

    fun `test file outside an ArkTS module gets the raw code`() {
        val file = myFixture.addFileToProject("notes/Scratch.ets", "")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        WidgetImporter.import(project, myFixture.editor, component)
        assertTrue(myFixture.editor.document.text.contains("export struct ColorStudio"))
        assertNull(componentFile())
    }

    fun `test file-and-import mode leaves the dropped call alone`() {
        openPage("struct Index {\n  build() {\n    ColorStudio({ step: this.step, title: '' })<caret>\n  }\n}\n")
        WidgetImporter.import(project, myFixture.editor, component, WidgetImporter.Mode.FILE_AND_IMPORT)
        val text = myFixture.editor.document.text
        assertEquals(text, 1, Regex("ColorStudio\\(\\{").findAll(text).count())
        assertTrue(text.startsWith("import { ColorStudio }"))
        assertNotNull(componentFile())
    }

    fun `test missing resources are reported`() {
        openPage("")
        val etsRoot = myFixture.findFileInTempDir("entry/src/main/ets")!!
        assertEquals(
            listOf("\$r('app.media.missing_icon')"),
            ResourceChecker.missingResources(component.code, etsRoot),
        )
    }
}
