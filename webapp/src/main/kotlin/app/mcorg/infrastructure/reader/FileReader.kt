package app.mcorg.infrastructure.reader

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

class FileReader<O>(
    private val path: String
) {

    private val kotlinMapper = KotlinModule.Builder().build()

    private val mapper = ObjectMapper().registerModule(kotlinMapper)

    fun readJson(clazz: Class<O>): O {
        return mapper.readValue(readContent(), clazz)
    }

    fun readContent(): String {
        return this::class.java.getResource(path)!!.readText()
    }

    companion object {
        fun <O> readJson(path: String, clazz: Class<O>): O {
            return FileReader<O>(path).readJson(clazz)
        }
    }
}