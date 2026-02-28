package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import app.mcorg.presentation.templated.common.form.searchableselect.searchableSelect
import kotlinx.html.MAIN

fun MAIN.testSelect() {
    searchableSelect("searchable-select-test", "searchableTest", listOf(
        SearchableSelectOption(
            value = "option1",
            label = "Option 1"
        ),
        SearchableSelectOption(
            value = "option2",
            label = "Option 2"
        ),
        SearchableSelectOption(
            value = "option3",
            label = "Option 3"
        ),
        SearchableSelectOption(
            value = "option4",
            label = "Option 4"
        ),
        SearchableSelectOption(
            value = "option5",
            label = "Option 5"
        ),
    ))
}