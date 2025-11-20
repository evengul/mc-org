package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

object GetItemsInVersionRangeStep : Step<MinecraftVersionRange, AppFailure.DatabaseError, List<Item>> {
    private val logger = LoggerFactory.getLogger(GetItemsInVersionRangeStep::class.java)
    override suspend fun process(input: MinecraftVersionRange): Result<AppFailure.DatabaseError, List<Item>> {
        return DatabaseSteps.query<MinecraftVersionRange, List<Item>>(
            sql = SafeSQL.select(getQuery(input)),
            resultMapper = {
                buildList {
                    while (it.next()) {
                        if (input !is MinecraftVersionRange.Unbounded) {
                            val version = try {
                                MinecraftVersion.fromString(it.getString("version"))
                            } catch (e: Exception) {
                                logger.warn("Invalid item version format in database: ${it.getString("version")}", e)
                                continue
                            }
                            if (!input.withinBounds(version)) continue
                        }
                        val item = Item(
                            id = it.getString("item_id"),
                            name = it.getString("item_name")
                        )
                        add(item)
                    }
                }
            }
        ).process(input)
    }
}


private fun getQuery(range: MinecraftVersionRange): String {
    @Language("SQL")
    val sql = when (range) {
        is MinecraftVersionRange.Unbounded -> "select distinct item_id, item_name from minecraft_items order by item_name desc"
        else -> "select distinct item_id, item_name, version from minecraft_items order by item_name desc"
    }

    return sql
}