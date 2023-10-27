package net.kuama.documentscanner.data

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Lines(
    val lines: List<Line>,
    val size: Size,
    val intersections: Set<LinesIntersection>
)

data class LinesIntersection(
    val line1: Line,
    val line2: Line,
    val point: Point
) {
    fun dist(i2: LinesIntersection) : Double = dist(point, i2.point)
}

data class Line(
    val rho: Double,
    val theta: Double
) {
    private val cosTheta = cos(theta)
    private val sinTheta = sin(theta)

    lateinit var extremePoints: MutableList<Point>

    fun getY(x: Double) =
        (rho - x * cosTheta) / sinTheta

    fun getX(y: Double) =
        (rho - y * sinTheta) / cosTheta

    private fun getY(x: Double, yMax: Double): Point? {
        val y = getY(x)
        if (y in 0.0..yMax) {
            return Point(x, y)
        }
        return null
    }

    private fun getX(y: Double, xMax: Double): Point? {
        val x = getX(y)
        if (x in 0.0..xMax) {
            return Point(x, y)
        }
        return null
    }

    fun computeExtremePoints(xMax: Double, yMax: Double): List<Point> {
        val leftEdgePoint = getY(0.0, yMax)
        val rightEdgePoint = getY(xMax, yMax)
        val topLinePoint = getX(0.0, xMax)
        val bottomLinePoint = getX(yMax, xMax)
        extremePoints = mutableListOf<Point>()
        if (leftEdgePoint != null) {
            extremePoints.add(leftEdgePoint)
        }
        if (rightEdgePoint != null) {
            extremePoints.add(rightEdgePoint)
        }
        if (topLinePoint != null) {
            extremePoints.add(topLinePoint)
        }
        if (bottomLinePoint != null) {
            extremePoints.add(bottomLinePoint)
        }
        return extremePoints
    }

    fun closestLine(uniqueLines: List<Line>): Line? {
        if (uniqueLines.isNullOrEmpty()) {
            return null
        }
        var minDist = Int.MAX_VALUE
        var closestLine: Line? = null
        for (line in uniqueLines) {
            val dist = abs(line.rho - rho).toInt()
            if (dist < minDist) {
                minDist = dist
                closestLine = line
            }
        }
        return closestLine
    }

    fun findIntersectionCords(line2: Line): Point {
        val y = (rho * line2.cosTheta - line2.rho * cosTheta) / (sinTheta * line2.cosTheta - cosTheta * line2.sinTheta)
        val x = getX(y)
        return Point(x, y)
    }
}

fun withinBounds(x: Int, y: Int, size: Size) = x in 0 until size.width.toInt() && y in 0 until size.height.toInt()

fun withinBounds(p: Point, size: Size) = withinBounds(p.x.toInt(), p.y.toInt(), size)

fun dist(p1: Point, p2: Point) = sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2) )

fun angle(line1: Line, line2: Line) = abs(line1.theta - line2.theta)

data class Quadrilateral(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
    val topEdge: Line,
    val rightEdge: Line,
    val bottomEdge: Line,
    val leftEdge: Line
) {
    var score: Double = 0.0
    fun computeScore(cannyImg: Mat): Double {
        score = 0.0
        val size = cannyImg.size()
        for (x in topLeft.x.toInt() .. topRight.x.toInt() ) {
            val y = topEdge.getY(x.toDouble()).toInt()
            if (withinBounds(x, y, size) && cannyImg.get(y, x)[0] > 0) score++
        }
        for (y in topRight.y.toInt() .. bottomRight.y.toInt() ) {
            val x = rightEdge.getX(y.toDouble()).toInt()
            if (withinBounds(x, y, size) && cannyImg.get(y, x)[0] > 0) score++
        }
        for (x in bottomLeft.x.toInt() .. bottomRight.x.toInt() ) {
            val y = bottomEdge.getY(x.toDouble()).toInt()
            if (withinBounds(x, y, size) && cannyImg.get(y, x)[0] > 0) score++
        }
        for (y in topLeft.y.toInt() .. bottomLeft.y.toInt() ) {
            val x = leftEdge.getX(y.toDouble()).toInt()
            if (withinBounds(x, y, size) && cannyImg.get(y, x)[0] > 0) score++
        }
        return score
    }
}
