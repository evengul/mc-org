package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.nbt.failure.NBTFailure
import app.mcorg.nbt.io.NbtLimits
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one way mc-web parses an uploaded schematic: off the call thread, a bounded number at a time,
 * and for a bounded time (MCO-426).
 *
 * mc-nbt bounds what a *single* parse may cost. Nothing bounded how many ran at once, so the
 * per-parse budget was only as safe as the number of concurrent uploads: forty-odd at once could
 * still exhaust the heap, and a slow one held an IO thread for as long as it liked. The gate is
 * where those two bounds live.
 *
 * The parse is blocking, so it runs under [runInterruptible]: a timeout, or the client going away,
 * interrupts the thread, and the reader checks for that in its loops. Without it a cancelled
 * request would return while its parse ran on, keeping the IO thread and the permit.
 *
 * The timeout covers waiting for a permit as well as parsing. A request queued behind two slow
 * parses is still holding its upload in memory, and the timeout is what bounds that too.
 */
class SchematicParseGate(
    concurrentParses: Int,
    private val timeout: Duration,
    private val read: (ByteArray) -> Result<NBTFailure, Litematica> = LitematicaReader::readLitematica,
) {
    private val permits = Semaphore(concurrentParses)

    suspend fun parse(bytes: ByteArray): Result<SchematicParseFailure, Litematica> {
        val result = withTimeoutOrNull(timeout) {
            permits.withPermit { runInterruptible(Dispatchers.IO) { read(bytes) } }
        } ?: return Result.failure(SchematicParseFailure.TimedOut)

        return when (result) {
            is Result.Success -> result
            is Result.Failure -> Result.failure(SchematicParseFailure.Unreadable)
        }
    }

    companion object {
        /**
         * Sized from the heap, not the CPU. One parse may hold up to [NbtLimits.MAX_TREE_HEAP_BYTES]
         * (128 MB) of tree, plus its arrays and the upload itself; two of them keep the worst case
         * near a third of the 768 MB heap. A third parse would buy nothing anyway: production has
         * one shared vCPU, and these are CPU-bound.
         */
        const val MAX_CONCURRENT_PARSES = 2

        /**
         * A real file parses in milliseconds, and the worst a file inside every mc-nbt budget
         * managed was about a second. Fifteen covers that twice over plus a wait behind two others,
         * and still frees a stuck request well before a user gives up on it.
         */
        val TIMEOUT: Duration = 15.seconds

        val shared = SchematicParseGate(MAX_CONCURRENT_PARSES, TIMEOUT)
    }
}

sealed interface SchematicParseFailure {
    /** What to tell the user, naming the file, since an upload can carry several. */
    fun describe(fileName: String): String

    data object Unreadable : SchematicParseFailure {
        override fun describe(fileName: String) = "Could not read $fileName"
    }

    data object TimedOut : SchematicParseFailure {
        override fun describe(fileName: String) =
            "Reading $fileName took too long. Try again in a moment, or import the build in smaller pieces."
    }
}
