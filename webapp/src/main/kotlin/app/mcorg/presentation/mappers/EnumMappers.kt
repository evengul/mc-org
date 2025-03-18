package app.mcorg.presentation.mappers

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority

fun EnumMappers.Companion.mapDimension(dimension: String?): Dimension = when(dimension) {
    "OVERWORLD" -> Dimension.OVERWORLD
    "NETHER" -> Dimension.NETHER
    "THE_END" -> Dimension.THE_END
    else -> Dimension.OVERWORLD
}

fun EnumMappers.Companion.mapPriority(priority: String?): Priority = when(priority) {
    "LOW" -> Priority.LOW
    "MEDIUM" -> Priority.MEDIUM
    "HIGH" -> Priority.HIGH
    else -> Priority.NONE
}