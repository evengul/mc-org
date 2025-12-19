package app.mcorg.nbt.io

import app.mcorg.domain.pipeline.Result

sealed interface MaxDepthFailure {
    object NegativeDepth : MaxDepthFailure
    object MaxDepthReached : MaxDepthFailure
}

interface MaxDepthIO {
    fun decrementMaxDepth(maxDepth: Int): Result<MaxDepthFailure, Int> {
        return if (maxDepth < 0) {
            Result.failure(MaxDepthFailure.NegativeDepth)
        } else if (maxDepth == 0) {
            Result.failure(MaxDepthFailure.MaxDepthReached)
        } else {
            Result.success(maxDepth - 1)
        }
    }
}