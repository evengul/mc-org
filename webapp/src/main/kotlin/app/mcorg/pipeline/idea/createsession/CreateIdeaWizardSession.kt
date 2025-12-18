package app.mcorg.pipeline.idea.createsession

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.idea.createwizard.CreateIdeaStage
import kotlinx.serialization.Serializable

@Serializable
data class CreateIdeaWizardSession(
    val userId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),

    val dataSource: Map<WizardField, DataSource> = emptyMap(),

    val currentStage: CreateIdeaStage = CreateIdeaStage.BASIC_INFO,
    val name: String? = null,
    val description: String? = null,
    val difficulty: IdeaDifficulty? = null,
    val author: Author? = null,
    val versionRange: MinecraftVersionRange? = null,

    val itemRequirements: Map<String, Int>? = null,

    val category: IdeaCategory? = null,
    val categoryData: Map<String, CategoryValue>? = null,

    val litematicaFileName: String? = null,
    val litematicaUploadedAt: Long? = null,
)