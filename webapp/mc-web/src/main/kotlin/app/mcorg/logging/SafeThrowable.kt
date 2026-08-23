package app.mcorg.logging

/**
 * Renders a throwable for a log line without its messages.
 *
 * Ktor's `logError(call, cause)` and a bare `logger.error("...", e)` both hand the throwable to
 * slf4j, and the rendered stack trace **starts with `getMessage()`** — so per
 * [documentation/logging.md] they leak exactly as much as interpolating the message would. Two
 * libraries in this codebase put payloads in that message: PostgreSQL appends
 * `DETAIL: Key (column)=(value)`, and kotlinx-serialization appends the whole JSON input. NBT and
 * schematic parse failures can carry an uploaded filename.
 *
 * What survives here is what you actually debug from — the exception types and the code locations
 * — with only the messages removed. The cause chain is walked, so a wrapper around a leaking
 * exception does not sneak through.
 *
 * Prefer a specific, structured line where the failure type is known ([app.mcorg.pipeline]'s
 * database logging does this). This exists for the boundary that catches *anything*, where the
 * type is by definition unknown.
 */
fun Throwable.describeWithoutMessages(maxFrames: Int = 12): String = buildString {
    var current: Throwable? = this@describeWithoutMessages
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (depth > 0) append(" caused by ")
        append(current::class.qualifiedName ?: current::class.simpleName ?: "UnknownThrowable")
        current = current.cause
        depth++
    }

    val frames = this@describeWithoutMessages.stackTrace
    frames.take(maxFrames).forEach { append("\n\tat ").append(it) }
    if (frames.size > maxFrames) append("\n\t... ").append(frames.size - maxFrames).append(" more")
}

private const val MAX_CAUSE_DEPTH = 8
