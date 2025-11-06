package app.mcorg.presentation.utils

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.templated.common.breadcrumb.BreadcrumbItem
import app.mcorg.presentation.templated.common.breadcrumb.Breadcrumbs
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link

/**
 * Helper functions to fetch entity names from database for breadcrumb construction
 */
suspend fun getWorldName(worldId: Int): String {
    return DatabaseSteps.query<Int, String?>(
        SafeSQL.select("SELECT name FROM world WHERE id = ?"),
        parameterSetter = { ps, id -> ps.setInt(1, id) },
        resultMapper = {
            if (it.next()) {
                it.getString("name")
            } else {
                null
            }
        }
    ).process(worldId).getOrNull() ?: "World #$worldId"
}

suspend fun getProjectName(projectId: Int): String {
    return DatabaseSteps.query<Int, String?>(
        SafeSQL.select("SELECT name FROM projects WHERE id = ?"),
        { ps, id -> ps.setInt(1, id) },
        resultMapper = { rs ->
            if (rs.next()) {
                rs.getString("name")
            } else {
                null
            }
        }
    ).process(projectId).getOrNull() ?: "Project #$projectId"
}

suspend fun getIdeaName(ideaId: Int): String {
    return DatabaseSteps.query<Int, String?>(
        sql = SafeSQL.select("SELECT name FROM ideas WHERE id = ?"),
        parameterSetter = { ps, id -> ps.setInt(1, id) },
        resultMapper = { rs ->
            if (rs.next()) {
                rs.getString("name")
            } else {
                null
            }
        }
    ).process(ideaId).getOrNull() ?: "Idea #$ideaId"
}

/**
 * Builder object to construct breadcrumb trails for different page types
 */
@Suppress("unused")
object BreadcrumbBuilder {

    /**
     * Build breadcrumbs for home page
     */
    fun buildForHome(): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", null, Icons.HOME)
        ))
    }

    /**
     * Build breadcrumbs for worlds list page
     */
    fun buildForWorldsList(): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Worlds", null, Icons.Menu.PROJECTS)
        ))
    }

    /**
     * Build breadcrumbs for a specific world page
     */
    fun buildForWorld(world: World): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(world.name, null, Icons.Dimensions.OVERWORLD)
        ))
    }

    /**
     * Build breadcrumbs for a specific world page (when only ID is available)
     */
    suspend fun buildForWorld(worldId: Int): Breadcrumbs {
        val worldName = getWorldName(worldId)

        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(worldName, null, Icons.Dimensions.OVERWORLD)
        ))
    }

    /**
     * Build breadcrumbs for a project page
     */
    fun buildForProject(world: World, project: Project): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(world.name, Link.Worlds.world(world.id), Icons.Dimensions.OVERWORLD),
            BreadcrumbItem(project.name, null, Icons.Menu.PROJECTS)
        ))
    }

    /**
     * Build breadcrumbs for a project page (when world name is already available)
     */
    fun buildForProject(worldId: Int, worldName: String, project: Project): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(worldName, Link.Worlds.world(worldId), Icons.Dimensions.OVERWORLD),
            BreadcrumbItem(project.name, null, Icons.Menu.PROJECTS)
        ))
    }

    suspend fun buildForProject(worldId: Int, projectName: String): Breadcrumbs {
        val worldName = getWorldName(worldId)
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(worldName, Link.Worlds.world(worldId), Icons.Dimensions.OVERWORLD),
            BreadcrumbItem(projectName, null, Icons.Menu.PROJECTS)
        ))
    }

    /**
     * Build breadcrumbs for a project page (when only IDs are available)
     */
    suspend fun buildForProject(worldId: Int, projectId: Int): Breadcrumbs {
        val worldName = getWorldName(worldId)
        val projectName = getProjectName(projectId)

        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(worldName, Link.Worlds.world(worldId), Icons.Dimensions.OVERWORLD),
            BreadcrumbItem(projectName, null, Icons.Menu.PROJECTS)
        ))
    }

    /**
     * Build breadcrumbs for world settings page
     */
    fun buildForWorldSettings(world: World): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(world.name, Link.Worlds.world(world.id), Icons.Dimensions.OVERWORLD),
            BreadcrumbItem("Settings", null, Icons.Menu.UTILITIES)
        ))
    }

    /**
     * Build breadcrumbs for world settings page (when only ID is available)
     */
    suspend fun buildForWorldSettings(worldId: Int): Breadcrumbs {
        val worldName = getWorldName(worldId)

        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem(worldName, Link.Worlds.world(worldId), Icons.Dimensions.OVERWORLD),
            BreadcrumbItem("Settings", null, Icons.Menu.UTILITIES)
        ))
    }

    /**
     * Build breadcrumbs for ideas list page
     */
    fun buildForIdeasList(): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Ideas", null, Icons.Menu.VOLCANO)
        ))
    }

    /**
     * Build breadcrumbs for a specific idea page
     */
    fun buildForIdea(idea: Idea): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Ideas", Link.Ideas, Icons.Menu.VOLCANO),
            BreadcrumbItem(idea.name, null, Icons.Menu.VOLCANO)
        ))
    }

    /**
     * Build breadcrumbs for a specific idea page (when only ID is available)
     */
    suspend fun buildForIdea(ideaId: Int): Breadcrumbs {
        val ideaName = getIdeaName(ideaId)

        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Ideas", Link.Ideas, Icons.Menu.VOLCANO),
            BreadcrumbItem(ideaName, null, Icons.Menu.VOLCANO)
        ))
    }

    /**
     * Build breadcrumbs for profile page
     */
    fun buildForProfile(): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Profile", null, Icons.USER_PROFILE)
        ))
    }

    /**
     * Build breadcrumbs for notifications page
     */
    fun buildForNotifications(): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Notifications", null, Icons.Notification.INFO)
        ))
    }

    fun buildForAdminPage(): Breadcrumbs {
        return Breadcrumbs(listOf(
            BreadcrumbItem("Home", Link.Home, Icons.HOME),
            BreadcrumbItem("Admin", null, Icons.Menu.UTILITIES)
        ))
    }
}

