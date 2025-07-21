package app.mcorg.domain.model.minecraft

sealed interface MinecraftVersion {
    data class Snapshot(
        val year: Int,
        val week: Int,
        val patch: Char,
        val forRelease: Release? = null
    ) : MinecraftVersion {
        override fun toString(): String = "${year}w${week}${patch.lowercase()}"
    }

    data class Release(
        val major: Int = 1,
        val minor: Int,
        val patch: Int
    ) : MinecraftVersion {
        override fun toString(): String = "$major.$minor.$patch"
    }

    operator fun compareTo(other: MinecraftVersion): Int {
        return when {
            this is Snapshot && other is Snapshot -> compareSnapshots(this, other)
            this is Release && other is Release -> compareReleases(this, other)
            this is Snapshot && other is Release -> {
                if (this.forRelease != null) {
                    // If the snapshot is for a specific release, compare it to that release
                    return compareReleases(this.forRelease, other)
                }
                return -1 // Snapshots are considered less than releases
            }
            this is Release && other is Snapshot -> {
                if (other.forRelease != null) {
                    // If the snapshot is for a specific release, compare it to that release
                    return compareReleases(this, other.forRelease)
                }
                return 1 // Releases are considered greater than snapshots
            }
            else -> 1 // Releases are considered greater than snapshots
        }
    }

    private fun compareSnapshots(snap: Snapshot, other: Snapshot): Int {
        return when {
            snap.year != other.year -> snap.year.compareTo(other.year)
            snap.week != other.week -> snap.week.compareTo(other.week)
            snap.patch != other.patch -> snap.patch.lowercase().compareTo(other.patch.lowercase())
            else -> 0
        }
    }

    private fun compareReleases(release: Release, other: Release): Int {
        return when {
            release.major != other.major -> release.major.compareTo(other.major)
            release.minor != other.minor -> release.minor.compareTo(other.minor)
            release.patch != other.patch -> release.patch.compareTo(other.patch)
            else -> 0
        }
    }

    companion object {
        fun fromString(version: String): MinecraftVersion {
            return when {
                version.matches(Regex("""\d+w\d+[a-zA-Z]""")) -> parseSnapshot(version)
                version.matches(Regex("""\d+\.\d+\.\d+""")) -> parseRelease(version)
                else -> throw IllegalArgumentException("Invalid version format: $version")
            }
        }

        private fun parseSnapshot(version: String): Snapshot {
            val regex = Regex("""(\d+)w(\d+)([a-zA-Z])""")
            val matchResult = regex.matchEntire(version)
                ?: throw IllegalArgumentException("Invalid snapshot format: $version")

            val (year, week, patch) = matchResult.destructured
            return Snapshot(
                year = year.toInt(),
                week = week.toInt(),
                patch = patch.first().lowercaseChar()
            )
        }

        private fun parseRelease(version: String): Release {
            val parts = version.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid release format: $version")
            }

            return Release(
                major = parts[0].toInt(),
                minor = parts[1].toInt(),
                patch = parts[2].toInt()
            )
        }

        fun snapshot(year: Int, week: Int, patch: Char, forRelease: Release? = null): Snapshot {
            return Snapshot(year, week, patch, forRelease)
        }

        fun release(minor: Int, patch: Int): Release {
            return Release(minor = minor, patch = patch)
        }

        val supportedVersions = setOf<MinecraftVersion>(
            release(20, 0)
        )
    }
}

sealed interface MinecraftVersionRange {
    object Unbounded : MinecraftVersionRange
    data class Bounded(
        val from: MinecraftVersion,
        val to: MinecraftVersion
    ) : MinecraftVersionRange
    data class UpperBounded(
        val to: MinecraftVersion
    ) : MinecraftVersionRange
    data class LowerBounded(
        val from: MinecraftVersion
    ) : MinecraftVersionRange

    fun withinBounds(version: MinecraftVersion): Boolean {
        return when (this) {
            is Unbounded -> true
            is Bounded -> version >= from && version <= to
            is UpperBounded -> version <= to
            is LowerBounded -> version >= from
        }
    }
}
