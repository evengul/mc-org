package app.mcorg.presentation.templated.common.page

enum class PageStyle {
    RESET,
    ROOT,
    TEST_PAGE,
    STYLESHEET;

    fun getPath(): String {
        return when (this) {
            RESET -> "/static/styles/reset.css"
            ROOT -> "/static/styles/root.css"
            TEST_PAGE -> "/static/styles/pages/test-page.css"
            STYLESHEET -> "/static/styles/styles.css"
        }
    }
}