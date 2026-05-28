package app.mcorg.pipeline.idea

data class PaginatedResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
) {
    val totalPages: Int get() = if (totalCount == 0) 1 else (totalCount + pageSize - 1) / pageSize
    val hasPreviousPage: Boolean get() = page > 1
    val hasNextPage: Boolean get() = page < totalPages
}
