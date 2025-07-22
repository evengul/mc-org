package app.mcorg.domain.model.task

sealed interface TaskRequirement {
    fun isCompleted(): Boolean

    companion object {
        fun item(item: String, requiredAmount: Int): ItemRequirement {
            return ItemRequirement.create(item, requiredAmount)
        }

        fun action(action: String): ActionRequirement {
            return ActionRequirement.create(action)
        }
    }
}

data class ItemRequirement(
    val item: String,
    val requiredAmount: Int,
    val collected: Int
) : TaskRequirement {
    override fun isCompleted(): Boolean {
        return collected >= requiredAmount
    }

    companion object {
        fun create(item: String, requiredAmount: Int): ItemRequirement {
            return ItemRequirement(item, requiredAmount, 0)
        }
    }
}

data class ActionRequirement(
    val action: String,
    val completed: Boolean
) : TaskRequirement {
    override fun isCompleted(): Boolean {
        return completed
    }

    companion object {
        fun create(action: String): ActionRequirement {
            return ActionRequirement(action, false)
        }
    }
}