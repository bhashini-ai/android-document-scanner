package net.kuama.documentscanner.data

import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class Lines(
    val lines: List<Line>,
    val size: Size
)

data class Line(
    val rho: Double,
    val theta: Double
) {
    private val cosTheta = cos(theta)
    private val sinTheta = sin(theta)

    lateinit var extremePoints: MutableList<Point>

    private fun getY(x: Double) =
        (rho - x * cosTheta) / sinTheta

    private fun getX(y: Double) =
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

}