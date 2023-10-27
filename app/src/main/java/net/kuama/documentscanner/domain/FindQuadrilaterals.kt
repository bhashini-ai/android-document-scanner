package net.kuama.documentscanner.domain

import android.graphics.Bitmap
import net.kuama.documentscanner.data.Corners
import net.kuama.documentscanner.data.Line
import net.kuama.documentscanner.data.LinesIntersection
import net.kuama.documentscanner.data.angle
import net.kuama.documentscanner.data.countOnPixels
import net.kuama.documentscanner.support.InfallibleUseCase
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs

class FindQuadrilaterals : InfallibleUseCase<Corners?, FindQuadrilaterals.Params>() {

    class Params(val bitmap: Bitmap)

    override suspend fun run(params: Params): Corners? {
        val original = Mat()
        val modified = Mat()
        val cannyImg = Mat()

        Utils.bitmapToMat(params.bitmap, original)

        // Convert image from RGBA to GrayScale
        Imgproc.cvtColor(original, modified, Imgproc.COLOR_RGBA2GRAY)

        // Strong Gaussian Filter
        Imgproc.GaussianBlur(modified, modified, Size(51.0, 51.0), 0.0)

        // Canny Edge Detection
        Imgproc.Canny(modified, cannyImg, 100.0, 200.0, 5, false)

        val lines = Mat()
        // val houghThreshold = (min(modified.size().width, modified.size().height) * 0.5 ).toInt()
        val houghThreshold = 75
        val groupSimilarThreshold = 45
        val angleThreshold = PI/4.0

        // OpenCV Hough Line Transform Tutorial https://docs.opencv.org/3.4/d9/db0/tutorial_hough_lines.html
        Imgproc.HoughLines(cannyImg, lines, 1.0, Math.PI / 180.0, houghThreshold)

        val imgSize = cannyImg.size()
        val xMax = imgSize.width - 1
        val yMax = imgSize.height - 1
        val uniqueLines = groupSimilarLines(lines, groupSimilarThreshold, xMax, yMax)
        val intersectionPoints = findIntersections(uniqueLines, xMax, yMax, angleThreshold)
        return buildQuadrilateralsAndFindBest(intersectionPoints, minLineLength = 75, minAngle = PI * 0.25, maxAngle = PI, cannyImg)
//        return Lines(uniqueLines, imgSize, intersectionPoints)
    }

    private fun groupSimilarLines(houghMat: Mat, groupSimilarThreshold: Int, xMax: Double, yMax: Double): List<Line> {
        val uniqueLines = mutableListOf<Line>()
        for (i in 0 until houghMat.rows()) {
            val element = houghMat.get(i, 0)
            val rho = element[0]
            val theta = element[1]
            val line = Line(rho, theta)
            val closestLine = line.closestLine(uniqueLines)
            if (closestLine == null || abs(line.rho - closestLine.rho) > groupSimilarThreshold) {
                uniqueLines.add(line)
            }
        }
        for (i in uniqueLines.indices) {
            val line = uniqueLines[i]
            val extremePoints = line.computeExtremePoints(xMax, yMax)
            if (extremePoints.size != 2) {
                Timber.e("Invalid number of extreme points (=${extremePoints.size}) detected for line with rho=${line.rho} and theta=${line.theta}")
            }
        }
        return uniqueLines
    }

    private fun findIntersections(lines: List<Line>, xMax: Double, yMax: Double, angleThreshold: Double): Set<LinesIntersection> {
        val intersections = mutableSetOf<LinesIntersection>()
        for (i in lines.indices) {
            val line1 = lines[i]
            for (j in i + 1 until lines.size) {
                val line2 = lines[j]
                if (abs(line1.theta - line2.theta) < angleThreshold) {
                    continue
                }
                val p = line1.findIntersectionCords(line2)
                if (p.x in 0.0..xMax && p.y in 0.0..yMax) {
                    intersections.add(LinesIntersection(line1, line2, p));
                }
            }
        }
        return intersections
    }

    private fun buildQuadrilateralsAndFindBest(intersections: Set<LinesIntersection>, minLineLength: Int, minAngle: Double, maxAngle: Double, cannyImg: Mat): Corners? {
        val intersectionsList = intersections.toList()
        var weights = Array(intersectionsList.size) { DoubleArray(intersectionsList.size) }
        var maxScore = 0.0
        var bestQuadrilateral: List<LinesIntersection>? = null
        for (i1 in 0 until intersectionsList.size) {
            val v1 = intersectionsList[i1]
            for (i2 in i1 + 1 until intersectionsList.size) {
                val v2 = intersectionsList[i2]
                val v1v2Line = commonLine(v1, v2)
                if (v1v2Line != null && v1.dist(v2) > minLineLength) {
                    if (weights[i1][i2] == 0.0) {
                        var weight = countOnPixels(v1, v2, v1v2Line, cannyImg).toDouble()
                        if (weight == 0.0) {
                            weight = 0.000001
                        }
                        weights[i1][i2] = weight
                        weights[i2][i1] = weight
                    }
                    for (i3 in i1 + 1 until intersectionsList.size) {
                        val v3 = intersectionsList[i3]
                        val v2v3Line = commonLine(v2, v3)
                        if (v2v3Line != null && v2.dist(v3) > minLineLength) {
                            val angle123 = angle(v1v2Line, v2v3Line)
                            if (angle123 !in minAngle..maxAngle) continue;
                            for (i4 in i1 + 1 until intersectionsList.size) {
                                val v4 = intersectionsList[i4]
                                val v3v4Line = commonLine(v3, v4)
                                val v4v1Line = commonLine(v4, v1)
                                if (v3v4Line != null && v4v1Line != null &&
                                    v3.dist(v4) > minLineLength && v4.dist(v1) > minLineLength) {
                                    val angle234 = angle(v2v3Line, v3v4Line)
                                    val angle341 = angle(v3v4Line, v4v1Line)
                                    val angle412 = angle(v4v1Line, v1v2Line)
                                    if (angle234 in minAngle..maxAngle && angle341 in minAngle..maxAngle && angle412 in minAngle..maxAngle) {
                                        val score = weights[i1][i2] + weights[i2][i3] + weights[i3][i4] + weights[i4][i1]
                                        if (score > maxScore) {
                                            maxScore = score
                                            bestQuadrilateral = listOf(v1, v2, v3, v4)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (bestQuadrilateral != null) {
            val sortedPoints = bestQuadrilateral!!.sortedBy { it.point.x }
            val leftSidePoints = listOf(sortedPoints[0], sortedPoints[1]).sortedBy { it.point.y }
            val rightSidePoints = listOf(sortedPoints[2], sortedPoints[3]).sortedBy { it.point.y }
            val topLeft = leftSidePoints[0]
            val bottomLeft = leftSidePoints[1]
            val topRight = rightSidePoints[0]
            val bottomRight = rightSidePoints[1]
            return Corners(topLeft.point, topRight.point, bottomRight.point, bottomLeft.point, cannyImg.size())
        }
        return null
    }

    private fun commonLine(q1: LinesIntersection, q2: LinesIntersection): Line? {
        var line: Line? = null
        if (q1.line1 == q2.line1 || q1.line1 == q2.line2) {
            line = q1.line1
        } else if (q1.line2 == q2.line1 || q1.line2 == q2.line2) {
            line = q1.line2
        }
        return line
    }
}