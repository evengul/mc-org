package app.mcorg.test

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.CreateUserIfNotExistsStep
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.fail

open class WithUser {
    protected val user: TokenProfile by lazy {
        runBlocking {
            when (val result = CreateUserIfNotExistsStep.process(
                MinecraftProfile(
                    "uuid", "mcname"
                )
            )) {
                is Result.Failure -> fail("Could not create test user: $result")
                is Result.Success -> result.value
            }
        }
    }
}