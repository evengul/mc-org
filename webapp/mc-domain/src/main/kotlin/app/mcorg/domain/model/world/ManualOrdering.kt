package app.mcorg.domain.model.world

import app.mcorg.domain.model.project.ProjectState

/**
 * An ordering somebody asserted by hand: `first` has to happen before `then`, and no material
 * passes between them (MCO-302).
 *
 * The roadmap is derived — an edge exists because a farm produces what a project needs, and
 * nobody types it in. This is the one exception, and it is deliberately narrow: "dig the
 * perimeter before you build the walls" is a real constraint that carries no items, so no
 * amount of deriving will ever find it. A row here must never be a way to fake a resource
 * relationship; that is what `resource_gathering.solved_by_project_id` is for.
 *
 * Field names say the *order*, not the roles. "Dependency" and "dependent" are a coin flip
 * under pressure, and reversing them silently inverts the graph with no feedback — which is
 * why the form labels its two pickers `DO THIS FIRST` and `BEFORE THIS` too.
 *
 * In the table, [firstProjectId] is `depends_on_project_id` and [thenProjectId] is
 * `project_id`: the row reads "then depends on first".
 */
data class ManualOrdering(
    val id: Int,
    val firstProjectId: Int,
    val firstProjectName: String,
    val thenProjectId: Int,
    val thenProjectName: String,
    /**
     * Why the ordering exists, in the author's words. Required of anything the editor writes
     * and absent from everything the idea import wrote — see [declaredBy].
     */
    val reason: String?,
    val declaredBy: OrderingSource,
)

/**
 * Which surface asserted an ordering.
 *
 * [IDEA_IMPORT] rows predate the editor and carry no reason. They are shown and removable
 * exactly like the others — an import that could assert an ordering the application could not
 * take back is the bug MCO-302 exists to fix.
 */
enum class OrderingSource {
    EDITOR,
    IDEA_IMPORT;

    companion object {
        fun fromDb(value: String): OrderingSource =
            entries.firstOrNull { it.name == value } ?: IDEA_IMPORT
    }
}

/**
 * One row of a combobox's results panel: a project you might pick, and everything needed to
 * decide whether it is the one you meant.
 *
 * [state] is on the row because a world can hold both *Slime farm* and *New slime farm*, and
 * picking the wrong one is undetectable afterwards.
 *
 * [wouldCycle] is why the search can offer every project in the world and still be safe. The
 * old candidate query made cycles unofferable by *excluding* them, which works only while the
 * list is a closed dropdown; a search box that matches on name has to say why a row is there
 * and not pickable. Marking at pick time also beats refusing on submit — it answers "which
 * end do I reconsider" before the reason has been typed.
 */
data class OrderingCandidate(
    val projectId: Int,
    val name: String,
    val state: ProjectState,
    val wouldCycle: Boolean,
)
