package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.editLocation
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetEditLocationFragment() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                editLocation(worldId, projectId, it)
            })
        },
    ) {
        value(projectId)
            .step(GetLocationStep)
    }
}

private val GetLocationStep = DatabaseSteps.query<Int, MinecraftLocation>(
    sql = SafeSQL.select("SELECT location_x, location_y, location_z, location_dimension FROM projects WHERE id = ?"),
    parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
    resultMapper = { rs ->
        if (rs.next()) {
            val x = rs.getInt("location_x")
            val y = rs.getInt("location_y")
            val z = rs.getInt("location_z")
            val dimensionStr = rs.getString("location_dimension")
            val dimension = if (dimensionStr != null) {
                try {
                    Dimension.valueOf(dimensionStr)
                } catch (_: IllegalArgumentException) {
                    Dimension.OVERWORLD
                }
            } else {
                null
            }
            if (dimension != null) {
                MinecraftLocation(dimension, x, y, z)
            } else {
                MinecraftLocation(Dimension.OVERWORLD, x, y, z)
            }
        } else {
            MinecraftLocation(Dimension.OVERWORLD, 0, 0, 0)
        }
    }
)