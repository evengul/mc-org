package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.page.PageScript.SEARCHABLE_SELECT
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.h2

fun createTestPage() = createPage(
    pageTitle = "Test Page - MC-ORG",
    pageScripts = setOf(SEARCHABLE_SELECT)
) {
    h2 {
        + "Test Page - Icons, Components, and Styles"
    }
    testIcons()
    testFonts()
    testSizes()
    testSelect()
    testButtons()
    testInput()
    testChips()
    testTabs()
    testProgress()
    testLinks()
    testModal()
}