package app.mcorg.domain

interface Env

object Local : Env
object Test : Env
object Prod : Env