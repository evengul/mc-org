package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.value.*

@CategoryMarker
class FarmCategory : Category(CategoryType.FARM, subCategories = mutableListOf())

fun FarmCategory.rates(init: RateModes.() -> Unit = {}) {
    val rateModes = RateModes("farm.rates", "Rates")
    rateModes.init()
    filter(rateModes)
}

fun FarmCategory.consumption(init: RateModes.() -> Unit = {}) {
    val rateModes = RateModes("farm.consumption", "Consumption")
    rateModes.init()
    filter(rateModes)
}

fun FarmCategory.size(init: FarmSize.() -> Unit = {}) {
    val farmSize = FarmSize()
    farmSize.init()
    filter(farmSize)
}

fun FarmCategory.mobs(init: Mobs.() -> Unit = {}) {
    val mobs = Mobs()
    mobs.init()
    filter(mobs)
}
