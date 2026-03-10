package app.mcorg.presentation.templated.common.page

enum class PageStyle {
    RESET,
    ROOT,
    DESIGN_TOKENS,
    TEST_PAGE,
    STYLESHEET;

    fun getPath(): String {
        return when (this) {
            RESET -> "/static/styles/reset.css"
            ROOT -> "/static/styles/root.css"
            DESIGN_TOKENS -> "/static/styles/design-tokens.css"
            TEST_PAGE -> "/static/styles/pages/test-page.css"
            STYLESHEET -> "/static/styles/styles.css"
        }
    }
}