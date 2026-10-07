package app.mcorg.pipeline.resources

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Where planner work that has been moved off the call thread runs: one derivation at a time,
 * across the whole app.
 *
 * Production has one CPU, so Ktor has one call thread (MCO-551) and [Dispatchers.Default] has two.
 * A derivation is ~0.7 s of CPU, and the roadmap can ask for one per planned project — after a
 * world-wide invalidation, all of them (MCO-578). On plain `Default`, two roadmaps doing that at
 * once would hold both threads, and the event bus, which also runs on `Default` and delivers the
 * webhooks, would wait for them. Limited to one, planner work always leaves `Default` a thread.
 * On one CPU, running two derivations at once would not finish either sooner anyway.
 */
val PlannerDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, "seam-planner")
