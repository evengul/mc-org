package app.mcorg.infrastructure.reader.itemsource

import app.mcorg.infrastructure.reader.FileReader

object ItemSourceReader {
    fun readItemSources(): ItemSourcesDTO {
        return FileReader.readJson("/minecraft-data/1.21.4.json", ItemSourcesDTO::class.java)
    }
}