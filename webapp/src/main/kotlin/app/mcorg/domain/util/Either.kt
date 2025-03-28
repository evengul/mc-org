package app.mcorg.domain.util

sealed interface Either<out L, out R> {
    fun <T> fold(ifLeft: (L) -> T, ifRight: (R) -> T): T

    companion object {
        fun <L, R> left(value: L): Either<L, R> = Left(value)
        fun <L, R> right(value: R): Either<L, R> = Right(value)
    }
}

data class Left<out L, out R>(val value: L) : Either<L, R> {
    override fun <T> fold(ifLeft: (L) -> T, ifRight: (R) -> T): T = ifLeft(value)
}

data class Right<out L, out R>(val value: R) : Either<L, R> {
    override fun <T> fold(ifLeft: (L) -> T, ifRight: (R) -> T): T = ifRight(value)
}