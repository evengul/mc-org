package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import kotlinx.html.*

fun FORM.hiddenFields(data: CreateIdeaWizardData) {
    if (data.stage != CreateIdeaStage.BASIC_INFO) {
        data.name?.let {
            input {
                type = InputType.hidden
                name = "name"
                value = it
            }
        }

        data.description?.let {
            input {
                type = InputType.hidden
                name = "description"
                value = it
            }
        }

        data.difficulty?.let {
            input {
                type = InputType.hidden
                name = "difficulty"
                value = it.name
            }
        }
    }

    if (data.stage != CreateIdeaStage.AUTHOR_INFO) {
        data.author?.let {
            when(it) {
                is Author.SingleAuthor -> {
                    input {
                        type = InputType.hidden
                        name = "authorType"
                        value = "single"
                    }
                    input {
                        type = InputType.hidden
                        name = "authorName"
                        value = it.name
                    }
                }
                is Author.Team -> {
                    input {
                        type = InputType.hidden
                        name = "authorType"
                        value = "team"
                    }
                    it.members.forEachIndexed { index, member ->
                        input {
                            type = InputType.hidden
                            name = "teamMembers[$index][name]"
                            value = member.name
                        }
                        member.role.let { role ->
                            input {
                                type = InputType.hidden
                                name = "teamMembers[$index][role]"
                                value = role
                            }
                        }
                        member.contributions.let { contributions ->
                            input {
                                type = InputType.hidden
                                name = "teamMembers[$index][contributions]"
                                value = contributions.joinToString(",")
                            }
                        }
                    }
                }
                is Author.TeamAuthor -> {}
            }
        }
    }

    if (data.stage != CreateIdeaStage.VERSION_COMPATIBILITY) {
        data.versionRange?.let {
            when(it) {
                is MinecraftVersionRange.Bounded -> {
                    input {
                        type = InputType.hidden
                        name = "versionRangeType"
                        value = "bounded"
                    }
                    input {
                        type = InputType.hidden
                        name = "versionFrom"
                        value = it.from.toString()
                    }
                    input {
                        type = InputType.hidden
                        name = "versionTo"
                        value = it.to.toString()
                    }
                }
                is MinecraftVersionRange.LowerBounded -> {
                    input {
                        type = InputType.hidden
                        name = "versionRangeType"
                        value = "lowerBounded"
                    }
                    input {
                        type = InputType.hidden
                        name = "versionFrom"
                        value = it.from.toString()
                    }
                }
                is MinecraftVersionRange.UpperBounded -> {
                    input {
                        type = InputType.hidden
                        name = "versionRangeType"
                        value = "upperBounded"
                    }
                    input {
                        type = InputType.hidden
                        name = "versionTo"
                        value = it.to.toString()
                    }
                }
                is MinecraftVersionRange.Unbounded -> {
                    input {
                        type = InputType.hidden
                        name = "versionRangeType"
                        value = "unbounded"
                    }
                }
            }
        }
    }

    // Item requirement inputs are always hidden
    div {
        id = "hidden-item-requirements-fields"
        data.itemRequirements?.let {
            it.forEach { req ->
                input {
                    hiddenItemRequirementField(req.key.id, req.value)
                }
            }
        }
    }

    if (data.stage != CreateIdeaStage.CATEGORY_SPECIFIC_FIELDS) {
        data.categoryData?.let { (category, map) ->
            input {
                type = InputType.hidden
                name = "category"
                value = category.name
            }
            val schema = IdeaCategorySchemas.getSchema(category)
            schema.fields.forEach { field ->
                when (field) {
                    is CategoryField.BooleanField,
                    is CategoryField.Number,
                    is CategoryField.Percentage,
                    is CategoryField.Rate,
                    is CategoryField.Select<*>,
                    is CategoryField.Text-> hiddenBasicField(field, map[field.key])
                    is CategoryField.ListField -> hiddenListField(field, map[field.key])
                    is CategoryField.MultiSelect -> hiddenMultiSelectField(field, map[field.key])
                    is CategoryField.StructField -> hiddenStructField(field, map[field.key])
                    is CategoryField.TypedMapField -> hiddenTypedMapField(field, map[field.key])
                }
            }
        }
    }
}

fun INPUT.hiddenItemRequirementField(itemId: String, quantity: Int) {
    id = "hidden-item-requirement-$itemId"
    type = InputType.hidden
    name = "itemRequirements.$itemId"
    value = quantity.toString()
}

private fun FORM.hiddenBasicField(field: CategoryField, value: Any?) {
    input {
        type = InputType.hidden
        name = field.getCompleteKey()
        if (value != null) {
            this.value = value.toString()
        }
    }
}

private fun FORM.hiddenMultiSelectField(field: CategoryField.MultiSelect, value: Any?) {
    val set = value as? Set<*> ?: return
    set.forEach { v ->
        input {
            type = InputType.hidden
            name = field.getCompleteKey()
            if (v != null) {
                this.value = v.toString()
            }
        }
    }
}

private fun FORM.hiddenListField(field: CategoryField.ListField, value: Any?) {
    val list = value as? List<*> ?: return
    input {
        type = InputType.hidden
        name = field.getCompleteKey()
        if (list.isNotEmpty()) {
            this.value = list.joinToString(",")
        }
    }
}

private fun FORM.hiddenStructField(field: CategoryField.StructField, value: Any?) {
    val structMap = value as? Map<*, *> ?: return
    field.fields.forEach { subField ->
        val v = structMap[subField.key]
        hiddenTypedMapEntry(subField, v)
    }
}

private fun FORM.hiddenTypedMapField(field: CategoryField.TypedMapField, value: Any?) {
    val map = value as? Map<*, *> ?: return

    map.forEach { (key, value) ->
        hiddenTypedMapEntry(field.keyType, key)
        hiddenTypedMapEntry(field.valueType, value)
    }
}

private fun FORM.hiddenTypedMapEntry(field: CategoryField, value: Any?) {
    when (field) {
        is CategoryField.BooleanField,
        is CategoryField.Text,
        is CategoryField.Number,
        is CategoryField.Percentage,
        is CategoryField.Rate,
        is CategoryField.Select<*> -> hiddenBasicField(field, value)
        is CategoryField.MultiSelect -> hiddenMultiSelectField(field, value)
        is CategoryField.ListField -> hiddenListField(field, value)
        is CategoryField.StructField -> hiddenStructField(field, value)
        is CategoryField.TypedMapField -> hiddenTypedMapField(field, value)
    }
}

