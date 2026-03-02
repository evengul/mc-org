package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.domain.model.minecraft.MinecraftVersion
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class PathResolversTest {

    private val basePath = Path.of("/data")
    private val v121 = MinecraftVersion.Release(1, 21, 0)
    private val v120 = MinecraftVersion.Release(1, 20, 4)
    private val v118 = MinecraftVersion.Release(1, 18, 0)

    @Test
    fun `resolves item tags path for 1_21+`() {
        assertEquals(Path.of("/data/tags/item"), ServerPathResolvers.resolveItemTagsPath(basePath, v121))
    }

    @Test
    fun `resolves item tags path for pre-1_21`() {
        assertEquals(Path.of("/data/tags/items"), ServerPathResolvers.resolveItemTagsPath(basePath, v120))
        assertEquals(Path.of("/data/tags/items"), ServerPathResolvers.resolveItemTagsPath(basePath, v118))
    }

    @Test
    fun `resolves block tags path for 1_21+`() {
        assertEquals(Path.of("/data/tags/block"), ServerPathResolvers.resolveBlockTagsPath(basePath, v121))
    }

    @Test
    fun `resolves block tags path for pre-1_21`() {
        assertEquals(Path.of("/data/tags/blocks"), ServerPathResolvers.resolveBlockTagsPath(basePath, v120))
    }

    @Test
    fun `resolves loot tables path for 1_21+`() {
        assertEquals(Path.of("/data/loot_table"), ServerPathResolvers.resolveLootTablesPath(basePath, v121))
    }

    @Test
    fun `resolves loot tables path for pre-1_21`() {
        assertEquals(Path.of("/data/loot_tables"), ServerPathResolvers.resolveLootTablesPath(basePath, v120))
    }

    @Test
    fun `resolves recipes path for 1_21+`() {
        assertEquals(Path.of("/data/recipe"), ServerPathResolvers.resolveRecipesPath(basePath, v121))
    }

    @Test
    fun `resolves recipes path for pre-1_21`() {
        assertEquals(Path.of("/data/recipes"), ServerPathResolvers.resolveRecipesPath(basePath, v120))
    }

    @Test
    fun `boundary version 1_21_0 uses new paths`() {
        val v = MinecraftVersion.Release(1, 21, 0)
        assertEquals(Path.of("/data/tags/item"), ServerPathResolvers.resolveItemTagsPath(basePath, v))
        assertEquals(Path.of("/data/tags/block"), ServerPathResolvers.resolveBlockTagsPath(basePath, v))
        assertEquals(Path.of("/data/loot_table"), ServerPathResolvers.resolveLootTablesPath(basePath, v))
        assertEquals(Path.of("/data/recipe"), ServerPathResolvers.resolveRecipesPath(basePath, v))
    }

    @Test
    fun `boundary version 1_20_6 uses old paths`() {
        val v = MinecraftVersion.Release(1, 20, 6)
        assertEquals(Path.of("/data/tags/items"), ServerPathResolvers.resolveItemTagsPath(basePath, v))
        assertEquals(Path.of("/data/tags/blocks"), ServerPathResolvers.resolveBlockTagsPath(basePath, v))
        assertEquals(Path.of("/data/loot_tables"), ServerPathResolvers.resolveLootTablesPath(basePath, v))
        assertEquals(Path.of("/data/recipes"), ServerPathResolvers.resolveRecipesPath(basePath, v))
    }
}
