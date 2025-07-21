package app.mcorg.pipeline.auth

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ConvertTokenStepFailure
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.AlgorithmMismatchException
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.auth0.jwt.exceptions.MissingClaimException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException

data class ConvertTokenStep(val issuer: String = ISSUER) : Step<String, ConvertTokenStepFailure, TokenProfile> {
    override suspend fun process(input: String): Result<ConvertTokenStepFailure, TokenProfile> {
        val (publicKey, privateKey) = JwtHelper.getKeys()

        val jwt = try {
            JWT.require(Algorithm.RSA256(publicKey, privateKey))
                .withIssuer(issuer)
                .withAudience(JwtHelper.AUDIENCE)
                .withClaimPresence("sub")
                .withClaimPresence("minecraft_username")
                .withClaimPresence("minecraft_uuid")
                .withClaimPresence("display_name")
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

        return Result.success(TokenProfile(
            jwt.getClaim("sub").asInt(),
            jwt.getClaim("minecraft_uuid").asString(),
            jwt.getClaim("minecraft_username").asString(),
            jwt.getClaim("display_name").asString()
        ))
    }
}