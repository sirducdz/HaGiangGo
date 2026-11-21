package com.reed.hagianggo.domain

import android.content.Context
import com.reed.hagianggo.model.ManeuverType
import com.reed.hagianggo.model.NavigationRoute
import com.reed.hagianggo.model.NavigationStep
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.RouteSource
import com.reed.hagianggo.model.VehicleType
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Bộ định tuyến 100% Offline trên mạng lưới 6.791 tọa độ đường đèo thực tế
 * của vòng cung Hà Giang Loop & 5 nhánh rẽ trong assets/hagiang_loop_roads.json.
 *
 * Dùng trực tiếp thuật toán Dijkstra tính quãng đường ngắn nhất theo mét thực tế,
 * không phụ thuộc Internet hay API ngoài.
 */
object OfflineRoadGraphRouter {

    private const val MAIN_LOOP_ROAD_NAME = "Trục chính Hà Giang Loop (QL4C)"

    data class Edge(
        val toNode: Int,
        val weightMeters: Double
    )

    data class RoadGraph(
        val lats: DoubleArray,
        val lngs: DoubleArray,
        val roadNames: Array<String>,
        val isMainLoop: BooleanArray,
        val adjacency: Array<List<Edge>>
    ) {
        val nodeCount: Int get() = lats.size
    }

    data class RawBranchData(
        val name: String,
        val points: List<Pair<Double, Double>>
    )

    private data class VertexNode(
        val lat: Double,
        val lng: Double,
        val roadName: String,
        val isMain: Boolean
    )

    @Volatile
    private var cachedAssetGraph: RoadGraph? = null

    fun getOrLoadAssetGraph(context: Context): RoadGraph {
        cachedAssetGraph?.let { return it }
        synchronized(this) {
            cachedAssetGraph?.let { return it }
            val jsonStr = context.assets.open("hagiang_loop_roads.json")
                .bufferedReader()
                .use { it.readText() }
            val graph = parseGraphFromJson(jsonStr)
            cachedAssetGraph = graph
            return graph
        }
    }

    /**
     * Giải mã JSON tuyến đường bằng parser Kotlin thuần để dùng chung được cả trong JVM Unit Test.
     */
    fun parseGraphFromJson(jsonStr: String): RoadGraph {
        val mainLoopPoints = extractPointArrayByKey(jsonStr, "\"main_loop\"")
        val branches = extractBranches(jsonStr)
        return buildGraph(mainLoopPoints, branches)
    }

