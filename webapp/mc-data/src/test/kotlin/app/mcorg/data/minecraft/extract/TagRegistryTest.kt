package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The whole tag registry, resolved, against every ingested version (MCO-488).
 *
 * [ChoiceTagCanonicaliser][app.mcorg.data.minecraft.extract.recipe.ChoiceTagCanonicaliser] walks
 * every tag rather than only the ones a recipe references, which is what first asked these
 * questions of real data — and got a `StackOverflowError`. Unit tests pin the two mechanisms;
 * this pins that vanilla, in all 31 versions, actually satisfies them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TagRegistryTest : ServerFileTest() {

    /**
     * Every tag with values resolves to at least one item. Under the old base-filename keying
     * this failed twice over: `#minecraft:foot_armor` was overwritten by
     * `enchantable/foot_armor.json` into a tag whose only member was itself (empty, after a
     * cycle guard — a stack overflow, without one), and every reference to a nested tag
     * (`#minecraft:enchantable/durability`) dangled, because the registry had filed it under
     * `#minecraft:durability`.
     */
    @ParameterizedTest
    @MethodSource("getVersions")
    fun `every tag in the registry resolves to items`(version: MinecraftVersion.Release) {
        val context = contextFor(version)

        val empty = context.tags
            .filter { (_, values) -> values.isNotEmpty() }
            .filter { (tag, _) -> context.contentOfTag(tag).isEmpty() }
            .keys

        assertEquals(emptySet(), empty, "Tags in $version have values but resolve to no items")
    }

    /** No tag references a tag the registry does not hold — the other half of the same bug. */
    @ParameterizedTest
    @MethodSource("getVersions")
    fun `every referenced tag is in the registry`(version: MinecraftVersion.Release) {
        val context = contextFor(version)

        val dangling = context.tags.values
            .flatten()
            .filter { it.startsWith("#") }
            .filterNot { it in context.tags }
            .toSet()

        assertEquals(emptySet(), dangling, "Tags referenced but not defined in $version")
    }

    /**
     * The concrete pair the bug was found on. Both are vanilla tags, they are not the same tag,
     * and the nested one is defined purely by reference to the other — so a registry that keys
     * them apart is the only one in which either is right.
     */
    @ParameterizedTest
    @MethodSource("getVersions")
    fun `foot_armor and enchantable foot_armor are two tags`(version: MinecraftVersion.Release) {
        val context = contextFor(version)
        val nested = context.tags["#minecraft:enchantable/foot_armor"] ?: return // pre-1.20.5

        assertEquals(listOf("#minecraft:foot_armor"), nested)

        val boots = context.contentOfTag("#minecraft:foot_armor")
        assertTrue(boots.contains("minecraft:iron_boots"), "#foot_armor in $version: $boots")
        assertFalse(boots.any { it.startsWith("#") }, "#foot_armor in $version resolved to a tag: $boots")
        assertEquals(boots, context.contentOfTag("#minecraft:enchantable/foot_armor"))
    }
}
