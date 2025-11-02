package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
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

sealed interface ConvertTokenStepFailure {
    val errorCode: String
    val arguments: List<Pair<String, String>>?

    fun toSignOutQueryParameters(): String {
        val base = "error=$errorCode"
        val args = arguments?.joinToString("&") { (key, value) -> "$key=$value" } ?: ""
        return if (args.isNotBlank()) {
            "$base&$args"
        } else {
            base
        }
    }

    fun toSignOutUrl(): String {
        return "/auth/sign-out?${toSignOutQueryParameters()}"
    }

    object InvalidToken : ConvertTokenStepFailure {
        override val errorCode: String = "invalid_token"
        override val arguments: List<Pair<String, String>>? = null
    }
    object ExpiredToken : ConvertTokenStepFailure {
        override val errorCode: String = "expired_token"
        override val arguments: List<Pair<String, String>>? = null
    }
    data class MissingClaim(val claimName: String) : ConvertTokenStepFailure {
        override val errorCode: String = "missing_claim"
        override val arguments: List<Pair<String, String>> = listOf("claim" to claimName)
    }
    data class IncorrectClaim(val claimName: String, val claimValue: String) : ConvertTokenStepFailure {
        override val errorCode: String = "incorrect_claim"
        override val arguments: List<Pair<String, String>> = listOf(
            "claim" to claimName,
            "value" to claimValue
        )
    }
    data class ConversionError(val error: Exception) : ConvertTokenStepFailure {
        override val errorCode: String = "conversion_error"
        override val arguments: List<Pair<String, String>>? = null
    }
}

data class ConvertTokenStep(val issuer: String = ISSUER) : Step<String, ConvertTokenStepFailure, TokenProfile> {
    override suspend fun process(input: String): Result<ConvertTokenStepFailure, TokenProfile> {
        if (input.trim().isBlank()) {
            return Result.failure(ConvertTokenStepFailure.InvalidToken)
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
                is AlgorithmMismatchException -> Result.failure(ConvertTokenStepFailure.InvalidToken)
                is SignatureVerificationException -> Result.failure(ConvertTokenStepFailure.InvalidToken)
                is TokenExpiredException -> Result.failure(ConvertTokenStepFailure.ExpiredToken)
                is MissingClaimException -> Result.failure(ConvertTokenStepFailure.MissingClaim(e.claimName))
                is IncorrectClaimException -> Result.failure(ConvertTokenStepFailure.IncorrectClaim(e.claimName, e.claimValue.asString()))
                else -> Result.failure(ConvertTokenStepFailure.ConversionError(e))
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