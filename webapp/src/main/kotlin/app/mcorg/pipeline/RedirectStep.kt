package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.AuthFailure

data class RedirectStep<I>(val getPath: (I) -> String): Step<I, AuthFailure, String> {
    override fun process(input: I): Result<AuthFailure, String> {
        return Result.success(getPath(input))
    }
}