package app.mcorg.pipeline.auth

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.AlgorithmMismatchException
import com.auth0.jwt.exceptions.MissingClaimException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException

data class ConvertCookieStep(val issuer: String) : Step<String, ConvertCookieStepFailure, User> {
    override fun process(input: String): Result<ConvertCookieStepFailure, User> {
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
                is AlgorithmMismatchException -> Result.failure(ConvertCookieStepFailure.InvalidToken)
                is SignatureVerificationException -> Result.failure(ConvertCookieStepFailure.InvalidToken)
                is TokenExpiredException -> Result.failure(ConvertCookieStepFailure.ExpiredToken)
                is MissingClaimException -> Result.failure(ConvertCookieStepFailure.MissingClaim(e.claimName))
                else -> Result.failure(ConvertCookieStepFailure.ConversionError(e))
            }
        }

        return Result.success(User(
            jwt.getClaim("sub").asInt(),
            jwt.getClaim("username").asString()
        ))
    }
}