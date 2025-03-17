package app.mcorg.presentation.mappers

import app.mcorg.domain.minecraft.Dimension
import app.mcorg.domain.projects.Priority


fun String?.toDimension(): Dimension = when(this) {
    "OVERWORLD" -> Dimension.OVERWORLD
    "NETHER" -> Dimension.NETHER
    "THE_END" -> Dimension.THE_END
    else -> Dimension.OVERWORLD
}

fun String?.toPriority(): Priority = when(this) {
    "LOW" -> Priority.LOW
    "MEDIUM" -> Priority.MEDIUM
    "HIGH" -> Priority.HIGH
    else -> Priority.NONE
}