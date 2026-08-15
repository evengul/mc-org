package app.mcorg.domain

sealed interface Env

// `data object` for the generated toString: these end up in log lines (the Hikari pool profile
// names the env), and `app.mcorg.domain.Local@2e727f8` is not a useful thing to read there.
data object Local : Env
data object Test : Env
data object Production : Env
