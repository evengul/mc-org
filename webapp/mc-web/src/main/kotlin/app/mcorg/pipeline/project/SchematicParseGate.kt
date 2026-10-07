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
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger(SchematicParseGate::class.java)

/**
 * The one way mc-web parses an uploaded schematic: off the call thread, a bounded number at a time,
 * and for a bounded time (MCO-426).
 *
 * mc-nbt bounds what a *single* parse may cost, which is only as safe as the number of parses
 * running at once, and a parse on its own has no wall clock. The gate is where those two bounds
 * live.
 *
 * The parse is blocking, so it runs under [runInterruptible]: a timeout, or the client going away,
 * interrupts the thread, and the reader checks for that in its loops. Without it a cancelled
 * request would return while its parse ran on, keeping the IO thread and the permit.
 *
 * One [timeout] covers a whole upload: every file in it, and the waits for a permit between them.
 * A request queued behind slow parses is still holding its upload in memory, and a timeout per file
 * would let it do so for files x timeout.
 */
class SchematicParseGate(
    concurrentParses: Int,
    private val timeout: Duration,
    private val read: (ByteArray) -> Result<NBTFailure, Litematica> = LitematicaReader::readLitematica,
) {
    private val permits = Semaphore(concurrentParses)

    /** Parses the files of one upload in order, stopping at the first that fails. */
    suspend fun parse(files: List<ByteArray>): Result<FileParseFailure, List<Litematica>> {
        val parsed = ArrayList<Litematica>(files.size)
        var refused: FileParseFailure? = null

        val finished = withTimeoutOrNull(timeout) {
            for ((index, bytes) in files.withIndex()) {
                when (val result = permits.withPermit { runInterruptible(Dispatchers.IO) { read(bytes) } }) {
                    is Result.Success -> parsed += result.value
                    is Result.Failure -> {
                        logRefusal(bytes.size, result.error)
                        refused = FileParseFailure(index, SchematicParseFailure.Unreadable)
                        break
                    }
                }
            }
        }

        if (finished == null) {
            logger.info("Schematic parse timed out after {} on file {} of {}", timeout, parsed.size + 1, files.size)
            return Result.failure(FileParseFailure(parsed.size, SchematicParseFailure.TimedOut))
        }
        return refused?.let { Result.failure(it) } ?: Result.success(parsed)
    }

    /**
     * Says why a file was refused, so a legitimate one that hit an mc-nbt budget can be told apart
     * from a corrupt one, and the budget raised.
     *
     * The cause is safe to log: it is rendered from mc-nbt's own failure types and the messages of
     * its size guards and of `DataInputStream`, which carry tag types, lengths and offsets but never
     * a key or a string value from the file. Truncated, because a deeply nested failure renders one
     * level per compound.
     */
    private fun logRefusal(bytes: Int, failure: NBTFailure) {
        val cause = when (failure) {
            is NBTFailure.DeserializeError -> failure.cause.take(500)
            else -> failure.javaClass.simpleName
        }
        logger.info("Schematic refused ({} bytes): {}", bytes, cause)
    }

    companion object {
        /**
         * Sized from the heap, not the CPU. One parse may hold up to [NbtLimits.MAX_TREE_HEAP_BYTES]
         * (128 MB charged, ~165 MB retained) of tree, plus its arrays and the upload itself; two of
         * them keep the worst case under half of the 768 MB heap. A third parse would buy nothing
         * anyway: production has one shared vCPU, and these are CPU-bound.
         */
        const val MAX_CONCURRENT_PARSES = 2

        /**
         * A real file parses in milliseconds, and the worst a file inside every mc-nbt budget
         * managed was about a second. Fifteen covers a full upload of those behind two others, and
         * still frees a stuck request well before a user gives up on it.
         */
        val TIMEOUT: Duration = 15.seconds

        val shared = SchematicParseGate(MAX_CONCURRENT_PARSES, TIMEOUT)
    }
}

/** Which file of an upload failed, by its position in the list given to [SchematicParseGate.parse]. */
data class FileParseFailure(val index: Int, val failure: SchematicParseFailure)

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
