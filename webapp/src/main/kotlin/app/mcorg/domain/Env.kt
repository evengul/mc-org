package app.mcorg.domain

interface Env

object Local : Env
object Test : Env
object Production : Env