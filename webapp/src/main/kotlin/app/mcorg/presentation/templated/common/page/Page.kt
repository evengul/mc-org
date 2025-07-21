package app.mcorg.presentation.templated.common.page

import app.mcorg.domain.model.v2.user.TokenProfile
import app.mcorg.presentation.templated.layout.topbar.topBar
import kotlinx.html.MAIN
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import kotlinx.html.title

fun createPage(
    pageTitle: String = "MC-ORG",
    pageScripts: Set<PageScript> = emptySet(),
    pageStyles: Set<PageStyle> = PageStyle.entries.toSet(),
    user: TokenProfile? = null,
    body: MAIN.() -> Unit
): String {
    return "<!DOCTYPE html>\n" + createHTML().html {
        lang = "en"
        head {
            title { + pageTitle }
            pageScripts.forEach { addScript(it) }
            pageStyles.forEach { // TODO: Split styles.css into multiple files
                link {
                    href = it.getPath()
                    rel = "stylesheet"
                }
            }
            meta {
                content = "width=device-width, initial-scale=1"
                name = "viewport"
            }
        }
        body {
            topBar(user)
            main {
                body()
            }
        }
    }
}