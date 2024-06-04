package app.mcorg.domain

fun Task.isDone(): Boolean {
    return done >= needed
}

fun Task.isCountable(): Boolean {
    return needed > 1
}