package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.*
import org.slf4j.LoggerFactory

data class ConvertTokenStep(val issuer: String = ISSUER) : Step<String, AppFailure.AuthError.ConvertTokenError, TokenProfile> {
    override suspend fun process(input: String): Result<AppFailure.AuthError.ConvertTokenError, TokenProfile> {
        val logger = LoggerFactory.getLogger(ConvertTokenStep::class.java)

        if (input.trim().isBlank()) {
            return Result.failure(AppFailure.AuthError.ConvertTokenError.invalidToken())
        }

        val (publicKey, privateKey) = JwtHelper.getKeys()

        val jwt = try {
            JWT.require(Algorithm.RSA256(publicKey, privateKey))
                .withIssuer(issuer)
                .withAudience(JwtHelper.AUDIENCE)
                .withClaimPresence("sub")
                .withClaimPresence("minecraft_username")
                .withClaimPresence("minecraft_uuid")
                .withClaimPresence("display_name")
                .withClaimPresence("roles")
                .acceptLeeway(3L)
                .build()
                .verify(input)
        } catch (e: Exception) {
            return when(e) {
                is AlgorithmMismatchException,
                is SignatureVerificationException -> Result.failure(AppFailure.AuthError.ConvertTokenError.invalidToken())
                is TokenExpiredException -> Result.failure(AppFailure.AuthError.ConvertTokenError.expiredToken())
                is MissingClaimException -> Result.failure(AppFailure.AuthError.ConvertTokenError.missingClaim(e.claimName))
                is IncorrectClaimException -> Result.failure(AppFailure.AuthError.ConvertTokenError.incorrectClaim(e.claimName, e.claimValue.asString()))
                else -> {
                    logger.error("Could not convert token", e)
                    Result.failure(AppFailure.AuthError.ConvertTokenError.conversionError())
                }
            }
        }

        return Result.success(TokenProfile(
            jwt.getClaim("sub").asInt(),
            jwt.getClaim("minecraft_uuid").asString(),
            jwt.getClaim("minecraft_username").asString(),
            jwt.getClaim("display_name").asString(),
            jwt.getClaim("roles").asList(String::class.java) ?: emptyList()
        ))
    }
}