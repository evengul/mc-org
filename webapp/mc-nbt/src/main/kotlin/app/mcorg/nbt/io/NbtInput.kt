package app.mcorg.nbt.io

import app.mcorg.pipeline.Result
import app.mcorg.nbt.tag.NamedTag
import app.mcorg.nbt.tag.Tag

interface NbtInput<T> {
    fun readTag(maxDepth: Int): Result<BinaryParseFailure, NamedTag<*>>

    fun readRawTag(maxDepth: Int): Result<BinaryParseFailure, Tag<*>>
}
