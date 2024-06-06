package app.mcorg.domain

fun Task.isDone(): Boolean {
    return done >= needed
}

fun Task.isCountable(): Boolean {
    return needed > 1
}

fun Project.doable() = tasks.filter { !it.isCountable() }
fun Project.countable() = tasks.filter { it.isCountable() }