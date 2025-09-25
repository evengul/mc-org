package app.mcorg.presentation.security

import app.mcorg.config.AppConfig
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

const val EIGHT_HOURS = 8 * 60 * 60 * 1000

class JwtHelper {
    companion object {
        const val AUDIENCE = "mcorg-webapp"
    }
}

fun JwtHelper.Companion.getKeys(): Pair<RSAPublicKey, RSAPrivateKey> {
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

private fun JwtHelper.Companion.readPrivateKey(): String {
    return AppConfig.rsaPrivateKey ?: readKey("private_key.pem")
}

private fun JwtHelper.Companion.readPublicKey(): String {
    return AppConfig.rsaPublicKey ?: readKey("public_key.pem")
}

private fun JwtHelper.Companion.readKey(filename: String) =
    object {}.javaClass.getResourceAsStream("/keys/$filename")
        ?.bufferedReader()
        ?.readLines()
        ?.joinToString(System.lineSeparator())
    ?: throw IllegalStateException("Could not read key file $filename")