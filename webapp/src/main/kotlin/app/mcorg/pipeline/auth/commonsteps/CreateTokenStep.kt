package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.security.EIGHT_HOURS
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory
import java.util.*

data object CreateTokenStep : Step<TokenProfile, AppFailure.AuthError.CouldNotCreateToken, String> {
    override suspend fun process(input: TokenProfile): Result<AppFailure.AuthError.CouldNotCreateToken, String> {
        val (publicKey, privateKey) = JwtHelper.getKeys()
        val logger = LoggerFactory.getLogger(CreateTokenStep.javaClass)

        try {
            val jwt = JWT.create()
                .withAudience(JwtHelper.AUDIENCE)
                .withIssuer("mcorg")
                .withClaim("sub", input.id)
                .withClaim("minecraft_username", input.minecraftUsername)
                .withClaim("minecraft_uuid", input.uuid)
                .withClaim("display_name", input.displayName)
                .withClaim("roles", input.roles)
                .withExpiresAt(Date(System.currentTimeMillis() + EIGHT_HOURS))
                .sign(Algorithm.RSA256(publicKey, privateKey)) as String
            return Result.success(jwt)
        } catch (e: Exception) {
            logger.error("Could not create JWT token for user ${input.minecraftUsername} (${input.id})", e)
            return Result.failure(AppFailure.AuthError.CouldNotCreateToken)
        }
    }
}
