package app.mcorg.pipeline.minecraftfiles

import kotlinx.serialization.Serializable

@Serializable
internal data class VersionManifest(
    val latest: Latest,
    val versions: List<ManifestEntry>
) {
    @Serializable
    data class Latest(val release: String, val snapshot: String)

    @Serializable
    data class ManifestEntry(
        val id: String,
        val type: String,
        val url: String
    )
}

@Serializable
internal data class VersionMeta(val downloads: Downloads) {
    @Serializable
    data class Downloads(val server: ServerDownload? = null)

    @Serializable
    data class ServerDownload(val url: String)
}
