package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.model.project.ImportedIdea
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.test.fixtures.TestDataFactory
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * MCO-61 — the project page links back to the idea it was imported from.
 *
 * Ideas are private by default (MCO-291), and a project is seen by its whole world while the idea
 * behind it may be seen only by whoever imported it. The link is therefore drawn only for a viewer
 * the idea route would let in; for anyone else there is no link and no name.
 */
class ImportedFromIdeaLinkTest {

    private val creatorId = 1
    private val creator = TestDataFactory.createTestTokenProfile(id = creatorId)
    private val worldMate = TestDataFactory.createTestTokenProfile(id = 2, uuid = "00000000-0000-0000-0000-000000000002")
    private val superAdmin = TestDataFactory.createTestTokenProfile(
        id = 3, uuid = "00000000-0000-0000-0000-000000000003", roles = listOf("superadmin")
    )

    private fun project(importedFromIdea: ImportedIdea?) = Project(
        id = 2,
        worldId = 1,
        name = "Iron Farm",
        description = "",
        type = ProjectType.BUILDING,
        stage = ProjectStage.PLANNING,
        state = ProjectState.ACTIVE,
        location = null,
        tasksTotal = 0,
        tasksCompleted = 0,
        importedFromIdea = importedFromIdea,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    private fun idea(visibility: IdeaVisibility) =
        ImportedIdea(id = 7, name = "Trident Killer Golem Farm", visibility = visibility, createdBy = creatorId)

    private fun render(project: Project, viewer: TokenProfile) = projectDetailPage(
        user = viewer,
        project = project,
        worldName = "Survival",
        resources = emptyList(),
        tasks = emptyList(),
    )

    @Test
    fun `a public idea is linked for any world member`() {
        val html = render(project(idea(IdeaVisibility.PUBLIC)), worldMate)

        assertContains(html, "href=\"/ideas/7\"")
        assertContains(html, "Trident Killer Golem Farm")
    }

    @Test
    fun `a private idea is linked for the member who imported it`() {
        val html = render(project(idea(IdeaVisibility.PRIVATE)), creator)

        assertContains(html, "href=\"/ideas/7\"")
    }

    @Test
    fun `a private idea is linked for a superadmin`() {
        val html = render(project(idea(IdeaVisibility.PRIVATE)), superAdmin)

        assertContains(html, "href=\"/ideas/7\"")
    }

    @Test
    fun `a private idea is neither linked nor named for another world member`() {
        val html = render(project(idea(IdeaVisibility.PRIVATE)), worldMate)

        assertFalse(html.contains("/ideas/7"), "a link the idea route would answer with 404")
        assertFalse(html.contains("Trident Killer Golem Farm"), "the name of someone else's private idea")
        assertFalse(html.contains("project-detail__idea"))
    }

    @Test
    fun `a project not imported from an idea shows no link`() {
        val html = render(project(importedFromIdea = null), creator)

        assertFalse(html.contains("project-detail__idea"))
        assertFalse(html.contains("/ideas/"))
    }
}
