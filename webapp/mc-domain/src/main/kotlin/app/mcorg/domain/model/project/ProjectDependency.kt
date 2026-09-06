package app.mcorg.domain.model.project

/**
 * Where a declared project dependency came from (MCO-302).
 *
 * Display only. It exists so a row can carry an "Imported" badge and nothing else — no ordering,
 * layering or cycle-breaking rule reads it, and none should start without its own reason.
 * `RoadmapCycles` already treats every declared row as never-the-guess, which is a claim about
 * the row being *declared*, not about who declared it.
 */
enum class DependencyOrigin {
    /** Someone asserted this ordering in the dependency editor. */
    MANUAL,

    /** An idea import asserted it — `ImportIdeaPipeline`, the only writer before MCO-302. */
    IMPORTED,
}

data class ProjectDependency(
    val dependentId: Int,
    val dependentName: String,
    val dependentStage: ProjectStage,
    val dependencyId: Int,
    val dependencyName: String,
    val dependencyStage: ProjectStage,
    val origin: DependencyOrigin = DependencyOrigin.MANUAL,
)
