package app.mcorg.domain.pipeline

import kotlinx.coroutines.coroutineScope

class ParallelPipelineBuilder<E> {
    private val nodes = mutableMapOf<String, PipelineNode<E, *>>()
    private val dependencies = mutableMapOf<String, Set<String>>()

    fun <I, O> singleStep(
        id: String,
        input: I,
        step: Step<I, E, O>
    ): PipelineRef<O> {
        nodes[id] = PipelineNode.Single(input, Pipeline.create<E, I>().pipe(step))
        dependencies[id] = emptySet()
        return PipelineRef(id)
    }

    fun <I, O> pipeline(
        id: String,
        input: I,
        pipeline: Pipeline<I, E, O>
    ): PipelineRef<O> {
        nodes[id] = PipelineNode.Single(input, pipeline)
        dependencies[id] = emptySet()
        return PipelineRef(id)
    }

    fun <O> merge(
        id: String,
        dependencies: List<PipelineRef<*>>,
        merger: suspend (Map<String, Any>) -> Result<E, O>
    ): PipelineRef<O> {
        this.nodes[id] = PipelineNode.Merge(merger)
        this.dependencies[id] = dependencies.map { it.id }.toSet()
        return PipelineRef(id)
    }

    fun <A, B, O> merge(
        id: String,
        depA: PipelineRef<A>,
        depB: PipelineRef<B>,
        merger: suspend (A, B) -> Result<E, O>
    ): PipelineRef<O> {
        nodes[id] = PipelineNode.Merge { results ->
            @Suppress("UNCHECKED_CAST")
            val a = results[depA.id] as A
            @Suppress("UNCHECKED_CAST")
            val b = results[depB.id] as B
            merger(a, b)
        }
        dependencies[id] = setOf(depA.id, depB.id)
        return PipelineRef(id)
    }

    fun <A, B, C, O> merge(
        id: String,
        depA: PipelineRef<A>,
        depB: PipelineRef<B>,
        depC: PipelineRef<C>,
        merger: suspend (A, B, C) -> Result<E, O>
    ): PipelineRef<O> {
        nodes[id] = PipelineNode.Merge { results ->
            @Suppress("UNCHECKED_CAST")
            val a = results[depA.id] as A
            @Suppress("UNCHECKED_CAST")
            val b = results[depB.id] as B
            @Suppress("UNCHECKED_CAST")
            val c = results[depC.id] as C
            merger(a, b, c)
        }
        dependencies[id] = setOf(depA.id, depB.id, depC.id)
        return PipelineRef(id)
    }

    fun <I, O> pipe(
        id: String,
        dependency: PipelineRef<I>,
        pipeline: Pipeline<I, E, O>
    ): PipelineRef<O> {
        nodes[id] = PipelineNode.Pipe(dependency.id, pipeline)
        dependencies[id] = setOf(dependency.id)
        return PipelineRef(id)
    }

    internal fun build(): PipelineGraph<E> {
        return PipelineGraph(nodes.toMap(), dependencies.toMap())
    }
}

data class PipelineRef<T>(val id: String)

sealed class PipelineNode<E, out O> {
    data class Single<E, I, O>(
        val input: I,
        val pipeline: Pipeline<I, E, O>
    ) : PipelineNode<E, O>()

    data class Merge<E, O>(
        val merger: suspend (Map<String, Any>) -> Result<E, O>
    ) : PipelineNode<E, O>()

    data class Pipe<E, I, O>(
        val dependencyId: String,
        val pipeline: Pipeline<I, E, O>
    ) : PipelineNode<E, O>()
}

class PipelineGraph<E>(
    private val nodes: Map<String, PipelineNode<E, *>>,
    private val dependencies: Map<String, Set<String>>
) {
    suspend fun execute(targetId: String): Result<E, Any> = coroutineScope {
        val results = mutableMapOf<String, Any>()
        val executed = mutableSetOf<String>()

        suspend fun executeNode(nodeId: String): Result<E, Any> {
            if (nodeId in executed) {
                return Result.success(results[nodeId]!!)
            }

            val deps = dependencies[nodeId] ?: emptySet()
            for (depId in deps) {
                when (val depResult = executeNode(depId)) {
                    is Result.Success -> results[depId] = depResult.value
                    is Result.Failure -> return depResult
                }
            }

            val node = nodes[nodeId] ?: error("Node $nodeId not found")
            val result = when (node) {
                is PipelineNode.Single<*, *, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val typedNode = node as PipelineNode.Single<E, Any, Any>
                    typedNode.pipeline.execute(typedNode.input)
                }
                is PipelineNode.Merge<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val typedNode = node as PipelineNode.Merge<E, Any>
                    val depResults = deps.associateWith { results[it]!! }
                    typedNode.merger(depResults)
                }
                is PipelineNode.Pipe<*, *, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val typedNode = node as PipelineNode.Pipe<E, Any, Any>
                    val depResult = results[typedNode.dependencyId]!!
                    typedNode.pipeline.execute(depResult)
                }
            }

            when (result) {
                is Result.Success -> {
                    results[nodeId] = result.value
                    executed.add(nodeId)
                    return result
                }
                is Result.Failure -> return result
            }
        }

        executeNode(targetId)
    }
}

fun <E> parallelPipeline(block: ParallelPipelineBuilder<E>.() -> PipelineRef<*>): Pipeline<Unit, E, Any> {
    return Pipeline { _ ->
        val builder = ParallelPipelineBuilder<E>()
        val targetRef = builder.block()
        val graph = builder.build()
        graph.execute(targetRef.id)
    }
}
