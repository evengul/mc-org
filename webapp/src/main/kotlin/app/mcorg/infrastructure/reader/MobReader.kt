package app.mcorg.infrastructure.reader

import app.mcorg.domain.minecraft.MobApi

class MobReader : MobApi, NameReader("entity.minecraft.") {
    override fun getMobs(): List<String> {
        return getNames()
            .map { it.second }
    }
}