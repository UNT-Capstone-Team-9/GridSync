package com.cloud9.gridsync.network

import java.io.Serializable

// An option (alternate) route for one player. points[0] is the branch point, which sits on the
// player's main route, and the rest is the dashed path the coach drew from there. Points are
// normalized 0 to 1 like every other route.
data class RouteBranch(
    val points: List<PointData>
) : Serializable
