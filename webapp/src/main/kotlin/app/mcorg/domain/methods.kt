package app.mcorg.domain

import java.io.InputStream

fun WorldVersion.toVersionString(): String = "1.${main}.${secondary}"

fun Task.isDone(): Boolean {
    return done >= needed
}

fun Task.isCountable(): Boolean {
    return taskType == TaskType.COUNTABLE
}

fun Project.doable() = tasks.filter { !it.isCountable() }
fun Project.countable() = tasks.filter { it.isCountable() }

fun Project.toSlim() = SlimProject(
    worldId = worldId,
    id = id,
    name = name,
    priority = priority,
    dimension = dimension,
    assignee = assignee,
    progress = progress
)

fun Profile.toUser() = User(id, username)

fun InputStream.tasksFromMaterialList(): List<PremadeTask> {
    return validateMaterialList()
        .getMaterialLines()
        .map { it.materialLineToTask() }
}

fun ContraptionVersion.matches(version: ContraptionVersion): Boolean {
    if (this.allAbove) {
        return this.main <= version.main && (this.secondary == null || this.secondary <= (version.secondary ?: 0))
    }
    return this.main == version.main && (this.secondary == null || this.secondary == version.secondary)
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