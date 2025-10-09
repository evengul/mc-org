package app.mcorg.domain.model.task

sealed interface TaskRequirement {
    val id: Int
    fun isCompleted(): Boolean

    fun title(): String {
        return when (this) {
            is ItemRequirement -> item
            is ActionRequirement -> action
        }
    }

    companion object {
        fun item(id: Int, item: String, requiredAmount: Int): ItemRequirement {
            return ItemRequirement.create(id, item, requiredAmount)
        }

        fun action(id: Int, action: String): ActionRequirement {
            return ActionRequirement.create(id, action)
        }
    }
}

data class ItemRequirement(
    override val id: Int,
    val item: String,
    val requiredAmount: Int,
    val collected: Int
) : TaskRequirement {
    override fun isCompleted(): Boolean {
        return collected >= requiredAmount
    }

    companion object {
        fun create(id: Int, item: String, requiredAmount: Int): ItemRequirement {
            return ItemRequirement(id, item, requiredAmount, 0)
        }
    }
}

data class ActionRequirement(
    override val id: Int,
    val action: String,
    val completed: Boolean
) : TaskRequirement {
    override fun isCompleted(): Boolean {
        return completed
    }

    companion object {
        fun create(id: Int, action: String): ActionRequirement {
            return ActionRequirement(id, action, false)
        }
    }
}