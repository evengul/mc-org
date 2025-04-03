package app.mcorg.pipeline.auth

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.AlgorithmMismatchException
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.auth0.jwt.exceptions.MissingClaimException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException

sealed interface ConvertTokenStepFailure : GetSignInPageFailure, AuthPluginFailure {
    data object InvalidToken : ConvertTokenStepFailure
    data object ExpiredToken : ConvertTokenStepFailure
    data class MissingClaim(val claimName: String) : ConvertTokenStepFailure
    data class IncorrectClaim(val claimName: String, val claimValue: String) : ConvertTokenStepFailure
    data class ConversionError(val error: Exception) : ConvertTokenStepFailure
}

data class ConvertTokenStep(val issuer: String) : Step<String, ConvertTokenStepFailure, User> {
    override fun process(input: String): Result<ConvertTokenStepFailure, User> {
        val (publicKey, privateKey) = JwtHelper.getKeys()

        val jwt = try {
            JWT.require(Algorithm.RSA256(publicKey, privateKey))
                .withIssuer(issuer)
                .withAudience(JwtHelper.AUDIENCE)
                .withClaimPresence("sub")
                .withClaimPresence("username")
                .acceptLeeway(3L)
                .build()
                .verify(input)
        } catch (e: Exception) {
            return when(e) {
                is AlgorithmMismatchException -> Result.failure(ConvertTokenStepFailure.InvalidToken)
                is SignatureVerificationException -> Result.failure(ConvertTokenStepFailure.InvalidToken)
                is TokenExpiredException -> Result.failure(ConvertTokenStepFailure.ExpiredToken)
                is MissingClaimException -> Result.failure(ConvertTokenStepFailure.MissingClaim(e.claimName))
                is IncorrectClaimException -> Result.failure(ConvertTokenStepFailure.IncorrectClaim(e.claimName, e.claimValue.toString()))
                else -> Result.failure(ConvertTokenStepFailure.ConversionError(e))
            }
        }

        return Result.success(User(
            jwt.getClaim("sub").asInt(),
            jwt.getClaim("username").asString()
        ))
    }
}