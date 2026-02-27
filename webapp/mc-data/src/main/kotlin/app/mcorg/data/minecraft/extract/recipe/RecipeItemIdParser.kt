package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonElement

object RecipeItemIdParser {
    suspend fun parse(json: JsonElement, filename: String): Result<ExtractionFailure, String> {
        return json.objectResult(filename).flatMap { it.getResult("id", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("result", filename) }
                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("result", filename) }
                    .flatMap { it.objectResult(filename) }
                    .flatMap { it.getResult("value", filename) }
                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("result", filename) }
                    .flatMap { it.objectResult(filename) }
                    .flatMap { it.getResult("id", filename) }
                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("result", filename) }
                    .flatMap { it.objectResult(filename) }
                    .flatMap { it.getResult("key", filename) }
                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("result", filename) }
                    .flatMap { it.objectResult(filename) }
                    .flatMap { it.getResult("item", filename) }
                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("result", filename) }
                    .flatMap { it.objectResult(filename) }
                    .flatMap { it.getResult("value", filename) }
                    .flatMap { it.objectResult(filename) }
                    .flatMap { it.getResult("id", filename) }
                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            }
    }
}
