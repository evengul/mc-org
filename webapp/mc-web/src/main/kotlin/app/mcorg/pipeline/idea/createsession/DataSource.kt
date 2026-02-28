package app.mcorg.pipeline.idea.createsession

import kotlinx.serialization.Serializable

@Serializable
enum class DataSource {
    MANUAL_ENTRY,
    LITEMATICA_UPLOAD
}