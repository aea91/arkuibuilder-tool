package com.arkuibuilder.tool.completion

import com.arkuibuilder.tool.model.MyWidgetsStore
import com.arkuibuilder.tool.model.WidgetSnippet
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WidgetCompletionTest : BasePlatformTestCase() {
    private var saved: WidgetSnippet? = null

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        saved = MyWidgetsStore.save(
            WidgetSnippet(
                id = "",
                title = "Promo Banner",
                category = "Cards",
                mainCategory = "Mobile",
                description = "",
                code = "@Component\nexport struct PromoBanner {\n  @Prop label: string\n  build() {}\n}",
            ),
        )
    }

    override fun tearDown() {
        try {
            saved?.let { MyWidgetsStore.delete(it.id) }
        } finally {
            super.tearDown()
        }
    }

    fun `test completing a widget name imports it`() {
        val file = myFixture.addFileToProject(
            "entry/src/main/ets/pages/Index.ets",
            "struct Index {\n  build() {\n    PromoBa<caret>\n  }\n}\n",
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val items = myFixture.completeBasic()
        // A single match is inserted right away (items == null); otherwise pick ours.
        if (items != null) {
            val ours = items.first { it.lookupString == "PromoBanner" }
            myFixture.lookup.currentItem = ours
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val text = myFixture.editor.document.text
        assertTrue(text, text.startsWith("import { PromoBanner } from '../components/PromoBanner'\n"))
        assertTrue(text, text.contains("    PromoBanner({ label: '' })\n"))
        assertFalse(text, text.contains("PromoBaPromo"))
        assertNotNull(myFixture.findFileInTempDir("entry/src/main/ets/components/PromoBanner.ets"))
    }

    fun `test not offered outside ets files`() {
        myFixture.configureByText("notes.txt", "PromoBa<caret>")
        val items = myFixture.completeBasic()
        assertTrue(items == null || items.none { it.lookupString == "PromoBanner" })
        assertEquals("PromoBa", myFixture.editor.document.text)
    }
}
