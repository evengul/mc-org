package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.ButtonType
import kotlinx.html.LI
import kotlinx.html.classes
import kotlinx.html.id

fun LI.itemRequirementListEntry(item: Item, quantity: Int) {
    id = "idea-item-requirement-${item.id}"
    +"${item.name} x $quantity"
    iconButton(Icons.DELETE, "Remove ${item.name} requirement") {
        buttonBlock = {
            type = ButtonType.button

            //language=js
            onClick = """
                const element = document.getElementById('idea-item-requirement-${item.id}');
                const hiddenElement = document.getElementById('hidden-item-requirement-${item.id}');
                if (element) {
                    element.remove();
                } else console.error("Could not find element with id idea-item-requirement-${item.id} to remove");
                if (hiddenElement) {
                    hiddenElement.remove();
                } else console.error("Could not find hidden element with id hidden-item-requirement-${item.id} to remove");
            """.trimIndent()
        }
    }
}
