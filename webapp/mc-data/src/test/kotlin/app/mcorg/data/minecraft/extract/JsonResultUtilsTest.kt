package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsonResultUtilsTest {

    @Test
    fun `objectResult succeeds for JsonObject`() {
        val json = Json.parseToJsonElement("""{"key": "value"}""")
        val result = json.objectResult("test.json")
        assertIs<JsonObject>(assertResultSuccess(result))
    }

    @Test
    fun `objectResult fails for JsonPrimitive`() {
        val json = Json.parseToJsonElement(""""hello"""")
        val error = assertResultFailure(json.objectResult("test.json"))
        assertIs<ExtractionFailure.JsonFailure.NotAnObject>(error)
    }

    @Test
    fun `objectResult fails for JsonArray`() {
        val json = Json.parseToJsonElement("""[1, 2, 3]""")
        val error = assertResultFailure(json.objectResult("test.json"))
        assertIs<ExtractionFailure.JsonFailure.NotAnObject>(error)
    }

    @Test
    fun `arrayResult succeeds for JsonArray`() {
        val json = Json.parseToJsonElement("""[1, 2, 3]""")
        val result = json.arrayResult("test.json")
        assertIs<JsonArray>(assertResultSuccess(result))
    }

    @Test
    fun `arrayResult fails for JsonObject`() {
        val json = Json.parseToJsonElement("""{"key": "value"}""")
        val error = assertResultFailure(json.arrayResult("test.json"))
        assertIs<ExtractionFailure.JsonFailure.NotAnArray>(error)
    }

    @Test
    fun `primitiveResult succeeds for JsonPrimitive`() {
        val json = Json.parseToJsonElement(""""hello"""")
        val result = json.primitiveResult("test.json")
        assertIs<JsonPrimitive>(assertResultSuccess(result))
    }

    @Test
    fun `primitiveResult fails for JsonObject`() {
        val json = Json.parseToJsonElement("""{"key": "value"}""")
        val error = assertResultFailure(json.primitiveResult("test.json"))
        assertIs<ExtractionFailure.JsonFailure.NotAPrimitive>(error)
    }

    @Test
    fun `getResult succeeds for existing key`() {
        val json = Json.parseToJsonElement("""{"key": "value"}""") as JsonObject
        val result = json.getResult("key", "test.json")
        val value = assertResultSuccess(result)
        assertIs<JsonPrimitive>(value)
        assertEquals("value", (value as JsonPrimitive).content)
    }

    @Test
    fun `getResult fails for missing key`() {
        val json = Json.parseToJsonElement("""{"key": "value"}""") as JsonObject
        val error = assertResultFailure(json.getResult("missing", "test.json"))
        assertIs<ExtractionFailure.JsonFailure.KeyNotFound>(error)
        assertEquals("missing", (error as ExtractionFailure.JsonFailure.KeyNotFound).key)
    }

    @Test
    fun `getResult succeeds for nested values`() {
        val json = Json.parseToJsonElement("""{"outer": {"inner": 42}}""") as JsonObject
        val outer = assertResultSuccess(json.getResult("outer", "test.json"))
        assertIs<JsonObject>(outer)
    }
}
