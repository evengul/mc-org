package app.mcorg.presentation.templated.common.page

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.breadcrumb.Breadcrumbs
import app.mcorg.presentation.templated.common.breadcrumb.breadcrumbComponent
import app.mcorg.presentation.templated.common.modal.confirmDeleteModal
import app.mcorg.presentation.templated.layout.alert.alertContainer
import app.mcorg.presentation.templated.layout.topbar.topBar
import kotlinx.html.MAIN
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import kotlinx.html.unsafe
import org.intellij.lang.annotations.Language

fun createPage(
    pageTitle: String = "MC-ORG",
    pageScripts: Set<PageScript> = setOf(PageScript.THEME_SWITCHER, PageScript.HTMX, PageScript.CONFIRMATION_MODAL),
    pageStyles: Set<PageStyle> = PageStyle.entries.toSet(),
    user: TokenProfile? = null,
    unreadNotificationCount: Int = 0,
    breadcrumbs: Breadcrumbs? = null,
    body: MAIN.() -> Unit
): String {
    return "<!DOCTYPE html>\n" + createHTML().html {
        lang = "en"
        head {
            title { + pageTitle }

            // Inline blocking script to prevent theme flash (FOUC)
            script {
                unsafe {
                    + getThemeScript.trimIndent()
                }
            }

            pageScripts.forEach { addScript(it) }
            pageStyles.forEach { // TODO: Split styles.css into multiple files
                link {
                    href = it.getPath()
                    rel = "stylesheet"
                }
            }
            link {
                rel = "preconnect"
                href = "https://fonts.googleapis.com"
            }
            link {
                rel = "preconnect"
                href = "https://fonts.gstatic.com"
                attributes["crossorigin"] = "true"
            }
            link {
                rel = "stylesheet"
                href = "https://fonts.googleapis.com/css2?family=Roboto+Mono:ital,wght@0,100..700;1,100..700&display=swap"
            }
            meta {
                content = "width=device-width, initial-scale=1"
                name = "viewport"
            }
        }
        body {
            topBar(user, unreadNotificationCount)
            breadcrumbs?.let { breadcrumbComponent(it) }
            alertContainer()
            confirmDeleteModal()
            main {
                body()
            }
        }
    }
}

@Language("JavaScript")
private const val getThemeScript = """
    (function() {
        const THEMES = { OVERWORLD: 'overworld', NETHER: 'nether', END: 'end' };
        const savedTheme = localStorage.getItem('mc-org-theme');
        let theme;
        
        if (savedTheme && ['overworld', 'nether', 'end'].includes(savedTheme)) {
            theme = savedTheme;
        } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            theme = THEMES.END;
        } else {
            theme = THEMES.OVERWORLD;
        }
        
        document.documentElement.setAttribute('data-theme', theme);
    })();
"""