    fun buildGraph(
        mainLoopPoints: List<Pair<Double, Double>>,
        branches: List<RawBranchData>
    ): RoadGraph {
        val vertices = ArrayList<VertexNode>(mainLoopPoints.size + branches.sumOf { it.points.size })
        val tempAdj = ArrayList<MutableList<Edge>>(mainLoopPoints.size + 1600)

        fun addVertex(lat: Double, lng: Double, roadName: String, isMain: Boolean): Int {
            val idx = vertices.size
            vertices.add(VertexNode(lat, lng, roadName, isMain))
            tempAdj.add(ArrayList(4))
            return idx
        }

        fun addUndirectedEdge(u: Int, v: Int, weightMeters: Double) {
            if (u == v || weightMeters.isNaN()) return
            val safeW = weightMeters.coerceAtLeast(0.5)
            tempAdj[u].add(Edge(v, safeW))
            tempAdj[v].add(Edge(u, safeW))
        }

        // 1. Nạp trục chính main_loop (lọc bỏ điểm trùng liên tiếp < 1m)
        val mainIndices = ArrayList<Int>(mainLoopPoints.size)
        for (pt in mainLoopPoints) {
            if (mainIndices.isNotEmpty()) {
                val prev = vertices[mainIndices.last()]
                val d = LocationMath.calculateDistanceKm(prev.lat, prev.lng, pt.first, pt.second) * 1000.0
                if (d < 1.0) continue
                val id = addVertex(pt.first, pt.second, MAIN_LOOP_ROAD_NAME, true)
                addUndirectedEdge(mainIndices.last(), id, d)
                mainIndices.add(id)
            } else {
                mainIndices.add(addVertex(pt.first, pt.second, MAIN_LOOP_ROAD_NAME, true))
            }
        }
        // Khép kín vòng lặp Km0 TP. Hà Giang
        if (mainIndices.size >= 3) {
            val first = vertices[mainIndices.first()]
            val last = vertices[mainIndices.last()]
            val loopClosureM = LocationMath.calculateDistanceKm(last.lat, last.lng, first.lat, first.lng) * 1000.0
            if (loopClosureM <= 400.0) {
                addUndirectedEdge(mainIndices.last(), mainIndices.first(), loopClosureM)
            }
        }

        // 2. Nạp các nhánh rẽ và gắn cầu nối vào giao lộ gần nhất trên trục chính
        val branchEndpoints = ArrayList<Int>(branches.size * 2)
        for (branch in branches) {
            val bIndices = ArrayList<Int>(branch.points.size)
            for (pt in branch.points) {
                if (bIndices.isNotEmpty()) {
                    val prev = vertices[bIndices.last()]
                    val d = LocationMath.calculateDistanceKm(prev.lat, prev.lng, pt.first, pt.second) * 1000.0
                    if (d < 1.0) continue
                    val id = addVertex(pt.first, pt.second, branch.name, false)
                    addUndirectedEdge(bIndices.last(), id, d)
                    bIndices.add(id)
                } else {
                    bIndices.add(addVertex(pt.first, pt.second, branch.name, false))
                }
            }
            if (bIndices.isNotEmpty()) {
                val startNode = bIndices.first()
                val endNode = bIndices.last()
                branchEndpoints.add(startNode)
                if (endNode != startNode) branchEndpoints.add(endNode)

                for (ep in listOf(startNode, endNode).distinct()) {
                    val v = vertices[ep]
                    var bestMainIdx = -1
                    var bestDistM = Double.MAX_VALUE
                    for (mIdx in mainIndices) {
                        val m = vertices[mIdx]
                        val d = LocationMath.calculateDistanceKm(v.lat, v.lng, m.lat, m.lng) * 1000.0
                        if (d < bestDistM) {
                            bestDistM = d
                            bestMainIdx = mIdx
                        }
                    }
                    if (bestMainIdx >= 0 && bestDistM <= 450.0) {
                        addUndirectedEdge(ep, bestMainIdx, bestDistM)
                    }
                }
            }
        }

        // Nối thêm nếu 2 nhánh phụ giao gần nhau (< 300m)
        for (i in branchEndpoints.indices) {
            for (j in i + 1 until branchEndpoints.size) {
                val u = branchEndpoints[i]
                val v = branchEndpoints[j]
                val d = LocationMath.calculateDistanceKm(
                    vertices[u].lat, vertices[u].lng,
                    vertices[v].lat, vertices[v].lng
                ) * 1000.0
                if (d <= 300.0) {
                    addUndirectedEdge(u, v, d)
                }
            }
        }

        val count = vertices.size
        val lats = DoubleArray(count) { vertices[it].lat }
        val lngs = DoubleArray(count) { vertices[it].lng }
        val roadNames = Array(count) { vertices[it].roadName }
        val isMainLoop = BooleanArray(count) { vertices[it].isMain }
        val adjacency = Array<List<Edge>>(count) { tempAdj[it] }

        return RoadGraph(lats, lngs, roadNames, isMainLoop, adjacency)
    }

    fun computeRoute(
        context: Context,
        originLat: Double,
        originLng: Double,
        destination: Place,
        vehicle: VehicleType,
        originLabel: String
    ): NavigationRoute {
        val graph = getOrLoadAssetGraph(context)
        return computeRouteOnGraph(graph, originLat, originLng, destination, vehicle, originLabel)
    }

