package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-400 — "Needs attention", ordered by what each question decides.
 *
 * The section used to render every question as an equal amber callout in id order. On the YAMS
 * import that opened with "Charcoal or Coal" (2 items) and buried "Planks" (110,824 — 96% of all
 * the material behind these questions) at position 19. These tests pin the ordering, the fold,
 * and the two things that must never be folded.
 */
class NeedsAttentionSectionTest {

    private fun tag(id: String, name: String) = MinecraftTag("#minecraft:$id", name, emptyList())

    private val planks = tag("planks", "Planks")
    private val woodenSlabs = tag("wooden_slabs", "Wooden Slabs")
    private val coals = tag("coals", "Coals")
    private val oakLogs = tag("oak_logs", "Oak Logs")
    private val logs = tag("logs", "Logs")
    private val netherPortal = Item("minecraft:nether_portal", "Nether Portal")
    private val oakLog = Item("minecraft:oak_log", "Oak Log")

    private fun project() = Project(
        id = 2,
        worldId = 1,
        name = "Storage System",
        description = "",
        type = ProjectType.BUILDING,
        stage = ProjectStage.PLANNING,
        state = ProjectState.ACTIVE,
        location = null,
        tasksTotal = 0,
        tasksCompleted = 0,
        importedFromIdea = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    private fun plan(vararg nodes: PlanNode) = GatheringPlan(
        nodes = nodes.associateBy { it.item.id },
        targets = nodes.map { PlanTarget(it.item, it.quantity) },
    )

    private fun question(item: MinecraftId, quantity: Long) =
        PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = PlanNodeStatus.OPEN_TAG)

