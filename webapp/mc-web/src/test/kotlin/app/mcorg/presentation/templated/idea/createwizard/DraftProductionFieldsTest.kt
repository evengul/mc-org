package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-412 — the productions section of the idea form.
 *
 * Two behaviours worth pinning: modes stay out of sight until there are two, and the farm
 * recommendation appears exactly when it is true.
 */
class DraftProductionFieldsTest {

    private fun draft(data: String) = IdeaDraft(
        id = 1,
        userId = 1,
        data = data,
        currentStage = "PRODUCTIONS",
        sourceIdeaId = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    private fun render(data: String) = createHTML().div { draftProductionFields(draft(data)) }

    /**
     * The markup a reader actually sees, with two things cut out.
     *
     * The inline script mentions every class it manipulates, so asserting a class is *absent* from
     * the whole document would match the script and never fail. Since MCO-417 the section also
     * carries a `<template>` holding the block a new mode is cloned from — inert markup that always
     * has a name field, because a second mode always gets one. Leaving it in makes
     * "one mode is never called a mode" unfalsifiable in the same way.
     */
    private fun markup(data: String) = render(data)
        .substringBefore("<script")
        .replace(Regex("<template[\\s\\S]*?</template>"), "")

    private fun isRecommendationVisible(html: String): Boolean {
        val marker = html.substringAfter("id=\"production-recommendation\"", "")
        return html.contains("production-recommendation") &&
            !html.substringBefore("id=\"production-recommendation\"").takeLast(120)
                .contains("production-recommendation--hidden") &&
            marker.isNotEmpty()
    }

    @Test
    fun `one mode is never called a mode`() {
        // The name is submitted blank and becomes "Default" on save; the author never sees either.
        val html = markup("""{"productionModes":[{"name":"","rates":{"minecraft:ice":71000}}]}""")

        assertContains(html, "productionMode[0][name]")
        assertContains(html, "type=\"hidden\"")
        assertFalse(html.contains("production-mode__name"))
    }

    @Test
    fun `a second mode brings names to both`() {
        val html = markup(
            """{"productionModes":[
                {"name":"Max speed","rates":{"minecraft:ice":62000}},
                {"name":"Slowed","rates":{"minecraft:ice":18000}}
            ]}"""
        )

        assertContains(html, "Max speed")
        assertContains(html, "Slowed")
        assertTrue(html.split("production-mode__name").size - 1 >= 2)
    }

    @Test
    fun `existing rates come back as removable rows`() {
        val html = render("""{"productionModes":[{"name":"","rates":{"minecraft:ice":71000}}]}""")

        assertContains(html, "productionRate[0][minecraft:ice]")
        assertContains(html, "71000")
    }

    @Test
    fun `a rate row carries the item id the add script de-duplicates on`() {
        // Without it, re-adding an item that came back from a saved draft appends a second hidden
        // input with the same name — and the parser takes the first, so the correction is lost.
        val html = markup("""{"productionModes":[{"name":"","rates":{"minecraft:ice":71000}}]}""")

        assertContains(html, "data-item-id=\"minecraft:ice\"")
    }

    @Test
    fun `the only mode keeps a name it was given`() {
        // Two modes, then the rates are deleted from one: the survivor is alone and loses its name
        // field, but writing "" into the hidden input would rename it Default on the next save.
        val html = markup("""{"productionModes":[{"name":"Max speed","rates":{"minecraft:ice":71000}}]}""")

        assertFalse(html.contains("production-mode__name"), "one mode is still never called a mode")
        assertContains(html, "value=\"Max speed\"")
    }

    @Test
    fun `each mode gets its own catalog search, scoped so they cannot collide (MCO-417)`() {
        // The whole reason productions shipped with a raw text field: /items/search hardcodes a
        // global selectSearchedItem, and one set of element ids cannot serve two modes at once.
        val html = markup(
            """{"productionModes":[
                {"name":"Max speed","rates":{"minecraft:ice":62000}},
                {"name":"Slowed","rates":{"minecraft:ice":18000}}
            ]}"""
        )

        assertContains(html, "item-search-results-production-0")
        assertContains(html, "item-search-results-production-1")
        assertEquals(2, html.split("hx-get=\"/items/search\"").size - 1, "one search per mode")
    }

    @Test
    fun `the search respects the draft's version range`() {
        // Otherwise it offers items the idea claims not to work with.
        val html = markup(
            """{"versionRange":{
                    "type":"app.mcorg.domain.model.minecraft.MinecraftVersionRange.LowerBounded",
                    "from":{"type":"app.mcorg.domain.model.minecraft.MinecraftVersion.Release","major":1,"minor":21,"patch":0}
                },
                "productionModes":[{"name":"","rates":{}}]}"""
        )

        assertContains(html, "versionRangeType: 'lowerBounded'")
        assertContains(html, "versionFrom: '1.21.0'")
    }

    @Test
    fun `a new mode is cloned from a server-rendered template, not built in JS`() {
        // The template carries a real combo — same hx-vals, same version range — so a mode added
        // client-side searches exactly like the ones rendered with the page.
        val html = render("""{"productionModes":[{"name":"","rates":{}}]}""")

        assertContains(html, "id=\"production-mode-template\"")
        assertContains(html, "__MODE_INDEX__")
        assertContains(html, "htmx.process(block)")
    }

    @Test
    fun `the free-text item field and its minecraft prefix are gone`() {
        // "Blue Ice" used to become minecraft:Blue Ice — accepted, matched nothing, and broke
        // import of the idea into every world.
        val html = render("""{"productionModes":[{"name":"","rates":{}}]}""")

        assertFalse(html.contains("production-item-search"), "the raw id field should be gone")
        assertFalse(html.contains("'minecraft:' +"), "nothing should be guessing a namespace")
    }

    @Test
    fun `a farm with no output is told what it costs them`() {
        // Recommended rather than required: an author who has seen a video but not built the farm
        // does not know the rate, and an invented number is worse than a missing one. So the note
        // states the consequence — MCO-294 matches farms by produced item.
        val html = render("""{"category":"FARM"}""")

        assertTrue(isRecommendationVisible(html))
        assertContains(html, "will not come up when a world needs that item")
    }

    @Test
    fun `the note goes once the farm declares output`() {
        val html = render("""{"category":"FARM","productionModes":[{"name":"","rates":{"minecraft:ice":71000}}]}""")

        assertFalse(isRecommendationVisible(html))
    }

    @Test
    fun `a storage system is not nagged about producing nothing`() {
        // Most builds produce nothing, and that is not a defect.
        assertFalse(isRecommendationVisible(render("""{"category":"STORAGE"}""")))
    }

    @Test
    fun `no category chosen yet means no advice about farms`() {
        assertFalse(isRecommendationVisible(render("{}")))
    }
}
