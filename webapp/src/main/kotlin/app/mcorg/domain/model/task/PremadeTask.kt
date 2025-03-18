package app.mcorg.domain.model.task

import java.io.InputStream

data class PremadeTask(val name: String, val needed: Int) {
    companion object {
        fun from(stream: InputStream) =
            stream.validateMaterialList()
            .getMaterialLines()
            .map { it.materialLineToTask() }

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
    }
}