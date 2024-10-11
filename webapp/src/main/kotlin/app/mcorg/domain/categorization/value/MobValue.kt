package app.mcorg.domain.categorization.value

data class MobsValue(var mobs: MutableList<MobValue> = mutableListOf()) : CategoryValue {
    override val id: String
        get() = "farm.required-mobs"
    override val name: String
        get() = "Required mobs"
}
data class MobValue(val name: String, val amount: Int)
