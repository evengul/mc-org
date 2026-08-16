package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.TestUtils
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.mockk.coEvery
import io.mockk.mockkObject
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the slim category schema (MCO-204): a handful of filterable fields per category
 * plus the free-form `specs` block. Nothing but the CART_TECH discriminator is required —
 * import speed is the point.
 */
class ValidateIdeaCategoryDataStepTest {

    private fun createParameters(vararg pairs: Pair<String, List<String>>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, values) ->
            values.forEach { value -> builder.append(key, value) }
        }
        return builder.build()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            mockkObject(GetItemsInVersionRangeStep)
            coEvery { GetItemsInVersionRangeStep.process(any()) } returns Result.success(
                listOf(
                    Item("minecraft:iron_ingot", "Iron Ingot"),
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:gold_ingot", "Gold Ingot")
                )
            )
        }
    }

    // --- Booleans ---

    @Test
    fun `boolean field with true returns success`() {
        val params = createParameters("categoryData.afkable" to listOf("true"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(CategoryValue.BooleanValue(true), result["afkable"])
    }

    @Test
    fun `boolean field with on returns success as true`() {
        val params = createParameters("categoryData.tileable" to listOf("on"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(CategoryValue.BooleanValue(true), result["tileable"])
    }

    @Test
    fun `boolean field with invalid value returns failure`() {
        val params = createParameters("categoryData.afkable" to listOf("maybe"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Boolean value expected") == true
        })
    }

    // --- Selects ---

    @Test
    fun `select field with valid option returns success`() {
        val params = createParameters("categoryData.storageType" to listOf("chest_hall"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(CategoryValue.TextValue("chest_hall"), result["storageType"])
    }

    @Test
    fun `select field with invalid option returns failure`() {
        val params = createParameters("categoryData.storageType" to listOf("invalid-option"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Invalid option") == true
        })
    }

    @Test
    fun `required select missing returns failure`() {
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.CART_TECH)

        val result = TestUtils.executeAndAssertFailure(step, Parameters.Empty)

        assertEquals(1, result.size)
        assertTrue(result.any {
            it is ValidationFailure.MissingParameter && it.parameterName == "cartTechType"
        })
    }

    // --- Multi-select ---

    @Test
    fun `multi-select field with valid options returns success`() {
        val params = createParameters("categoryData.materials[]" to listOf("Stone", "Wood", "Glass"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.BUILD)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val materials = assertIs<CategoryValue.MultiSelectValue>(result["materials"]).values
        assertEquals(setOf("Stone", "Wood", "Glass"), materials)
    }

    @Test
    fun `multi-select field with invalid option returns failure`() {
        val params = createParameters("categoryData.materials[]" to listOf("Stone", "Unobtanium"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.BUILD)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Invalid option for multi-select") == true
        })
    }

    @Test
    fun `blank values in multi-select are filtered out`() {
        val params = createParameters("categoryData.materials[]" to listOf("Stone", "", "Glass", "  "))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.BUILD)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val materials = assertIs<CategoryValue.MultiSelectValue>(result["materials"]).values
        assertEquals(setOf("Stone", "Glass"), materials)
    }

    // --- Size struct (optional, including its sub-fields) ---

    @Test
    fun `struct field with all sub-values returns success`() {
        val params = createParameters(
            "categoryData.size.x" to listOf("16"),
            "categoryData.size.y" to listOf("10"),
            "categoryData.size.z" to listOf("16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val size = assertIs<CategoryValue.MapValue>(result["size"]).value
        assertEquals(CategoryValue.IntValue(16), size["x"])
        assertEquals(CategoryValue.IntValue(10), size["y"])
        assertEquals(CategoryValue.IntValue(16), size["z"])
    }

    @Test
    fun `struct field keeps the sub-values that were provided`() {
        val params = createParameters(
            "categoryData.size.x" to listOf("16"),
            "categoryData.size.y" to listOf(""),
            "categoryData.size.z" to listOf("")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val size = assertIs<CategoryValue.MapValue>(result["size"]).value
        assertEquals(mapOf<String, CategoryValue>("x" to CategoryValue.IntValue(16)), size)
    }

    @Test
    fun `struct field with no sub-values is omitted entirely`() {
        val params = createParameters(
            "categoryData.size.x" to listOf(""),
            "categoryData.size.y" to listOf(""),
            "categoryData.size.z" to listOf("")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertNull(result["size"])
    }

    @Test
    fun `number sub-field with non-integer returns failure`() {
        val params = createParameters("categoryData.size.x" to listOf("not-a-number"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.parameterName == "categoryData.size.x"
        })
    }

    // --- Specs block: the free-form label -> value replacement for typed fields ---

    @Test
    fun `specs with matching labels and values returns success`() {
        val params = createParameters(
            "categoryData.specs.key[]" to listOf("TNT per piston", "Remaining fuse"),
            "categoryData.specs.value[]" to listOf("10", "21gt")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.TNT)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val specs = assertIs<CategoryValue.MapValue>(result["specs"]).value
        assertEquals(CategoryValue.TextValue("10"), specs["TNT per piston"])
        assertEquals(CategoryValue.TextValue("21gt"), specs["Remaining fuse"])
    }

    @Test
    fun `specs with mismatched labels and values returns failure`() {
        val params = createParameters(
            "categoryData.specs.key[]" to listOf("a", "b", "c"),
            "categoryData.specs.value[]" to listOf("1", "2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.TNT)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Mismatched number of keys and values") == true
        })
    }

    @Test
    fun `specs rows with a blank label are skipped`() {
        val params = createParameters(
            "categoryData.specs.key[]" to listOf("Width", "", "Precision"),
            "categoryData.specs.value[]" to listOf("15", "ignored", "1e-9")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.TNT)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val specs = assertIs<CategoryValue.MapValue>(result["specs"]).value
        assertEquals(2, specs.size)
        assertEquals(CategoryValue.TextValue("15"), specs["Width"])
        assertEquals(CategoryValue.TextValue("1e-9"), specs["Precision"])
    }

    @Test
    fun `specs rows with a blank value are skipped`() {
        val params = createParameters(
            "categoryData.specs.key[]" to listOf("Width", "Precision"),
            "categoryData.specs.value[]" to listOf("15", "")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.TNT)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val specs = assertIs<CategoryValue.MapValue>(result["specs"]).value
        assertEquals(mapOf<String, CategoryValue>("Width" to CategoryValue.TextValue("15")), specs)
    }

    @Test
    fun `entirely blank specs block is omitted`() {
        val params = createParameters(
            "categoryData.specs.key[]" to listOf(""),
            "categoryData.specs.value[]" to listOf("")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.TNT)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertNull(result["specs"])
    }

    // --- Production rate: item -> rate, the one field the engine consumes ---

    @Test
    fun `references parse as a comma-separated list`() {
        val params = createParameters(
            "categoryData.references" to listOf("https://youtu.be/abc, https://example.com/schematic")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val references = assertIs<CategoryValue.MultiSelectValue>(result["references"]).values
        assertEquals(setOf("https://youtu.be/abc", "https://example.com/schematic"), references)
    }

    // --- Retired fields and general parameter handling ---

    @Test
    fun `retired field returns unknown field failure`() {
        val params = createParameters("categoryData.playersRequired" to listOf("1"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertEquals(1, result.size)
        val error = assertIs<ValidationFailure.CustomValidation>(result.first())
        assertContains(error.message, "Unknown field provided: playersRequired")
    }

    @Test
    fun `unknown field returns failure`() {
        val params = createParameters(
            "categoryData.unknownField" to listOf("some value"),
            "categoryData.afkable" to listOf("true")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertEquals(1, result.size)
        val error = assertIs<ValidationFailure.CustomValidation>(result.first())
        assertContains(error.message, "Unknown field provided: unknownField")
    }

    @Test
    fun `non-category-data parameters are ignored`() {
        val params = createParameters(
            "title" to listOf("Iron Farm"),
            "description" to listOf("A simple iron farm"),
            "categoryData.afkable" to listOf("true")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(1, result.size)
        assertEquals(CategoryValue.BooleanValue(true), result["afkable"])
    }

    @Test
    fun `single value field submitted as array ignores value`() {
        val params = createParameters("categoryData.storageType[]" to listOf("chest_hall", "bulk_storage"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertNull(result["storageType"])
    }

    @Test
    fun `multiple values for single value field returns failure`() {
        val params = createParameters("categoryData.storageType" to listOf("chest_hall", "bulk_storage"))
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Expected single value") == true
        })
    }

    @Test
    fun `empty parameters succeed for categories with no required fields`() {
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, Parameters.Empty)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a fully blank form stores nothing`() {
        val params = ParametersBuilder().apply {
            append("categoryData.size.x", "")
            append("categoryData.size.y", "")
            append("categoryData.size.z", "")
            append("categoryData.tileable", "")
            append("categoryData.directional", "")
            append("categoryData.afkable", "")
            append("categoryData.specs.key[]", "")
            append("categoryData.specs.value[]", "")
            append("categoryData.references", "")
        }.build()
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertTrue(result.isEmpty(), "Expected no category data, got $result")
    }

    @Test
    fun `all validation failures are collected before returning`() {
        val params = createParameters(
            "categoryData.size.x" to listOf("not-a-number"),
            "categoryData.storageType" to listOf("invalid-option"),
            "categoryData.tileable" to listOf("maybe")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertEquals(3, result.size)
    }

    @Test
    fun `a complete farm submission is decoded`() {
        val params = createParameters(
            "categoryData.size.x" to listOf("10"),
            "categoryData.size.y" to listOf("20"),
            "categoryData.size.z" to listOf("30"),
            "categoryData.tileable" to listOf("true"),
            "categoryData.afkable" to listOf("true"),
            "categoryData.specs.key[]" to listOf("Villagers"),
            "categoryData.specs.value[]" to listOf("3"),
            "categoryData.references" to listOf("https://youtu.be/abc")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(
            // productionRate left the schema in MCO-412 — production is relational now.
            setOf("size", "tileable", "afkable", "specs", "references"),
            result.keys
        )
        val size = assertIs<CategoryValue.MapValue>(result["size"]).value
        assertEquals(CategoryValue.IntValue(20), size["y"])
        val specs = assertIs<CategoryValue.MapValue>(result["specs"]).value
        assertEquals(CategoryValue.TextValue("3"), specs["Villagers"])
    }
}
