package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.idea.validators.*
import io.ktor.http.*
import kotlinx.html.emptyMap

data class CreateIdeaWizardData(
    val stage: CreateIdeaStage = CreateIdeaStage.BASIC_INFO,
    val name: String? = null,
    val description: String? = null,
    val difficulty: IdeaDifficulty? = null,

    val author: Author? = null,
    val versionRange: MinecraftVersionRange? = null,

    val categoryData: Pair<IdeaCategory, Map<String, Any?>>? = null
)

suspend fun Parameters.toCreateIdeaDataHolder(currentUsername: String, supportedVersions: List<MinecraftVersion.Release>): CreateIdeaWizardData {
    val stage = this["stage"]?.let {
        try {
            CreateIdeaStage.valueOf(it.uppercase())
        } catch (_: IllegalArgumentException) {
            CreateIdeaStage.BASIC_INFO
        }
    } ?: CreateIdeaStage.BASIC_INFO

    val name = ValidateIdeaNameStep.process(this)
    val description = ValidateIdeaDescriptionStep.process(this)
    val difficulty = ValidateIdeaDifficultyStep.process(this)
    val category = ValidateIdeaCategoryStep.process(this)
    val author = ValidateIdeaAuthorStep.process(this).recover {
        when (this["authorType"]) {
            "single" -> Result.success(Author.SingleAuthor(currentUsername))
            "team" -> Result.success(Author.Team(emptyList()))
            else -> Result.failure(it)
        }
    }
    val versionRange = ValidateIdeaMinecraftVersionStep.process(this).recover {
        when (this["versionRangeType"]) {
            "bounded" -> Result.success(MinecraftVersionRange.Bounded(
                supportedVersions.sortedWith { a, b -> b.compareTo(a) }.first(),
                supportedVersions.sortedWith { a, b -> a.compareTo(b) }.first()
            ))
            "lowerBounded" -> Result.success(MinecraftVersionRange.LowerBounded(
                supportedVersions.sortedWith { a, b -> b.compareTo(a) }.first()
            ))
            "upperBounded" -> Result.success(MinecraftVersionRange.UpperBounded(
                supportedVersions.sortedWith { a, b -> a.compareTo(b) }.first()
            ))
            "unbounded" -> Result.success(MinecraftVersionRange.Unbounded)
            else -> Result.success(MinecraftVersionRange.Unbounded)
        }
    }

    val categoryData = category.getOrNull()?.let {
        val result = ValidateIdeaCategoryDataStep(it).process(this)
        if (result is Result.Success) {
            it to result.value
        } else {
            it to emptyMap
        }
    }

    return CreateIdeaWizardData(
        stage = stage,
        name = name.getOrNull(),
        description = description.getOrNull(),
        difficulty = difficulty.getOrNull(),
        author = author.getOrNull(),
        versionRange = versionRange.getOrNull(),
        categoryData = categoryData
    )
}