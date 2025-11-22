package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.extractors.deserializeVersionRange
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.hxSwap
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.id
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetSelectWorldForIdeaImportFragment() {
    val userId = this.getUser().id
    val ideaId = this.getIdeaId()

    executePipeline(
        onSuccess = { worldsList ->
            respondHtml(createHTML().select {
                id = "import-idea-selector"
                name = "worldId"

                hxPost(Link.Ideas.single(ideaId) + "/import")
                hxTarget("#import-idea-selector")
                hxSwap("outerHTML")
                hxTrigger("change")
                hxInclude("#import-idea-selector")

                option {
                    value = ""
                    if (worldsList.isEmpty()) {
                        disabled = true
                        + "No compatible worlds available"
                    } else {
                        + "Select a world"
                    }
                }
                for ((worldId, worldName) in worldsList) {
                    option {
                        value = worldId.toString()
                        +worldName
                    }
                }
            })
        }
    ) {
        value(ideaId)
            .step(GetIdeaVersionRangeStep)
            .value { it to userId }
            .step(GetPermittedWorldsStep)
    }
}

private val GetIdeaVersionRangeStep = DatabaseSteps.query<Int, MinecraftVersionRange>(
    sql = SafeSQL.select("SELECT minecraft_version_range from ideas WHERE id = ?"),
    parameterSetter = { statement, ideaId -> statement.setInt(1, ideaId) },
    resultMapper = {
        if (it.next()) {
            val rangeString = it.getString("minecraft_version_range")
            deserializeVersionRange(rangeString)
        } else {
            MinecraftVersionRange.Unbounded
        }
    }
)

private object GetPermittedWorldsStep : Step<Pair<MinecraftVersionRange, Int>, AppFailure.DatabaseError, List<Pair<Int, String>>> {
    override suspend fun process(input: Pair<MinecraftVersionRange, Int>): Result<AppFailure.DatabaseError, List<Pair<Int, String>>> {
        return DatabaseSteps.query<Pair<MinecraftVersionRange, Int>, List<Pair<Int, String>>>(
            sql = SafeSQL.select("SELECT world.id, world.name, world.version from world JOIN world_members on world.id = world_members.world_id WHERE world_members.user_id = ? AND world_members.world_role <= 10"),
            parameterSetter = { statement, (_, userId) -> statement.setInt(1, userId) },
            resultMapper = {
                buildList {
                    while (it.next()) {
                        val worldId = it.getInt("id")
                        val worldName = it.getString("name")
                        val version = MinecraftVersion.fromString(it.getString("version"))
                        if (input.first.withinBounds(version)) {
                            add(Pair(worldId, worldName))
                        }
                    }
                }
            }
        ).process(input)
    }
}
