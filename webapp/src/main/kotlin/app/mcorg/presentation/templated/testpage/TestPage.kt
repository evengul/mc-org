package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.modal.modal
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.h2
import kotlinx.html.p

fun createTestPage() = createPage(
    pageTitle = "Test Page - MC-ORG",
) {
    h2 {
        + "Test Page - Icons, Components, and Styles"
    }
    testIcons()
    testFonts()
    testSizes()
    testButtons()
    testInput()
    testChips()
    testTabs()
    testProgress()
    testLinks()
    modal("test-modal", "Some title", openButtonHandler = {
        + "Open Modal"
    }) {
        saveText = "Shut up :)"
        description = "This is a test modal dialog to demonstrate the modal component functionality."
        addChildren = {
            p {
                + "This is a modal dialog. You can add any content you like here."
            }
        }
    }
}