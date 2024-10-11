package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.value.*

class FarmCategory : Category(CategoryType.FARM, subCategories = mutableListOf())

fun FarmCategory.rates(init: RateModes.() -> Unit = {}) {
    val rateModes = RateModes("farm.rates", "Rates")
    rateModes.init()
    values.add(rateModes)
}

fun FarmCategory.consumption(init: RateModes.() -> Unit = {}) {
    val rateModes = RateModes("farm.consumption", "Consumption")
    rateModes.init()
    values.add(rateModes)
}

fun FarmCategory.size(init: FarmSizeValue.() -> Unit = {}) {
    val farmSize = FarmSizeValue()
    farmSize.init()
    values.add(farmSize)
}

fun FarmCategory.mobs(init: MobsValue.() -> Unit = {}) {
    val mobs = MobsValue()
    mobs.init()
    values.add(mobs)
}
