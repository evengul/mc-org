package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.JsonElement

object RecipeItemIdParser {
    suspend fun parse(json: JsonElement, filename: String): Result<AppFailure, String> {
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