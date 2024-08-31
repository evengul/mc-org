package app.mcorg.domain

import java.io.InputStream

fun Task.isDone(): Boolean {
    return done >= needed
}

fun Task.isCountable(): Boolean {
    return taskType == TaskType.COUNTABLE
}

fun Project.doable() = tasks.filter { !it.isCountable() }
fun Project.countable() = tasks.filter { it.isCountable() }

fun InputStream.tasksFromMaterialList(): List<PremadeTask> {
    return validateMaterialList()
        .getMaterialLines()
        .map { it.materialLineToTask() }
}

private fun String.materialLineToTask(): PremadeTask {
    val pieces = split("|").map { it.trim() }.filter { it.isNotBlank() }
    val name = pieces[0]
    val amount = pieces[1].toInt()

    return PremadeTask(name, amount)
}

private fun String.getMaterialLines(): List<String> {
    return this.split(System.lineSeparator().toRegex())
        .asSequence()
        .map { it.trim() }
        .filter { !it.contains("+") }
        .filter { !it.contains("Item") }
        .filter { !it.contains("Material List") }
        .filter { it.isNotBlank() }
        .toList()
}

private fun InputStream.validateMaterialList(): String {
    return readAllBytes().toString(Charsets.UTF_8)
}