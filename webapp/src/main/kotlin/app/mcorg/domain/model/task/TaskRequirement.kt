package app.mcorg.domain.model.task

sealed interface TaskRequirement {
    fun isCompleted(): Boolean
}

data class ItemRequirement(
    val itemId: String,
    val requiredAmount: Int,
    val collected: Int
) : TaskRequirement {
    override fun isCompleted(): Boolean {
        return collected >= requiredAmount
    }
}

data class ActionRequirement(
    val completed: Boolean
) : TaskRequirement {
    override fun isCompleted(): Boolean {
        return completed
    }
}