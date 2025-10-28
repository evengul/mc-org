package app.mcorg.presentation.templated.common.searchField

import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.button.ghostButton
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.title

fun <T : Tag> T.searchField(id: String, block: SearchField.() -> Unit = {}) {
    val component = SearchField(id)
    block.invoke(component)
    this.addComponent(component)
}

data class SearchFieldHxValues(
    val hxGet: String,
    val hxTarget: String,
    val hxInclude: String? = null,
    val hxSwap: String = "outerHTML",
    val hxTrigger: String = "input changed delay:500ms, change changed, search"
)

class SearchField(
    val id: String,
    var placeHolder: String = "Search...",
    var hxValues: SearchFieldHxValues? = null
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div("search-wrapper") {
            input {
                this.id = this@SearchField.id
                this.type = InputType.search
                this.placeholder = placeHolder
                this.name = "query"

                hxValues?.let {
                    hxGet(it.hxGet)
                    it.hxInclude?.let { hxInclude -> hxInclude(hxInclude) }
                    hxTarget(it.hxTarget)
                    hxSwap(it.hxSwap)
                    hxTrigger(it.hxTrigger)
                    hxIndicator(".search-wrapper")
                }
            }
            ghostButton("x") {
                addClass("input-clear-button")
                buttonBlock = {
                    type = ButtonType.button
                    title = "Clear search"
                    attributes["aria-label"] = "Clear search"
                    onClick = """
                        const input = document.getElementById('${this@SearchField.id}');
                        input.value = '';
                        input.dispatchEvent(new Event('change'));
                    """.trimIndent()
                }
            }
        }
    }
}