    fun computeRouteOnGraph(
        graph: RoadGraph,
        originLat: Double,
        originLng: Double,
        destination: Place,
        vehicle: VehicleType,
        originLabel: String
    ): NavigationRoute {
        if (graph.nodeCount == 0) {
            return buildDirectFallbackRoute(originLat, originLng, destination, vehicle, originLabel)
        }

        val startNode = findNearestNode(graph, originLat, originLng)
        val endNode = findNearestNode(graph, destination.lat, destination.lng)
        if (startNode < 0 || endNode < 0) {
            return buildDirectFallbackRoute(originLat, originLng, destination, vehicle, originLabel)
        }

        val nodePath = runDijkstra(graph, startNode, endNode)
        if (nodePath.isEmpty()) {
            return buildDirectFallbackRoute(originLat, originLng, destination, vehicle, originLabel)
        }

        val polyline = ArrayList<Pair<Double, Double>>(nodePath.size + 2)
        val nodeToPolyIndex = IntArray(nodePath.size)

        val startDistM = LocationMath.calculateDistanceKm(
            originLat, originLng,
            graph.lats[startNode], graph.lngs[startNode]
        ) * 1000.0
        if (startDistM > 25.0) {
            polyline.add(originLat to originLng)
        }
        for (i in nodePath.indices) {
            val n = nodePath[i]
            nodeToPolyIndex[i] = polyline.size
            polyline.add(graph.lats[n] to graph.lngs[n])
        }
        val endDistM = LocationMath.calculateDistanceKm(
            graph.lats[endNode], graph.lngs[endNode],
            destination.lat, destination.lng
        ) * 1000.0
        if (endDistM > 25.0 || polyline.size < 2) {
            polyline.add(destination.lat to destination.lng)
        }

        // Cộng dồn chiều dài từng đoạn theo mét
        val cumMeters = DoubleArray(polyline.size)
        for (i in 1 until polyline.size) {
            val a = polyline[i - 1]
            val b = polyline[i]
            cumMeters[i] = cumMeters[i - 1] + LocationMath.calculateDistanceKm(a.first, a.second, b.first, b.second) * 1000.0
        }
        val totalMeters = cumMeters.last()
        val avgSpeedKmh = if (vehicle == VehicleType.MOTORBIKE) 28.0 else 32.0
        val totalDurationSec = ((totalMeters / 1000.0) / avgSpeedKmh * 3600.0).toInt().coerceAtLeast(60)

        val steps = generateOfflineSteps(graph, nodePath, nodeToPolyIndex, polyline, cumMeters, destination, avgSpeedKmh)

        return NavigationRoute(
            originLat = originLat,
            originLng = originLng,
            destination = destination,
            polyline = polyline,
            steps = steps,
            totalDistanceMeters = totalMeters,
            totalDurationSeconds = totalDurationSec,
            source = RouteSource.OFFLINE_LOOP_GRAPH,
            originLabel = originLabel
        )
    }

    private fun runDijkstra(graph: RoadGraph, start: Int, goal: Int): List<Int> {
        if (start == goal) return listOf(start)
        val n = graph.nodeCount
        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val prev = IntArray(n) { -1 }
        val visited = BooleanArray(n)

        data class State(val node: Int, val distM: Double) : Comparable<State> {
            override fun compareTo(other: State): Int = distM.compareTo(other.distM)
        }

        val pq = PriorityQueue<State>()
        dist[start] = 0.0
        pq.add(State(start, 0.0))

        while (pq.isNotEmpty()) {
            val cur = pq.poll() ?: break
            if (visited[cur.node]) continue
            visited[cur.node] = true
            if (cur.node == goal) break

            for (edge in graph.adjacency[cur.node]) {
                if (visited[edge.toNode]) continue
                val nd = cur.distM + edge.weightMeters
                if (nd < dist[edge.toNode]) {
                    dist[edge.toNode] = nd
                    prev[edge.toNode] = cur.node
                    pq.add(State(edge.toNode, nd))
                }
            }
        }

        if (dist[goal].isInfinite()) return emptyList()
        val path = ArrayList<Int>()
        var curr = goal
        while (curr != -1) {
            path.add(curr)
            curr = prev[curr]
        }
        path.reverse()
        return path
    }

