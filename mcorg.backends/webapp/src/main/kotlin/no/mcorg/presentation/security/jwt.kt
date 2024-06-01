package no.mcorg.presentation.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import no.mcorg.domain.User
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

const val EIGHT_HOURS = 8 * 60 * 60 * 1000;

fun getUserFromJwtToken(token: String): User {
    val jwt = validateSignature(token)

    return User(jwt.getClaim("sub").asInt(), jwt.getClaim("username").asString())
}

private fun validateSignature(token: String): DecodedJWT {
    val (publicKey, privateKey) = getKeys()
    return JWT.require(Algorithm.RSA256(publicKey, privateKey))
        .withIssuer("http://localhost:8080")
        .withAudience("mcorg-webapp")
        .acceptLeeway(3L)
        .build()
        .verify(token)
}

fun createSignedJwtToken(user: User): String {

    val (publicKey, privateKey) = getKeys()

    return JWT.create()
        .withAudience("mcorg-webapp")
        .withIssuer("http://localhost:8080")
        .withClaim("sub", user.id)
        .withClaim("username", user.username)
        .withExpiresAt(Date(System.currentTimeMillis() + EIGHT_HOURS))
        .sign(Algorithm.RSA256(publicKey, privateKey)) as String
}

private fun getKeys(): Pair<RSAPublicKey, RSAPrivateKey> {
    val privateKeyStr = readKey("private_key.pem")
        .replace("\n", "")
        .replace("\r", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
    val publicKeyStr = readKey("public_key.pem")
        .replace("\n", "")
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

private fun readKey(filename: String): String {
    return readAllBytes(filename.toKeysPath()).toString(Charset.defaultCharset())
}

private fun String.toKeysPath(): Path {
    return Paths.get("src", "main", "resources", "keys", this)
}