    private fun gather(item: MinecraftId, quantity: Long) =
        PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = PlanNodeStatus.RAW_GATHER)

    private fun blocked(item: MinecraftId, quantity: Long) =
        PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = PlanNodeStatus.BLOCKED)

    private fun render(plan: GatheringPlan) = gatheringPlannerFragment(
        project = project(),
        resources = emptyList(),
        tasks = emptyList(),
        plan = plan,
    )

    /**
     * Everything in the section before the fold — what someone actually sees on arriving.
     * Anchored on the section label, not the questions: blocked rows render ahead of them.
     */
    private fun visiblePart(html: String): String {
        val start = html.indexOf("Needs attention")
        val fold = html.indexOf("plan-attention__rest", start + 1)
        return if (fold > 0) html.substring(start, fold) else html.substring(start)
    }

    @Test
    fun `the question deciding the most material leads`() {
        val html = render(
            plan(
                question(coals, 1),
                question(planks, 110_824),
                question(woodenSlabs, 3_540),
                question(oakLogs, 46),
                question(logs, 10),
            )
        )

        assertContains(visiblePart(html), "Planks")
        assertFalse(visiblePart(html).contains("Coals"))
    }

    @Test
    fun `each question carries its quantity`() {
        // Without the number every question looks alike, which is the whole failure: on a real
        // import two of them are four orders of magnitude apart.
        val html = render(plan(question(planks, 110_824)))

        assertContains(html, "110,824")
        assertContains(html, "plan-attention__quantity")
    }

    @Test
    fun `the lead says how much of the material the visible questions decide`() {
        val html = render(
            plan(
                question(planks, 110_824),
                question(woodenSlabs, 3_540),
                question(coals, 1),
                question(oakLogs, 46),
                question(logs, 10),
            )
        )

        assertContains(
            html,
            "5 questions to answer — the first decides 97% of the material behind them. " +
                "The plan below is provisional until they are answered.",
        )
    }

    @Test
    fun `a small set is not folded at all`() {
        // Hiding one or two questions behind a toggle costs a click and saves nothing.
        val html = render(plan(question(planks, 110_824), question(coals, 1)))

        assertContains(
            html,
            "2 questions to answer. The plan below is provisional until they are answered.",
        )
        assertFalse(html.contains("plan-attention__rest"))
    }

    @Test
    fun `a flat distribution still leads with at most five`() {
        // Coverage alone would expand nearly everything when no single question dominates —
        // which is the wall again, just sorted.
        val nodes = (1..12).map { question(tag("filler_$it", "Filler $it"), 1_000L) }
        val html = render(plan(*nodes.toTypedArray()))

        assertContains(html, "Show 7 smaller choices")
    }

    @Test
    fun `blocked rows are never folded away`() {
        // They cannot be answered by picking a variant at all, so no amount of choosing clears
        // them — hiding them behind a "smaller choices" toggle would misfile them as trivial.
        val nodes = (1..12).map { question(tag("filler_$it", "Filler $it"), 1_000L) } + blocked(netherPortal, 2)
        val html = render(plan(*nodes.toTypedArray()))

        assertContains(visiblePart(html), "Blocked: ")
        assertContains(visiblePart(html), "Nether Portal")
    }

    @Test
    fun `a section of only blocked rows has no lead line or toggle`() {
        val html = render(plan(blocked(netherPortal, 2)))

        assertContains(html, "Nether Portal")
        assertFalse(html.contains("variant choices"))
        assertFalse(html.contains("plan-attention__rest"))
    }

    @Test
    fun `every question is still present, folded or not`() {
        // Ordering and folding, never dropping — a hidden question is still a question.
        val html = render(
            plan(
                question(planks, 110_824),
                question(woodenSlabs, 3_540),
                question(coals, 1),
                question(oakLogs, 46),
                question(logs, 10),
            )
        )

        listOf("Planks", "Wooden Slabs", "Coals", "Oak Logs", "Logs").forEach {
            assertContains(html, it)
        }
        assertTrue(html.contains("Show 4 smaller choices"))
    }

    // ---- MCO-504: the question, and when Next up is allowed to speak --------------------

    /**
     * The row used to end in "- Pick a variant (open tag)". `open tag` is a `PlanNodeStatus`
     * name; it had leaked from the engine onto the page and meant nothing to a player. MCO-489
     * made the label name the options; this makes the row name the question.
     */
    @Test
    fun `a question states what it is asking, without engine vocabulary`() {
        val html = render(plan(question(planks, 110_824)))

        assertContains(html, "Which should the plan use in recipes?")
        assertFalse(html.contains("open tag"), "PlanNodeStatus vocabulary must not reach the page")
        assertFalse(html.contains("Pick a variant"), "the row states a question, not an instruction")
    }

    /**
     * Even, reviewing round 3: "what's next is mostly relevant AFTER the questions have been
     * answered. When those questions are there, they are the most important thing, and now
     * they're asked in two different places."
     *
     * Not only duplication - the widget's advice is provisional while a question is open, since
     * answering one redirects the tag to a member and merges its demand, so the largest
     * remaining pile can change under it.
     */
    @Test
    fun `Next up is silent while a question could change its answer`() {
        // 110,824 planks against 27,763 logs: answering the question merges that demand and can
        // dethrone the top pick, so the widget has nothing trustworthy to say yet.
        val html = render(plan(question(planks, 110_824), gather(oakLog, 27_763)))

        assertFalse(html.contains("NEXT UP"), "the questions are the page's business until answered")
        assertContains(html, "Which should the plan use in recipes?")
    }

    /**
     * The first cut of the gate suppressed the widget for *any* open question, and on the real
     * YAMS plan that meant a 4-item choice between red sand and sand hid it on a build of
     * 400,000 items. The test is not a threshold: a question can only change what Next up claims
     * — the largest outstanding work — if the material it decides could exceed it.
     */
    @Test
    fun `a question far smaller than the top pick does not silence Next up`() {
        val html = render(plan(question(coals, 3), gather(oakLog, 27_763)))

        assertContains(html, "NEXT UP")
        assertContains(html, "Which should the plan use in recipes?")
    }

    @Test
    fun `Next up speaks once every question is answered`() {
        val html = render(plan(gather(oakLog, 27_763)))

        assertContains(html, "NEXT UP")
        assertContains(html, "Oak Log")
    }

    /**
     * A BLOCKED node also needs the user, but it does not make the rest of the plan provisional
     * - its chain is known, it simply has no source at any price. So work still gets pointed at.
     */
    @Test
    fun `a blocked row does not silence Next up`() {
        val html = render(plan(blocked(netherPortal, 1), gather(oakLog, 27_763)))

        assertContains(html, "NEXT UP")
    }
}
