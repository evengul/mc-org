package app.mcorg.presentation.templates.contraptions

import app.mcorg.domain.DifficultyLevel
import app.mcorg.domain.categorization.value.*
import app.mcorg.domain.minecraft.model.Item
import kotlinx.html.*

private fun HtmlBlockTag.defaultLabel(filter: CategoryFilter<*>) {
    label {
        htmlFor = filter.id
        + filter.name
    }
}

val usesDefaultLabel = listOf(
    BooleanFilter::class.java,
    IntFilter::class.java,
    DoubleFilter::class.java,
    TextFilter::class.java,
    EnumFilter::class.java,
    AllowedList::class.java
)

fun HtmlBlockTag.contraptionFilterElement(filter: CategoryFilter<*>, items: List<Item>) {
    if (!filter.canBeFiltered) return
    if (usesDefaultLabel.contains(filter.javaClass)) {
        defaultLabel(filter)
    }
    when(filter) {
        is BooleanFilter -> input {
            id = filter.id
            type = InputType.checkBox
            checked = filter.value == true
        }
        is IntFilter -> input {
            id = filter.id
            type = InputType.number
            min = filter.min?.toString() ?: ""
            max = filter.max?.toString() ?: ""
            value = filter.value?.toString() ?: ""
        }
        is DoubleFilter -> input {
            id = filter.id
            type = InputType.number
            min = filter.min?.toString() ?: ""
            max = filter.max?.toString() ?: ""
            value = filter.value?.toString() ?: ""
        }
        is TextFilter -> if (filter.longText) {
            textArea {
                rows = "3"
                id = filter.id
                filter.value?.apply { text(this) }
            }
        } else input {
            id = filter.id
            type = InputType.text
            value = filter.value ?: ""
        }
        is EnumFilter<*> -> select {
            id = filter.id
            option {
                selected = filter.value == null
                + ""
            }
            filter.clazz.enumConstants.forEach {
                option {
                    selected = filter.value?.javaClass == it
                    value = it.name
                    + when(it) {
                        is DifficultyLevel -> it.displayName
                        else -> it.name
                    }
                }
            }
        }
        is AllowedList<*> -> {
            select {
                multiple = true
                filter.allowedValues.forEach {
                    option {
                        selected = filter.value?.contains(it) ?: false
                        + when(it) {
                            String -> it.toString()
                            Enum -> (it as Enum<*>).name
                            else -> it.toString()
                        }
                    }
                }
            }
        }
        is ValueGroup -> {
            val copy = filter.value?.filters
            if (copy != null) {
                details {
                    this.open = false
                    summary { + filter.name }
                    copy.forEach {
                        contraptionFilterElement(it, items)
                    }
                }
            }
        }
        is Authors, is Credits -> {}
        is TestResult -> {
            label {
                htmlFor = filter.id + ".mspt"
                + "Measured MSPT"
            }
            input {
                id = filter.id + ".mspt"
                type = InputType.number
                value = filter.value?.mspt?.toString() ?: ""
            }
            label {
                htmlFor = filter.id + ".version"
                + "Version used during test"
            }
            input {
                id = filter.id + ".version"
                value = filter.value?.let { it.versionValue.major.toString() + "." + it.versionValue.minor.toString() } ?: ""
                pattern = "[0-9]{1,2}\\.[0-9]{1,2}"
            }
        }
        is BoundedVersion -> {
            label {
                htmlFor = filter.id + ".lower"
                + "Lowest usable version"
            }
            input {
                id = filter.id + ".lower"
                value = filter.value?.let { it.lowerBound.major.toString() + "." + it.lowerBound.minor.toString() } ?: ""
                pattern = "[0-9]{1,2}\\.[0-9]{1,2}"
            }
            label {
                htmlFor = filter.id + ".upper"
                + "Highest usable version"
            }
            input {
                id = filter.id + ".upper"
                value = filter.value?.upperBound?.let { it.major.toString() + "." + it.minor.toString() } ?: ""
                pattern = "[0-9]{1,2}\\.[0-9]{1,2}"
            }
        }

        is FarmSize -> {
            label {
                htmlFor = filter.id + ".x"
                + "Size in X(North/South) direction"
            }
            input {
                id = filter.id + ".x"
                type = InputType.number
                min = "1"
            }
            label {
                htmlFor = filter.id + ".y"
                + "Size in Y(Up/Down) direction"
            }
            input {
                id = filter.id + ".y"
                type = InputType.number
                min = "1"
            }
            label {
                htmlFor = filter.id + "z"
                + "Size in Z(East/West) direction"
            }
            input {
                id = filter.id + ".z"
                type = InputType.number
                min = "1"
            }
        }
        is Mobs -> {
            filter.value?.entries?.forEachIndexed { index, mobEntry ->
                label {
                    htmlFor = "${filter.id}.$index.name"
                    + "Name of mob"
                }
                select {
                    id = filter.id + ".$index.name"
                    filter.allowedValues.forEach {
                        option {
                            selected = it == mobEntry.key
                            + it
                        }
                    }
                }
                label {
                    htmlFor = "${filter.id}.$index.amount"
                    + "Amount of mob(s)"
                }
                input {
                    type = InputType.number
                    min = "1"
                    value = mobEntry.value.toString()
                }
            }
            label {
                htmlFor = "${filter.id}.${filter.value?.size ?: 0}.name"
                + "Name of mob"
            }
            select {
                id = filter.id + ".${filter.value?.size ?: 0}.name"
                filter.allowedValues.forEach {
                    option {
                        value = it
                        + it
                    }
                }
            }
            label {
                htmlFor = "${filter.id}.${filter.value?.size ?: 0}.amount"
                + "Amount of mob(s)"
            }
            input {
                id = "${filter.id}.${filter.value?.size ?: 0}.amount"
                type = InputType.number
                min = "1"
            }
            button {
                type = ButtonType.button
                + "Add required mob for the farm to function"
            }
        }
        is RateModes -> {
            val value = filter.value
            if (value == null) {
                label {
                    htmlFor = filter.id + ".0.mode_name"
                    + "Mode name (Leave empty if the farm only has one mode)"
                }
                input {
                    id = filter.id + "." + "mode_name"
                    type = InputType.text
                }
                label {
                    htmlFor = filter.id + ".0.drop"
                    + "Drop #1"
                }
                select {
                    id = filter.id + ".0.drop"
                    items.forEach {
                        option {
                            + it.name
                        }
                    }
                }
                label {
                    htmlFor = filter.id + ".0.amount"
                    + "Drop rate / hour"
                }
                input {
                    id = filter.id + ".0.amount"
                    type = InputType.number
                }
            } else {
                value.forEachIndexed { index, mode ->
                    label {
                        htmlFor = filter.id + "." + "mode_name"
                        + "Mode name (Leave empty if the farm only has one mode)"
                    }
                    input {
                        id = filter.id + "." + "mode_name"
                        type = InputType.text
                        this.value = mode.name
                    }
                    mode.rates.forEachIndexed { rateIndex, rate ->
                        label {
                            htmlFor = "${filter.id}.$index.drop.$rateIndex"
                            + "Drop #${index + 1}"
                        }
                        select {
                            id = filter.id + ".${rateIndex}.drop"
                            items.forEach {
                                option {
                                    + it.name
                                }
                            }
                        }
                        label {
                            htmlFor = "${filter.id}.$index.amount.$rateIndex"
                            + "Drop rate / hour"
                        }
                        input {
                            id = "${filter.id}.$index.amount.$rateIndex"
                            type = InputType.number
                            this.value = rate.second.toString()
                        }
                    }
                    label {
                        htmlFor = "${filter.id}.$index.drop.${mode.rates.size}"
                        + "Drop #${index + 1}"
                    }
                    select {
                        id = filter.id + ".${mode.rates.size}.drop"
                        items.forEach {
                            option {
                                + it.name
                            }
                        }
                    }
                    label {
                        htmlFor = "${filter.id}.$index.amount.${mode.rates.size}"
                        + "Drop rate / hour"
                    }
                    input {
                        id = "${filter.id}.$index.amount.${mode.rates.size}"
                        type = InputType.number
                    }
                    button {
                        type = ButtonType.button
                        + "Add farm drop"
                    }
                }
                label {
                    htmlFor = filter.id + "." + "mode_name"
                    + "Mode name (Leave empty if the farm only has one mode)"
                }
                input {
                    id = filter.id + "." + "mode_name"
                    type = InputType.text
                }
                label {
                    htmlFor = filter.id + "." + value.size.toString() + ".drop"
                    + "Drop #1"
                }
                select {
                    id = filter.id + ".0.drop"
                    items.forEach {
                        option {
                            + it.name
                        }
                    }
                }
                label {
                    htmlFor = filter.id + ".0.amount"
                    + "Drop rate / hour"
                }
                input {
                    id = filter.id + ".0.amount"
                    type = InputType.number
                }

                button {
                    type = ButtonType.button
                    + "Add farm drop"
                }
            }

            button {
                type = ButtonType.button
                + "Add farm mode"
            }
        }

        else -> p {
            + "Unhandled filter ${filter.name}"
        }
    }
}