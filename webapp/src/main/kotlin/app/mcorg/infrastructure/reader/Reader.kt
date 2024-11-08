package app.mcorg.infrastructure.reader

open class Reader {
    val contextClassLoader: ClassLoader
            get() = Thread.currentThread().contextClassLoader
}
