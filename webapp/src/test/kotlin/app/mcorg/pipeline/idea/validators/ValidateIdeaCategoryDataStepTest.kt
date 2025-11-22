package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockkObject
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.*

class ValidateIdeaCategoryDataStepTest {

    private val requiredFarmFields = IdeaCategorySchemas.FARM.fields.count { it.required }

    private fun createParameters(vararg pairs: Pair<String, List<String>>, type: IdeaCategory = IdeaCategory.FARM): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, values) ->
            values.forEach { value -> builder.append(key, value) }
        }
        if (type == IdeaCategory.FARM) {
            if (!builder.build().contains("categoryData.size.x")) {
                builder.append("categoryData.size.x", "16")
            }
            if (!builder.build().contains("categoryData.size.y")) {
                builder.append("categoryData.size.y", "10")
            }
            if (!builder.build().contains("categoryData.size.z")) {
                builder.append("categoryData.size.z", "16")
            }
            if (!builder.build().contains("categoryData.howToUse")) {
                builder.append("categoryData.howToUse", "Default usage instructions")
            }
            if (!builder.build().contains("categoryData.playersRequired")) {
                builder.append("categoryData.playersRequired", "1")
            }
        }
        return builder.build()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            mockkObject(GetItemsInVersionRangeStep)
            coEvery { GetItemsInVersionRangeStep.process(any()) } returns app.mcorg.domain.pipeline.Result.success(
                listOf(
                    Item("minecraft:iron_ingot", "Iron Ingot"),
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:gold_ingot", "Gold Ingot")
                )
            )
        }
    }

    @Test
    fun `text field with valid value returns success`() {
        val params = createParameters(
            "categoryData.farmVersion" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals("v3.2", result["farmVersion"])
    }

    @Test
    fun `text field exceeding max length returns failure`() {
        val longText = "a".repeat(1001)
        val params = createParameters(
            "categoryData.howToUse" to listOf(longText)
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any { it is ValidationFailure.InvalidFormat && it.parameterName == "categoryData.howToUse" })
    }

    @Test
    fun `required text field missing returns failure`() {
        val params = Parameters.Empty
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.MissingParameter && it.parameterName == "howToUse"
        })
    }

    @Test
    fun `number field with valid integer returns success`() {
        val params = createParameters(
            "categoryData.yLevel" to listOf("64")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(64, result["yLevel"])
    }

    @Test
    fun `number field with non-integer returns failure`() {
        val params = createParameters(
            "categoryData.yLevel" to listOf("not-a-number")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any { it is ValidationFailure.InvalidFormat && it.parameterName == "categoryData.yLevel" })
    }

    @Test
    fun `number field below minimum returns failure`() {
        val params = createParameters(
            "categoryData.yLevel" to listOf("-100")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("must be at least") == true
        })
    }

    @Test
    fun `select field with valid option returns success`() {
        val params = createParameters(
            "categoryData.playersRequired" to listOf("1")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals("1", result["playersRequired"])
    }

    @Test
    fun `select field with invalid option returns failure`() {
        val params = createParameters(
            "categoryData.playersRequired" to listOf("invalid-option")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Invalid option") == true
        })
    }

    @Test
    fun `multi-select field with valid options returns success`() {
        val params = createParameters(
            "categoryData.biomes[]" to listOf("Plains", "Forest", "Desert")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val biomes = result["biomes"] as Set<*>
        assertEquals(3, biomes.size)
        assertTrue(biomes.contains("Plains"))
        assertTrue(biomes.contains("Forest"))
        assertTrue(biomes.contains("Desert"))
    }

    @Test
    fun `multi-select field with invalid option returns failure`() {
        val params = createParameters(
            "categoryData.biomes[]" to listOf("Plains", "InvalidBiome")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any { it is ValidationFailure.InvalidFormat })
    }

    @Test
    fun `multi-select field with empty array returns success`() {
        val params = createParameters(
            "categoryData.biomes[]" to emptyList()
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        TestUtils.executeAndAssertSuccess(step, params)
    }

    @Test
    fun `boolean field with true returns success`() {
        val params = createParameters(
            "categoryData.afkable" to listOf("true")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(true, result["afkable"])
    }

    @Test
    fun `boolean field with on returns success as true`() {
        val params = createParameters(
            "categoryData.stackable" to listOf("on")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(true, result["stackable"])
    }

    @Test
    fun `boolean field with invalid value returns failure`() {
        val params = createParameters(
            "categoryData.afkable" to listOf("maybe")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Boolean value expected") == true
        })
    }

    @Test
    fun `rate field with valid integer returns success`() {
        val params = createParameters(
            "categoryData.yLevel" to listOf("60")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(60, result["yLevel"])
    }

    @Test
    fun `percentage field with valid decimal returns success`() {
        val params = createParameters(
            "categoryData.hopperLockPercentage" to listOf("0.75"),
            type = IdeaCategory.STORAGE
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals(0.75, result["hopperLockPercentage"])
    }

    @Test
    fun `percentage field outside range returns failure`() {
        val params = createParameters(
            "categoryData.hopperLockPercentage" to listOf("1.5"),
            type = IdeaCategory.STORAGE
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("must be between") == true
        })
    }

    @Test
    fun `map field with key options returns success`() {
        val params = createParameters(
            "categoryData.size.x" to listOf("16"),
            "categoryData.size.y" to listOf("10"),
            "categoryData.size.z" to listOf("16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val size = result["size"] as Map<*, *>
        assertEquals(16, size["x"])
        assertEquals(10, size["y"])
        assertEquals(16, size["z"])
    }

    @Test
    fun `map field with invalid key option returns failure`() {
        val params = ParametersBuilder().apply {
            append("categoryData.width", "16")
            append("categoryData.howToUse", "Default usage instructions")
            append("categoryData.playersRequired", "1")
        }.build()
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.CustomValidation && it.message.contains("Unknown field provided: width")
        })
    }

    @Test
    fun `map field with blank required values get error`() {
        val params = createParameters(
            "categoryData.size.x" to listOf("16"),
            "categoryData.size.y" to listOf(""),
            "categoryData.size.z" to listOf("16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertEquals(1, result.size)
        assertIs<ValidationFailure.MissingParameter>(result.first())
    }

    @Test
    fun `free-form map field with matching keys and values returns success`() {
        val params = createParameters(
            "categoryData.mobRequirements.key[]" to listOf("zombie", "skeleton"),
            "categoryData.mobRequirements.value[]" to listOf("10", "5")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val mobReqs = result["mobRequirements"] as Map<*, *>
        assertEquals(10, mobReqs["zombie"])
        assertEquals(5, mobReqs["skeleton"])
    }

    @Test
    fun `free-form map field with mismatched keys and values returns failure`() {
        val params = createParameters(
            "categoryData.mobRequirements.key[]" to listOf("zombie", "skeleton", "creeper"),
            "categoryData.mobRequirements.value[]" to listOf("10", "5")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Mismatched number of keys and values for typed map field") == true
        })
    }

    @Test
    fun `free-form map field filters out blank keys`() {
        val params = createParameters(
            "categoryData.mobRequirements.key[]" to listOf("zombie", "", "skeleton"),
            "categoryData.mobRequirements.value[]" to listOf("10", "3", "5")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val mobReqs = result["mobRequirements"] as Map<*, *>
        assertEquals(2, mobReqs.size)
        assertEquals(10, mobReqs["zombie"])
        assertEquals(5, mobReqs["skeleton"])
    }

    @Test
    fun `list field with allowed values returns success`() {
        val params = createParameters(
            "categoryData.playerSetup[]" to listOf("Looting III Sword", "Shield")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val setup = assertIs<Set<*>>(result["playerSetup"])
        assertEquals(2, setup.size)
        assertTrue(setup.contains("Looting III Sword"))
        assertTrue(setup.contains("Shield"))
    }

    @Test
    fun `list field with invalid allowed value returns failure`() {
        val params = createParameters(
            "categoryData.playerSetup[]" to listOf("Looting III Sword", "Invalid Item")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Invalid option for multi-select") == true
        })
    }

    @Test
    fun `list field without allowed values as comma-separated returns success`() {
        val params = createParameters(
            "categoryData.pros" to listOf("Fast, Efficient, Cheap")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val pros = result["pros"] as List<*>
        assertEquals(3, pros.size)
        assertTrue(pros.contains("Fast"))
        assertTrue(pros.contains("Efficient"))
        assertTrue(pros.contains("Cheap"))
    }

    @Test
    fun `multiple fields of different types return success`() {
        val params = createParameters(
            "categoryData.farmVersion" to listOf("v3.2"),
            "categoryData.afkable" to listOf("true"),
            "categoryData.playersRequired" to listOf("1"),
            "categoryData.biomes[]" to listOf("Plains", "Forest"),
            "categoryData.size.x" to listOf("16"),
            "categoryData.size.y" to listOf("10"),
            "categoryData.size.z" to listOf("16"),
            "categoryData.howToUse" to listOf("Stand here and wait")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(6, result.size)
        assertEquals("v3.2", result["farmVersion"])
        assertEquals(true, result["afkable"])
    }

    @Test
    fun `unknown field returns failure`() {
        val params = createParameters(
            "categoryData.unknownField" to listOf("some value"),
            "categoryData.farmVersion" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertEquals(1, result.size)
        val error = result.first()
        assertIs<ValidationFailure.CustomValidation>(error)
        assertContains(error.message, "Unknown field provided: unknownField")
    }

    @Test
    fun `non-category-data parameters are ignored`() {
        val params = createParameters(
            "title" to listOf("Iron Farm"),
            "description" to listOf("A simple iron farm"),
            "categoryData.farmVersion" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals(requiredFarmFields + 1, result.size)
        assertEquals("v3.2", result["farmVersion"])
    }

    @Test
    fun `single value field submitted as array ignores value`() {
        val params = createParameters(
            "categoryData.farmVersion[]" to listOf("v3.2", "v3.3")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertNull(result["farmVersion"])
    }

    @Test
    fun `map field submitted as single value returns failure`() {
        val params = ParametersBuilder().apply {
            append("categoryData.size", "16x16x16")
            append("categoryData.howToUse", "Default usage instructions")
            append("categoryData.playersRequired", "1")
        }.build()

        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertEquals(result.size, 3)
        result.forEach {
            assertIs<ValidationFailure.MissingParameter>(it)
            assertTrue { it.parameterName.contains("x") || it.parameterName.contains("y") || it.parameterName.contains("z")  }
        }
    }

    @Test
    fun `empty parameters returns success with empty map when no required fields`() {
        val params = Parameters.Empty
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple values for single value field returns failure`() {
        val params = createParameters(
            "categoryData.farmVersion" to listOf("v3.2", "v3.3")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Expected single value") == true
        })
    }

    @Test
    fun `blank values in multi-select are filtered out`() {
        val params = createParameters(
            "categoryData.biomes[]" to listOf("Plains", "", "Forest", "  ")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val biomes = result["biomes"] as Set<*>
        assertEquals(2, biomes.size)
        assertTrue(biomes.contains("Plains"))
        assertTrue(biomes.contains("Forest"))
    }

    @Test
    fun `list field returns list type for allowed values`() {
        val params = createParameters(
            "categoryData.playerSetup[]" to listOf("Looting III Sword", "Shield")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertIs<Set<*>>(result["playerSetup"])
    }

    @Test
    fun `multi-select returns set type`() {
        val params = createParameters(
            "categoryData.biomes[]" to listOf("Plains", "Forest")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertIs<Set<*>>(result["biomes"])
    }

    @Test
    fun `all validation failures are collected before returning`() {
        val params = createParameters(
            "categoryData.yLevel" to listOf("not-a-number"),
            "categoryData.playersRequired" to listOf("invalid-option"),
            "categoryData.biomes[]" to listOf("InvalidBiome")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.size >= 3)
    }

    @Test
    fun `complex farm is decoded`() {
        val params = createParameters(
            "categoryData.productionRate.key[]" to listOf("Normal Mode"),
            "categoryData.productionRate.value.key[]" to listOf("minecraft:iron_ingot", "minecraft:diamond"),
            "categoryData.productionRate.value.value[]" to listOf("2000", "500"),
            "categoryData.size.x" to listOf("10"),
            "categoryData.size.y" to listOf("20"),
            "categoryData.size.z" to listOf("30"),
            "categoryData.howToUse" to listOf("Use as needed"),
        )

        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        val productionRate = assertIs<Map<*, *>>(result["productionRate"])
        assertEquals(1, productionRate.size)
        val normalRate = assertIs<Map<*, *>>(productionRate["Normal Mode"])
        assertEquals(2, normalRate.size)
        assertEquals(2000, normalRate["minecraft:iron_ingot"])
        assertEquals(500, normalRate["minecraft:diamond"])

        val size = assertIs<Map<*, *>>(result["size"])
        assertEquals(10, size["x"])
        assertEquals(20, size["y"])
        assertEquals(30, size["z"])

        assertEquals("Use as needed", result["howToUse"])
    }

    @Test
    fun `empty parameters returns errors for each required field`() {
        val params = Parameters.Empty
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertEquals(requiredFarmFields, result.size)
        result.forEach {
            assertIs<ValidationFailure.MissingParameter>(it)
        }
    }

    @Test
    fun `Empty, non-required values are ignored`() {
        val params = ParametersBuilder().apply {
            append("categoryData.farmVersion", "")
            append("categoryData.productionRate.key[]", "")
            append("categoryData.productionRate.value.key[]", "")
            append("categoryData.productionRate.value.value[]", "")
            append("categoryData.consumptionRate.key[]", "")
            append("categoryData.consumptionRate.value.key[]", "")
            append("categoryData.consumptionRate.value.value[]", "")
            append("categoryData.size.x", "16")
            append("categoryData.size.y", "10")
            append("categoryData.size.z", "16")
            append("categoryData.stackable", "")
            append("categoryData.tileable", "")
            append("categoryData.yLevel", "")
            append("categoryData.subChunkAligned", "")
            append("categoryData.biomes[]", "")
            append("categoryData.mobRequirements.key[]", "")
            append("categoryData.mobRequirements.value[]", "")
            append("categoryData.playerSetup[]", "")
            append("categoryData.beaconSetup[]", "")
            append("categoryData.howToUse", "Default usage instructions")
            append("categoryData.afkable", "")
            append("categoryData.playersRequired", "1")
            append("categoryData.pros", "")
            append("categoryData.cons", "")
            append("categoryData.directional", "")
            append("categoryData.locational", "")
        }.build()

        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)
        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(3, result.size)
        assertEquals("Default usage instructions", result["howToUse"])
        assertEquals(16, (result["size"] as Map<*, *>)["x"])
        assertEquals(10, (result["size"] as Map<*, *>)["y"])
        assertEquals(16, (result["size"] as Map<*, *>)["z"])
        assertEquals("1", result["playersRequired"])
    }
}

