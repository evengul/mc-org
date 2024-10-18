package app.mcorg.domain.categorization

@CategoryMarker
data class Categories(
    val common: Common,
    val categories: MutableList<Category>
)

fun Categories.add(category: Category) {
    this.categories.add(category)
}

fun categories(init: Categories.() -> Unit): Categories {
    val categories = Categories(
        common = Common(),
        categories = mutableListOf()
    )
    categories.apply(init)
    return categories
}

fun Categories.common(init: Common.() -> Unit = {}) = common.apply(init)

fun Categories.farms(init: FarmCategory.() -> Unit) {
    val farm = FarmCategory()
    farm.init()
    this.add(farm)
}

fun Categories.storage(init: Category.() -> Unit): Unit = category(CategoryType.STORAGE, init)
fun Categories.cartTech(init: Category.() -> Unit) = category(CategoryType.CART_TECH, init)
fun Categories.tntTech(init: Category.() -> Unit) = category(CategoryType.TNT_TECH, init)
fun Categories.slimestone(init: Category.() -> Unit) = category(CategoryType.SLIMESTONE, init)
fun Categories.other(init: Category.() -> Unit) = category(CategoryType.OTHER, init)

fun Categories.category(type: CategoryType, init: Category.() -> Unit) {
    val category = Category(type)
    category.apply(init)
    this.add(category)
}
