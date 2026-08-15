package app.mcorg.pipeline.auth.domain

import app.mcorg.logging.redacted

data class TokenData(
    val token: String,
    val hash: String
) {
    // MCO-340: both fields are credentials — the token itself, and the hash it is looked up by.
    override fun toString() = "TokenData(token=${redacted(token)}, hash=${redacted(hash)})"
}
