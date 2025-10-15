package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.RatingSummary
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import java.time.ZonedDateTime

object IdeaMockData {
    val sugarcaneFarm = Idea(
        id = 1,
        name = "Automatic Sugarcane Farm",
        description = """
            An efficient sugarcane farm that automatically harvests and collects sugarcane using observers and pistons.

            This automatic sugarcane farm uses observers to detect when sugarcane grows to its full height. When detected, the observers send a redstone signal to pistons that break the sugarcane. The broken sugarcane falls into water streams that carry it to hoppers, which then deposit it into chests.

            The farm is expandable and can be built as large as needed. It's also very efficient, as it only harvests sugarcane when it's fully grown, maximizing the yield per harvest.

            This design works in all recent Minecraft versions and is relatively simple to build, requiring only basic redstone knowledge.
        """.trimIndent(),
        category = IdeaCategory.FARM,
        author = Author.SingleAuthor(name = UserMockData.RedstoneWizard.name),
        subAuthors = emptyList(),
        labels = listOf("redstone", "farm", "automation"),
        favouritesCount = 42,
        rating = RatingSummary(
            average = 4.7,
            total = 23
        ),
        difficulty = IdeaDifficulty.MID_GAME,
        worksInVersionRange = MinecraftVersionRange.Bounded(
            from = MinecraftVersion.fromString("1.19.0"),
            to = MinecraftVersion.fromString("1.19.4")
        ),
        testData = emptyList(),
        categoryData = emptyMap(),
        createdBy = UserMockData.RedstoneWizard.id,
        createdAt = ZonedDateTime.now().minusYears(1),
    )

    val villagerTradingHall = Idea(
        id = 2,
        name = "Villager Trading Hall",
        description = """
            A compact and efficient villager trading hall with easy access to all villager professions.
        """.trimIndent(),
        category = IdeaCategory.BUILD,
        author = Author.SingleAuthor(name = UserMockData.VillagerMaster.name),
        subAuthors = emptyList(),
        labels = listOf("villager", "trading", "economy"),
        favouritesCount = 38,
        rating = RatingSummary(
            average = 4.2,
            total = 18
        ),
        difficulty = IdeaDifficulty.END_GAME,
        worksInVersionRange = MinecraftVersionRange.LowerBounded(
            from = MinecraftVersion.fromString("1.18.0"),
        ),
        testData = emptyList(),
        categoryData = emptyMap(),
        createdBy = UserMockData.VillagerMaster.id,
        createdAt = ZonedDateTime.now().minusYears(1),
    )

    val ironFarm = Idea(
        id = 3,
        name = "Iron Farm",
        description = """
            A simple but effective iron farm that produces iron at a steady rate using villager mechanics.
        """.trimIndent(),
        category = IdeaCategory.FARM,
        author = Author.SingleAuthor(name = UserMockData.IronMiner.name),
        subAuthors = emptyList(),
        labels = listOf("iron", "farm", "villager"),
        favouritesCount = 56,
        rating = RatingSummary(
            average = 4.9,
            total = 32
        ),
        difficulty = IdeaDifficulty.MID_GAME,
        worksInVersionRange = MinecraftVersionRange.UpperBounded(
            to = MinecraftVersion.fromString("1.19.0"),
        ),
        testData = emptyList(),
        categoryData = emptyMap(),
        createdBy = UserMockData.IronMiner.id,
        createdAt = ZonedDateTime.now().minusYears(1),
    )

    val mobXpFarm = Idea(
        id = 4,
        name = "Mob XP Farm",
        description = """
            An efficient XP farm using a mob spawner or dark room design for quick leveling.
        """.trimIndent(),
        category = IdeaCategory.FARM,
        author = Author.Team(
            members = listOf(
                Author.TeamAuthor(UserMockData.XPGrinder.name, 0, "Lead", listOf("Design", "Testing")),
                Author.TeamAuthor(UserMockData.FarmingPro.name, 1, "Contributor", listOf("Building"))
            )
        ),
        subAuthors = emptyList(),
        labels = listOf("mob", "xp", "farm"),
        favouritesCount = 45,
        rating = RatingSummary(
            average = 3.8,
            total = 15
        ),
        difficulty = IdeaDifficulty.MID_GAME,
        worksInVersionRange = MinecraftVersionRange.Unbounded,
        testData = emptyList(),
        categoryData = emptyMap(),
        createdBy = UserMockData.XPGrinder.id,
        createdAt = ZonedDateTime.now().minusYears(1),
    )

    val allIdeas = listOf(sugarcaneFarm, villagerTradingHall, ironFarm, mobXpFarm)
}