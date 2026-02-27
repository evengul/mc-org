package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.modal.exampleBasicModal
import app.mcorg.presentation.templated.common.modal.exampleComplexModal
import app.mcorg.presentation.templated.common.modal.exampleCreateUserFormModal
import app.mcorg.presentation.templated.common.modal.exampleDeleteConfirmationModal
import app.mcorg.presentation.templated.common.modal.exampleSuccessNotificationModal
import kotlinx.html.MAIN

fun MAIN.testModal() {
    exampleBasicModal()
    exampleCreateUserFormModal()
    exampleDeleteConfirmationModal("item", "Name")
    exampleSuccessNotificationModal("Success!!!")
    exampleComplexModal()
}