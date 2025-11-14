package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidateIdeaCategoryDataStepTest {

    private val requiredFarmFields = 4

    private fun createParameters(vararg pairs: Pair<String, List<String>>, type: IdeaCategory = IdeaCategory.FARM): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, values) ->
            values.forEach { value -> builder.append(key, value) }
        }
        if (type == IdeaCategory.FARM) {
            // Ensure required field is present for FARM category
            if (!builder.build().contains("categoryData[productionRate]")) {
                builder.append("categoryData[productionRate]", "1000")
            }
            if (!builder.build().contains("categoryData[size][x][value]")) {
                builder.append("categoryData[size][x][value]", "16")
            }
            if (!builder.build().contains("categoryData[size][y][value]")) {
                builder.append("categoryData[size][y][value]", "10")
            }
            if (!builder.build().contains("categoryData[size][z][value]")) {
                builder.append("categoryData[size][z][value]", "16")
            }
            if (!builder.build().contains("categoryData[howToUse]")) {
                builder.append("categoryData[howToUse]", "Default usage instructions")
            }
            if (!builder.build().contains("categoryData[playersRequired]")) {
                builder.append("categoryData[playersRequired]", "1")
            }
        }
        return builder.build()
    }

    @Test
    fun `text field with valid value returns success`() {
        val params = createParameters(
            "categoryData[farmVersion]" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals("v3.2", result["farmVersion"])
    }

    @Test
    fun `text field exceeding max length returns failure`() {
        val longText = "a".repeat(1001)
        val params = createParameters(
            "categoryData[howToUse]" to listOf(longText)
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any { it is ValidationFailure.InvalidFormat && it.parameterName == "howToUse" })
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
            "categoryData[yLevel]" to listOf("64")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(64, result["yLevel"])
    }

    @Test
    fun `number field with non-integer returns failure`() {
        val params = createParameters(
            "categoryData[yLevel]" to listOf("not-a-number")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any { it is ValidationFailure.InvalidFormat && it.parameterName == "yLevel" })
    }

    @Test
    fun `number field below minimum returns failure`() {
        val params = createParameters(
            "categoryData[productionRate]" to listOf("-100")
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
            "categoryData[playersRequired]" to listOf("1")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals("1", result["playersRequired"])
    }

    @Test
    fun `select field with invalid option returns failure`() {
        val params = createParameters(
            "categoryData[playersRequired]" to listOf("invalid-option")
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
            "categoryData[biomes][]" to listOf("Plains", "Forest", "Desert")
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
            "categoryData[biomes][]" to listOf("Plains", "InvalidBiome")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)

        assertTrue(result.any { it is ValidationFailure.InvalidFormat })
    }

    @Test
    fun `multi-select field with empty array returns success`() {
        val params = createParameters(
            "categoryData[biomes][]" to emptyList()
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        TestUtils.executeAndAssertSuccess(step, params)
    }

    @Test
    fun `boolean field with true returns success`() {
        val params = createParameters(
            "categoryData[afkable]" to listOf("true")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(true, result["afkable"])
    }

    @Test
    fun `boolean field with on returns success as true`() {
        val params = createParameters(
            "categoryData[stackable]" to listOf("on")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(true, result["stackable"])
    }

    @Test
    fun `boolean field with invalid value returns failure`() {
        val params = createParameters(
            "categoryData[afkable]" to listOf("maybe")
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
            "categoryData[productionRate]" to listOf("5000")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(5000, result["productionRate"])
    }

    @Test
    fun `percentage field with valid decimal returns success`() {
        val params = createParameters(
            "categoryData[hopperLockPercentage]" to listOf("0.75"),
            type = IdeaCategory.STORAGE
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals(0.75, result["hopperLockPercentage"])
    }

    @Test
    fun `percentage field outside range returns failure`() {
        val params = createParameters(
            "categoryData[hopperLockPercentage]" to listOf("1.5"),
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
            "categoryData[size][x][value]" to listOf("16"),
            "categoryData[size][y][value]" to listOf("10"),
            "categoryData[size][z][value]" to listOf("16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val size = result["size"] as Map<*, *>
        assertEquals("16", size["x"])
        assertEquals("10", size["y"])
        assertEquals("16", size["z"])
    }

    @Test
    fun `map field with invalid key option returns failure`() {
        val params = createParameters(
            "categoryData[size][width][value]" to listOf("16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Invalid key 'width'") == true
        })
    }

    @Test
    fun `map field with blank values are ignored`() {
        val params = createParameters(
            "categoryData[size][x][value]" to listOf("16"),
            "categoryData[size][y][value]" to listOf(""),
            "categoryData[size][z][value]" to listOf("16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val size = result["size"] as Map<*, *>
        assertEquals(2, size.size)
        assertEquals("16", size["x"])
        assertEquals("16", size["z"])
    }

    @Test
    fun `free-form map field with matching keys and values returns success`() {
        val params = createParameters(
            "categoryData[mobRequirements][key][]" to listOf("Zombie", "Skeleton"),
            "categoryData[mobRequirements][value][]" to listOf("10", "5")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val mobReqs = result["mobRequirements"] as Map<*, *>
        assertEquals("10", mobReqs["Zombie"])
        assertEquals("5", mobReqs["Skeleton"])
    }

    @Test
    fun `free-form map field with mismatched keys and values returns failure`() {
        val params = createParameters(
            "categoryData[mobRequirements][key][]" to listOf("Zombie", "Skeleton", "Creeper"),
            "categoryData[mobRequirements][value][]" to listOf("10", "5")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("mismatched key-value pairs") == true
        })
    }

    @Test
    fun `free-form map field filters out blank keys`() {
        val params = createParameters(
            "categoryData[mobRequirements][key][]" to listOf("Zombie", "", "Skeleton"),
            "categoryData[mobRequirements][value][]" to listOf("10", "3", "5")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val mobReqs = result["mobRequirements"] as Map<*, *>
        assertEquals(2, mobReqs.size)
        assertEquals("10", mobReqs["Zombie"])
        assertEquals("5", mobReqs["Skeleton"])
    }

    @Test
    fun `list field with allowed values returns success`() {
        val params = createParameters(
            "categoryData[playerSetup][]" to listOf("Looting III Sword", "Shield")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        val setup = result["playerSetup"] as List<*>
        assertEquals(2, setup.size)
        assertTrue(setup.contains("Looting III Sword"))
        assertTrue(setup.contains("Shield"))
    }

    @Test
    fun `list field with invalid allowed value returns failure`() {
        val params = createParameters(
            "categoryData[playerSetup][]" to listOf("Looting III Sword", "Invalid Item")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("Invalid values") == true
        })
    }

    @Test
    fun `list field without allowed values as comma-separated returns success`() {
        val params = createParameters(
            "categoryData[pros]" to listOf("Fast, Efficient, Cheap")
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
            "categoryData[farmVersion]" to listOf("v3.2"),
            "categoryData[productionRate]" to listOf("5000"),
            "categoryData[afkable]" to listOf("true"),
            "categoryData[playersRequired]" to listOf("1"),
            "categoryData[biomes][]" to listOf("Plains", "Forest"),
            "categoryData[size][x][value]" to listOf("16"),
            "categoryData[size][y][value]" to listOf("10"),
            "categoryData[size][z][value]" to listOf("16"),
            "categoryData[howToUse]" to listOf("Stand here and wait")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)

        assertEquals(7, result.size)
        assertEquals("v3.2", result["farmVersion"])
        assertEquals(5000, result["productionRate"])
        assertEquals(true, result["afkable"])
    }

    @Test
    fun `unknown field is ignored without error`() {
        val params = createParameters(
            "categoryData[unknownField]" to listOf("some value"),
            "categoryData[farmVersion]" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals(requiredFarmFields + 1, result.size)
        assertEquals("v3.2", result["farmVersion"])
    }

    @Test
    fun `non-category-data parameters are ignored`() {
        val params = createParameters(
            "title" to listOf("Iron Farm"),
            "description" to listOf("A simple iron farm"),
            "categoryData[farmVersion]" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals(requiredFarmFields + 1, result.size)
        assertEquals("v3.2", result["farmVersion"])
    }

    @Test
    fun `multi-select submitted as single value returns failure`() {
        val params = createParameters(
            "categoryData[biomes]" to listOf("Plains")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("multi-select") == true
        })
    }

    @Test
    fun `single value field submitted as array returns failure`() {
        val params = createParameters(
            "categoryData[farmVersion][]" to listOf("v3.2", "v3.3")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("not a multi-select or list field") == true
        })
    }

    @Test
    fun `map field submitted as single value returns failure`() {
        val params = createParameters(
            "categoryData[size]" to listOf("16x10x16")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.any {
            it is ValidationFailure.InvalidFormat && it.message?.contains("map field") == true
        })
    }

    @Test
    fun `empty parameters returns success with empty map when no required fields`() {
        val params = Parameters.Empty
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.STORAGE)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `malformed category data key is skipped with warning`() {
        val params = createParameters(
            "categoryData[invalid" to listOf("value"),
            "categoryData[farmVersion]" to listOf("v3.2")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertEquals(requiredFarmFields + 1, result.size)
    }

    @Test
    fun `multiple values for single value field returns failure`() {
        val params = createParameters(
            "categoryData[farmVersion]" to listOf("v3.2", "v3.3")
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
            "categoryData[biomes][]" to listOf("Plains", "", "Forest", "  ")
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
            "categoryData[playerSetup][]" to listOf("Looting III Sword", "Shield")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertTrue(result["playerSetup"] is List<*>)
    }

    @Test
    fun `multi-select returns set type`() {
        val params = createParameters(
            "categoryData[biomes][]" to listOf("Plains", "Forest")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertSuccess(step, params)
        assertIs<Set<*>>(result["biomes"])
    }

    @Test
    fun `all validation failures are collected before returning`() {
        val params = createParameters(
            "categoryData[yLevel]" to listOf("not-a-number"),
            "categoryData[playersRequired]" to listOf("invalid-option"),
            "categoryData[biomes][]" to listOf("InvalidBiome")
        )
        val step = ValidateIdeaCategoryDataStep(IdeaCategory.FARM)

        val result = TestUtils.executeAndAssertFailure(step, params)
        assertTrue(result.size >= 3)
    }
}

