package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.minecraft.LitematicaRegion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-414 — several files resolved as one build.
 *
 * Litematica saves a selection from one world, so a build with a nether side cannot be one file.
 * The screen already had the concept that fits them — MCO-398's region groups — so a file
 * contributes its regions as groups tagged with the file, and the flat list sums across all of
 * them. What is pinned here is that nothing is lost or double-counted in that sum, and that a
 * single-file import comes out byte-identical to the pre-MCO-414 shape.
 */
class MapSchematicFilesToMaterialsStepTest {

    private val catalog = listOf(
        "minecraft:oak_planks",
        "minecraft:stone",
        "minecraft:netherrack",
        "minecraft:redstone",
        "minecraft:water",
        "minecraft:water_bucket",
    ).map { Item(it, it.substringAfterLast(':')) }

    private fun file(name: String, vararg regions: Pair<String, Map<String, Int>>): ParsedSchematic {
        val regionList = regions.map { (regionName, items) -> LitematicaRegion(regionName, items) }
        val flat = LinkedHashMap<String, Int>()
        regionList.forEach { r -> r.items.forEach { (id, n) -> flat[id] = (flat[id] ?: 0) + n } }
        return ParsedSchematic(name, Litematica(name.removeSuffix(".litematic"), "", "", Triple(1, 1, 1), flat, regionList))
    }

    private fun resolve(vararg files: ParsedSchematic): SchematicMaterials = runBlocking {
        val result = MapSchematicFilesToMaterialsStep(catalog).process(files.toList())
        assertIs<Result.Success<SchematicMaterials>>(result)
        result.value
    }

    private fun SchematicMaterials.amountOf(id: String) =
        requirements.firstOrNull { it.first.id == id }?.second

    @Test
    fun `a material in both files is summed, not replaced`() {
        // The failure this guards is the one the issue describes from the other side: a build
        // arriving with only part of its materials. Overwriting instead of summing would show
        // the nether half's 200 planks as the whole build's requirement.
        val materials = resolve(
            file("Sorter.litematic", "Main" to mapOf("minecraft:oak_planks" to 500)),
            file("Sorter (nether).litematic", "Main" to mapOf("minecraft:oak_planks" to 200)),
        )

        assertEquals(700, materials.amountOf("minecraft:oak_planks"))
    }

    @Test
    fun `each file's regions become groups tagged with that file`() {
        val materials = resolve(
            file("Overworld.litematic", "Frame" to mapOf("minecraft:stone" to 100)),
            file("Nether.litematic", "Frame" to mapOf("minecraft:netherrack" to 60)),
        )

        assertEquals(2, materials.regions.size)
        assertEquals(listOf("Overworld", "Nether"), materials.regions.map { it.sourceFile })
    }

    @Test
    fun `several regions in several files all survive`() {
        val materials = resolve(
            file(
                "Overworld.litematic",
                "Frame" to mapOf("minecraft:stone" to 100),
                "Shell" to mapOf("minecraft:oak_planks" to 40),
            ),
            file("Nether.litematic", "Portal" to mapOf("minecraft:netherrack" to 60)),
        )

        assertEquals(3, materials.regions.size)
        assertEquals(100, materials.amountOf("minecraft:stone"))
        assertEquals(40, materials.amountOf("minecraft:oak_planks"))
        assertEquals(60, materials.amountOf("minecraft:netherrack"))
    }

    @Test
    fun `a single file carries no source tag at all`() {
        // Null rather than "the only file", so the review screen renders a one-file import
        // exactly as it did before multi-file existed rather than qualifying against nothing.
        val materials = resolve(
            file(
                "Sorter.litematic",
                "Frame" to mapOf("minecraft:stone" to 100),
                "Shell" to mapOf("minecraft:oak_planks" to 40),
            ),
        )

        assertTrue(materials.regions.isNotEmpty())
        materials.regions.forEach { assertNull(it.sourceFile) }
    }

    @Test
    fun `a file with no regions becomes one group named after itself`() {
        // Real Litematica files always have a region; test-constructed ones need not. Letting it
        // dissolve into the flat list would make that file's contribution unstrikeable.
        val regionless = ParsedSchematic(
            "Nether.litematic",
            Litematica("Nether", "", "", Triple(1, 1, 1), mapOf("minecraft:netherrack" to 60)),
        )
        val materials = resolve(
            file("Overworld.litematic", "Frame" to mapOf("minecraft:stone" to 100)),
            regionless,
        )

        val nether = materials.regions.single { it.sourceFile == "Nether" }
        assertEquals("Nether", nether.name)
        assertEquals(60, nether.requirements.single().second)
    }

    @Test
    fun `fluids stay one bucket per section across files`() {
        // MCO-398's reading, carried across the file boundary: a pond spanning both halves of a
        // build asks for one bucket per half — one per section you actually go and build — rather
        // than collapsing to a single bucket for the whole import.
        val materials = resolve(
            file("Overworld.litematic", "Pond" to mapOf("minecraft:water" to 4000)),
            file("Nether.litematic", "Pond" to mapOf("minecraft:water" to 900)),
        )

        assertEquals(2, materials.amountOf("minecraft:water_bucket"))
        assertEquals(4900, materials.placedCounts["minecraft:water_bucket"])
    }

    @Test
    fun `no files at all is a validation failure rather than an empty project`() {
        val result = runBlocking { MapSchematicFilesToMaterialsStep(catalog).process(emptyList()) }

        assertIs<Result.Failure<*>>(result)
    }
}