    private fun findNearestNode(graph: RoadGraph, lat: Double, lng: Double): Int {
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        for (i in 0 until graph.nodeCount) {
            val d = LocationMath.calculateDistanceKm(lat, lng, graph.lats[i], graph.lngs[i])
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun generateOfflineSteps(
        graph: RoadGraph,
        nodePath: List<Int>,
        nodeToPolyIndex: IntArray,
        polyline: List<Pair<Double, Double>>,
        cumMeters: DoubleArray,
        destination: Place,
        avgSpeedKmh: Double
    ): List<NavigationStep> {
        data class RawStep(
            val polyIdx: Int,
            val maneuver: ManeuverType,
            val instructionVi: String,
            val roadName: String
        )

        val rawSteps = ArrayList<RawStep>()
        val firstRoad = graph.roadNames[nodePath.first()]
        rawSteps.add(
            RawStep(
                polyIdx = 0,
                maneuver = ManeuverType.DEPART,
                instructionVi = "Khởi hành theo $firstRoad",
                roadName = firstRoad
            )
        )

        var lastStepMeters = 0.0
        for (i in 1 until nodePath.size - 1) {
            val prevNode = nodePath[i - 1]
            val currNode = nodePath[i]
            val polyIdx = nodeToPolyIndex[i]
            val prevRoad = graph.roadNames[prevNode]
            val currRoad = graph.roadNames[currNode]
            val distFromStart = cumMeters[polyIdx]

            if (prevRoad != currRoad) {
                val angle = computeLocalTurnDelta(polyline, polyIdx)
                val maneuver = classifyTurnAngle(angle)
                val turnVerb = turnVerbVi(maneuver)
                rawSteps.add(
                    RawStep(
                        polyIdx = polyIdx,
                        maneuver = maneuver,
                        instructionVi = "$turnVerb vào $currRoad",
                        roadName = currRoad
                    )
                )
                lastStepMeters = distFromStart
            } else if (distFromStart - lastStepMeters >= 1500.0 && cumMeters.last() - distFromStart >= 600.0) {
                val angle = computeLocalTurnDelta(polyline, polyIdx, windowPts = 6)
                if (abs(angle) >= 75f) {
                    val maneuver = classifyTurnAngle(angle)
                    val dirLabel = if (angle < 0f) "trái" else "phải"
                    rawSteps.add(
                        RawStep(
                            polyIdx = polyIdx,
                            maneuver = maneuver,
                            instructionVi = "Cua gắt sang $dirLabel bám $currRoad",
                            roadName = currRoad
                        )
                    )
                    lastStepMeters = distFromStart
                }
            }
        }

        val lastIdx = polyline.lastIndex
        rawSteps.add(
            RawStep(
                polyIdx = lastIdx,
                maneuver = ManeuverType.ARRIVE,
                instructionVi = "Đã đến ${destination.name}",
                roadName = destination.name
            )
        )

        val steps = ArrayList<NavigationStep>(rawSteps.size)
        for (i in rawSteps.indices) {
            val cur = rawSteps[i]
            val nextMeters = if (i + 1 < rawSteps.size) cumMeters[rawSteps[i + 1].polyIdx] else cumMeters.last()
            val legDistM = (nextMeters - cumMeters[cur.polyIdx]).coerceAtLeast(0.0)
            val legSec = ((legDistM / 1000.0) / avgSpeedKmh * 3600.0).toInt()
            val pt = polyline[cur.polyIdx]
            steps.add(
                NavigationStep(
                    index = i,
                    instructionVi = cur.instructionVi,
                    roadName = cur.roadName,
                    maneuver = cur.maneuver,
                    distanceMeters = legDistM,
                    durationSeconds = legSec,
                    maneuverLat = pt.first,
                    maneuverLng = pt.second,
                    routePointIndex = cur.polyIdx
                )
            )
        }
        return steps
    }

    private fun computeLocalTurnDelta(
        polyline: List<Pair<Double, Double>>,
        idx: Int,
        windowPts: Int = 4
    ): Float {
        val beforeIdx = (idx - windowPts).coerceAtLeast(0)
        val afterIdx = (idx + windowPts).coerceAtMost(polyline.lastIndex)
        if (beforeIdx >= idx || afterIdx <= idx) return 0f
        val pBefore = polyline[beforeIdx]
        val pMid = polyline[idx]
        val pAfter = polyline[afterIdx]
        val b1 = LocationMath.calculateBearingDeg(pBefore.first, pBefore.second, pMid.first, pMid.second)
        val b2 = LocationMath.calculateBearingDeg(pMid.first, pMid.second, pAfter.first, pAfter.second)
        return LocationMath.normalizeAngleDeltaDeg(b1, b2)
    }

    private fun classifyTurnAngle(deltaDeg: Float): ManeuverType {
        val a = abs(deltaDeg)
        return when {
            a < 22f -> ManeuverType.STRAIGHT
            deltaDeg < 0f && a < 55f -> ManeuverType.SLIGHT_LEFT
            deltaDeg < 0f && a < 115f -> ManeuverType.LEFT
            deltaDeg < 0f -> ManeuverType.SHARP_LEFT
            deltaDeg > 0f && a < 55f -> ManeuverType.SLIGHT_RIGHT
            deltaDeg > 0f && a < 115f -> ManeuverType.RIGHT
            else -> ManeuverType.SHARP_RIGHT
        }
    }

    private fun turnVerbVi(m: ManeuverType): String = when (m) {
        ManeuverType.STRAIGHT -> "Đi thẳng"
        ManeuverType.SLIGHT_LEFT -> "Đi chếch sang trái"
        ManeuverType.LEFT -> "Rẽ trái"
        ManeuverType.SHARP_LEFT -> "Cua gắt sang trái"
        ManeuverType.SLIGHT_RIGHT -> "Đi chếch sang phải"
        ManeuverType.RIGHT -> "Rẽ phải"
        ManeuverType.SHARP_RIGHT -> "Cua gắt sang phải"
        else -> "Tiếp tục"
    }

    private fun buildDirectFallbackRoute(
        originLat: Double,
        originLng: Double,
        destination: Place,
        vehicle: VehicleType,
        originLabel: String
    ): NavigationRoute {
        val poly = listOf(originLat to originLng, destination.lat to destination.lng)
        val straightKm = LocationMath.calculateDistanceKm(originLat, originLng, destination.lat, destination.lng)
        val distM = straightKm * 1000.0 * 1.45
        val durSec = LocationMath.estimateMountainTravelTimeMin(straightKm, vehicle) * 60
        return NavigationRoute(
            originLat = originLat,
            originLng = originLng,
            destination = destination,
            polyline = poly,
            steps = listOf(
                NavigationStep(0, "Hướng về ${destination.name}", "", ManeuverType.DEPART, distM, durSec, originLat, originLng, 0),
                NavigationStep(1, "Đã đến ${destination.name}", destination.name, ManeuverType.ARRIVE, 0.0, 0, destination.lat, destination.lng, 1)
            ),
            totalDistanceMeters = distM,
            totalDurationSeconds = durSec,
            source = RouteSource.OFFLINE_LOOP_GRAPH,
            originLabel = originLabel
        )
    }

    // ====== Bộ giải mã JSON thuần Kotlin cho hagiang_loop_roads.json (chạy được cả JVM Test) ======

    private fun extractPointArrayByKey(json: String, keyToken: String): List<Pair<Double, Double>> {
        val keyPos = json.indexOf(keyToken)
        if (keyPos < 0) return emptyList()
        val startBracket = json.indexOf('[', keyPos + keyToken.length)
        if (startBracket < 0) return emptyList()
        return parseNestedCoordArray(json, startBracket).first
    }

    private fun extractBranches(json: String): List<RawBranchData> {
        val brKeyPos = json.indexOf("\"branches\"")
        if (brKeyPos < 0) return emptyList()
        val arrStart = json.indexOf('[', brKeyPos)
        if (arrStart < 0) return emptyList()

        val result = ArrayList<RawBranchData>()
        var i = arrStart + 1
        while (i < json.length) {
            val ch = json[i]
            if (ch == ']') break
            if (ch == '{') {
                val objEnd = findMatchingBrace(json, i)
                if (objEnd <= i) break
                val objStr = json.substring(i, objEnd + 1)
                val name = extractJsonStringField(objStr, "\"name\"") ?: "Nhánh đường đèo"
                val pts = extractPointArrayByKey(objStr, "\"points\"")
                if (pts.isNotEmpty()) {
                    result.add(RawBranchData(name, pts))
                }
                i = objEnd + 1
                continue
            }
            i++
        }
        return result
    }

    private fun extractJsonStringField(objStr: String, keyToken: String): String? {
        val kPos = objStr.indexOf(keyToken)
        if (kPos < 0) return null
        val colon = objStr.indexOf(':', kPos + keyToken.length)
        if (colon < 0) return null
        val q1 = objStr.indexOf('"', colon + 1)
        if (q1 < 0) return null
        val sb = StringBuilder()
        var idx = q1 + 1
        while (idx < objStr.length) {
            val c = objStr[idx]
            if (c == '\\' && idx + 1 < objStr.length) {
                sb.append(objStr[idx + 1])
                idx += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            idx++
        }
        return sb.toString()
    }

    private fun findMatchingBrace(s: String, openIdx: Int): Int {
        var depth = 0
        var inString = false
        var i = openIdx
        while (i < s.length) {
            val c = s[i]
            if (c == '"' && (i == 0 || s[i - 1] != '\\')) {
                inString = !inString
            } else if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun parseNestedCoordArray(s: String, outerOpenBracket: Int): Pair<List<Pair<Double, Double>>, Int> {
        val points = ArrayList<Pair<Double, Double>>(512)
        var depth = 0
        var i = outerOpenBracket
        var numBuf = StringBuilder()
        var currentPairLat: Double? = null

        fun flushNumber() {
            if (numBuf.isEmpty()) return
            val v = numBuf.toString().toDoubleOrNull()
            numBuf.setLength(0)
            if (v != null) {
                val lat = currentPairLat
                if (lat == null) {
                    currentPairLat = v
                } else {
                    points.add(lat to v)
                    currentPairLat = null
                }
            }
        }

        while (i < s.length) {
            when (val c = s[i]) {
                '[' -> {
                    depth++
                    if (depth == 2) currentPairLat = null
                }
                ']' -> {
                    flushNumber()
                    depth--
                    if (depth == 0) return points to i
                }
                ',' -> flushNumber()
                in '0'..'9', '-', '+', '.', 'e', 'E' -> if (depth >= 2) numBuf.append(c)
            }
            i++
        }
        return points to s.lastIndex
    }
}
