package app.mcorg.infrastructure.reader

import app.mcorg.domain.minecraft.Item
import app.mcorg.domain.minecraft.ItemApi

class ItemReader : ItemApi, NameReader("item.minecraft.") {
    override fun getItems(): List<Item> {
        return getValues()
            .map { Item(it.second) }
    }
}