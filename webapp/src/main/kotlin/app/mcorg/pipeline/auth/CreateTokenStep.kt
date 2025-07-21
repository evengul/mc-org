package app.mcorg.pipeline.auth

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.CreateTokenFailure
import app.mcorg.presentation.security.EIGHT_HOURS
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

data object CreateTokenStep : Step<TokenProfile, CreateTokenFailure, String> {
    override suspend fun process(input: TokenProfile): Result<CreateTokenFailure, String> {
        val (publicKey, privateKey) = JwtHelper.getKeys()

        try {
            val jwt = JWT.create()
                .withAudience(JwtHelper.AUDIENCE)
                .withIssuer("mcorg")
                .withClaim("sub", input.id)
                .withClaim("minecraft_username", input.minecraftUsername)
                .withClaim("minecraft_uuid", input.uuid)
                .withClaim("display_name", input.displayName)
                .withExpiresAt(Date(System.currentTimeMillis() + EIGHT_HOURS))
                .sign(Algorithm.RSA256(publicKey, privateKey)) as String
            return Result.success(jwt)
        } catch (e: Exception) {
            return Result.failure(CreateTokenFailure.CouldNotCreateToken(e))
        }
    }
}
