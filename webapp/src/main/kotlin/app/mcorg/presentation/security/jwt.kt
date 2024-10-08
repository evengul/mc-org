package app.mcorg.presentation.security

import app.mcorg.domain.User
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.nio.charset.Charset
import java.nio.file.Files.readAllBytes
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

const val EIGHT_HOURS = 8 * 60 * 60 * 1000

private const val audience = "mcorg-webapp"

private fun getIssuer() = System.getenv("JWT_ISSUER") ?: "http://localhost:8080"

fun getUserFromJwtToken(token: String): User {
    val jwt = validateSignature(token)

    return User(jwt.getClaim("sub").asInt(), jwt.getClaim("username").asString())
}

private fun validateSignature(token: String): DecodedJWT {
    val (publicKey, privateKey) = getKeys()
    return JWT.require(Algorithm.RSA256(publicKey, privateKey))
        .withIssuer(getIssuer())
        .withAudience(audience)
        .acceptLeeway(3L)
        .build()
        .verify(token)
}

fun createSignedJwtToken(user: User): String {

    val (publicKey, privateKey) = getKeys()

    return JWT.create()
        .withAudience(audience)
        .withIssuer(getIssuer())
        .withClaim("sub", user.id)
        .withClaim("username", user.username)
        .withExpiresAt(Date(System.currentTimeMillis() + EIGHT_HOURS))
        .sign(Algorithm.RSA256(publicKey, privateKey)) as String
}

private fun getKeys(): Pair<RSAPublicKey, RSAPrivateKey> {
    val privateKeyStr = readPrivateKey()
        .replace("\\n", "")
        .replace("\n", "")
        .replace("\\r", "")
        .replace("\r", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
    val publicKeyStr = readPublicKey()
        .replace("\\n", "")
        .replace("\n", "")
        .replace("\\r", "")
        .replace("\r", "")
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")

    val keyFactory = KeyFactory.getInstance("RSA")

    val keySpecPKCS8 = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyStr))
    val privateKey = keyFactory.generatePrivate(keySpecPKCS8) as RSAPrivateKey

    val x509EncodedKeySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyStr))
    val publicKey = keyFactory.generatePublic(x509EncodedKeySpec) as RSAPublicKey

    return publicKey to privateKey
}

private fun readPrivateKey(): String {
    return System.getenv("RSA_PRIVATE_KEY") ?: readKey("private_key.pem")
}

private fun readPublicKey(): String {
    return System.getenv("RSA_PUBLIC_KEY") ?: readKey("public_key.pem")
}

private fun readKey(filename: String): String {
    return readAllBytes(filename.toKeysPath()).toString(Charset.defaultCharset())
}

private fun String.toKeysPath(): Path {
    return Paths.get("src", "main", "resources", "keys", this)
}