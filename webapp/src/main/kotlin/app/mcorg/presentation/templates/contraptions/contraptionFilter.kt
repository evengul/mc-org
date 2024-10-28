package app.mcorg.presentation.templates.contraptions

import app.mcorg.domain.categorization.Categories
import app.mcorg.domain.categorization.CategoryType
import app.mcorg.domain.categorization.subtypes.*
import app.mcorg.domain.minecraft.Item
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createContraptionFilter(
    categories: Categories,
    items: List<Item>,
    selectedCategory: CategoryType?,
    selectedSubCategory: SubCategoryType?
) = createHTML().form {
    contraptionFilter(categories, items, selectedCategory, selectedSubCategory)
}

fun FORM.contraptionFilter(
    categories: Categories,
    items: List<Item>,
    selectedCategory: CategoryType? = null,
    selectedSubCategory: SubCategoryType? = null,
) {
    id = "contraptions-filter-form"
    h1 {
        + "Contraptions filter"
    }
    categories.common.filters.forEach {
        contraptionFilterElement(it, items)
    }
    label {
        htmlFor = "contraptions-filter-category-select"
        + "Category"
    }
    select {
        id = "contraptions-filter-category-select"
        name = "filterCategory"
        hxGet("/app/contraptions/filter")
        hxTarget("#contraptions-filter-form")
        hxSwap("outerHTML")
        hxTrigger("change changed")
        option {
            value = "NONE"
            + ""
        }
        CategoryType.values().forEach {
            option {
                value = it.name
                +it.displayName
            }
        }
    }
    val category = categories.categories.find { it.type == selectedCategory }
    if (category != null) {
        h2 {
            + category.type.displayName
        }
        category.filters.forEach {
            contraptionFilterElement(it, items)
        }
        if (selectedCategory != null) {
            label {
                htmlFor = "contraptions-filter-subcategory-select"
                + "Subcategory"
            }
            select {
                id = "contraptions-filter-subcategory-select"
                name = "filterSubCategory"
                hxGet("/app/contraptions/filter")
                hxTarget("#contraptions-filter-form")
                hxSwap("outerHTML")
                hxTrigger("change changed")
                option {
                    selected = selectedSubCategory == null
                    value = "NONE"
                    + ""
                }
                when(selectedCategory) {
                    CategoryType.FARM -> FarmSubcategoryType.values().map { it.name to it.displayName }
                    CategoryType.STORAGE -> StorageSubCategoryType.values().map { it.name to it.displayName }
                    CategoryType.CART_TECH -> CartTechSubCategoryType.values().map { it.name to it.displayName }
                    CategoryType.TNT_TECH -> TntTechSubCategoryType.values().map { it.name to it.displayName }
                    CategoryType.SLIMESTONE -> SlimestoneSubCategoryType.values().map { it.name to it.displayName }
                    CategoryType.OTHER -> OtherSubCategoryType.values().map { it.name to it.displayName }
                }.forEach {
                    option {
                        selected = selectedSubCategory?.displayName == it.second
                        value = it.first
                        + it.second
                    }
                }
            }
            val subCategory = category.subCategories.find { it.type == selectedSubCategory }
            if (subCategory != null) {
                h3 {
                    + subCategory.type.displayName
                }
                subCategory.filters.forEach {
                    contraptionFilterElement(it, items)
                }
            }
        }
    }
}
