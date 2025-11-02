package app.mcorg.domain

sealed interface Env

object Local : Env
object Test : Env
object Production : Env