package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.ResourceGatheringPlan
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure

val UpsertPathStep = DatabaseSteps.transaction { txConn ->
    object : Step<Pair<Int, ProductionPath>, AppFailure.DatabaseError, Int> {
        override suspend fun process(input: Pair<Int, ProductionPath>): Result<AppFailure.DatabaseError, Int> {
            val (resourceGatheringId, path) = input

            // 1. Upsert the plan row
            val upsertPlan = DatabaseSteps.update<Int>(
                sql = SafeSQL.insert(
                    """
                    INSERT INTO resource_gathering_plan (resource_gathering_id, confirmed, created_at, updated_at)
                    VALUES (?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (resource_gathering_id)
                    DO UPDATE SET confirmed = FALSE, updated_at = CURRENT_TIMESTAMP
                    RETURNING id
                    """.trimIndent()
                ),
                parameterSetter = { ps, rgId ->
                    ps.setInt(1, rgId)
                },
                transactionConnection = txConn
            )
            val planId = when (val result = upsertPlan.process(resourceGatheringId)) {
                is Result.Success -> result.value
                is Result.Failure -> return result
            }

            // 2. Delete existing nodes for this plan
            val deleteNodes = DatabaseSteps.update<Int>(
                sql = SafeSQL.delete("DELETE FROM resource_gathering_plan_node WHERE plan_id = ?"),
                parameterSetter = { ps, id -> ps.setInt(1, id) },
                transactionConnection = txConn
            )
            when (val result = deleteNodes.process(planId)) {
                is Result.Failure -> return result
                is Result.Success -> {} // continue
            }

            // 3. Recursively insert the tree
            insertNodeTree(txConn, planId, null, path, 0)

            return Result.success(planId)
        }
    }
}

private suspend fun insertNodeTree(
    txConn: TransactionConnection,
    planId: Int,
    parentNodeId: Int?,
    path: ProductionPath,
    sortOrder: Int
): Result<AppFailure.DatabaseError, Unit> {
    val insertNode = DatabaseSteps.update<Unit>(
        sql = SafeSQL.insert(
            """
            INSERT INTO resource_gathering_plan_node (plan_id, parent_node_id, item_id, source, sort_order)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent()
        ),
        parameterSetter = { ps, _ ->
            ps.setInt(1, planId)
            if (parentNodeId != null) ps.setInt(2, parentNodeId) else ps.setNull(2, java.sql.Types.INTEGER)
            ps.setString(3, path.itemId)
            if (path.source != null) ps.setString(4, path.source) else ps.setNull(4, java.sql.Types.VARCHAR)
            ps.setInt(5, sortOrder)
        },
        transactionConnection = txConn
    )

    val nodeId = when (val result = insertNode.process(Unit)) {
        is Result.Success -> result.value
        is Result.Failure -> return Result.failure(result.error)
    }

    // Insert children
    for ((index, child) in path.requirements.withIndex()) {
        val childResult = insertNodeTree(txConn, planId, nodeId, child, index)
        if (childResult is Result.Failure) return childResult
    }

    return Result.success(Unit)
}

val LoadSavedPathStep = DatabaseSteps.query<Int, Result<AppFailure, ResourceGatheringPlan>>(
    sql = SafeSQL.select(
        """
        SELECT p.id AS plan_id, p.resource_gathering_id, p.confirmed,
               n.id AS node_id, n.parent_node_id, n.item_id, n.source, n.sort_order
        FROM resource_gathering_plan p
        LEFT JOIN resource_gathering_plan_node n ON n.plan_id = p.id
        WHERE p.resource_gathering_id = ?
        ORDER BY n.id
        """.trimIndent()
    ),
    parameterSetter = { ps, resourceGatheringId ->
        ps.setInt(1, resourceGatheringId)
    },
    resultMapper = { rs ->
        if (!rs.next()) return@query Result.failure(AppFailure.DatabaseError.NotFound)

        val planId = rs.getInt("plan_id")
        val resourceGatheringId = rs.getInt("resource_gathering_id")
        val confirmed = rs.getBoolean("confirmed")

        data class NodeRow(val id: Int, val parentNodeId: Int?, val itemId: String, val source: String?, val sortOrder: Int)
        val nodes = mutableListOf<NodeRow>()

        // First row might have node data (LEFT JOIN)
        val firstNodeId = rs.getInt("node_id")
        if (!rs.wasNull()) {
            nodes.add(NodeRow(
                id = firstNodeId,
                parentNodeId = rs.getInt("parent_node_id").takeIf { !rs.wasNull() },
                itemId = rs.getString("item_id"),
                source = rs.getString("source"),
                sortOrder = rs.getInt("sort_order")
            ))
        }

        while (rs.next()) {
            val nodeId = rs.getInt("node_id")
            if (!rs.wasNull()) {
                nodes.add(NodeRow(
                    id = nodeId,
                    parentNodeId = rs.getInt("parent_node_id").takeIf { !rs.wasNull() },
                    itemId = rs.getString("item_id"),
                    source = rs.getString("source"),
                    sortOrder = rs.getInt("sort_order")
                ))
            }
        }

        if (nodes.isEmpty()) return@query Result.failure(AppFailure.DatabaseError.NotFound)

        // Build the tree from flat rows
        val childrenByParent = nodes.groupBy { it.parentNodeId }

        fun buildTree(nodeRow: NodeRow): ProductionPath {
            val children = childrenByParent[nodeRow.id]
                ?.sortedBy { it.sortOrder }
                ?.map { buildTree(it) }
                ?: emptyList()
            return ProductionPath(
                itemId = nodeRow.itemId,
                source = nodeRow.source,
                requirements = children
            )
        }

        val root = nodes.find { it.parentNodeId == null }
            ?: return@query Result.failure(AppFailure.DatabaseError.NotFound)

        Result.success(ResourceGatheringPlan(
            id = planId,
            resourceGatheringId = resourceGatheringId,
            selectedPath = buildTree(root),
            confirmed = confirmed
        ))
    }
)

val ConfirmPathStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.update(
        """
        UPDATE resource_gathering_plan
        SET confirmed = TRUE, updated_at = CURRENT_TIMESTAMP
        WHERE resource_gathering_id = ?
        """.trimIndent()
    ),
    parameterSetter = { ps, resourceGatheringId ->
        ps.setInt(1, resourceGatheringId)
    }
)
