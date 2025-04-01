package app.mcorg.model

interface Env

object Local : Env
object Test : Env
object Prod : Env