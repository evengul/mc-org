package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.GetSignInPageFailure

data class RedirectStep<I>(val getPath: (I) -> String): Step<I, GetSignInPageFailure, String> {
    override fun process(input: I): Result<GetSignInPageFailure, String> {
        return Result.success(getPath(input))
    }
}