package net.kuama.documentscanner.domain

import android.graphics.Bitmap
import net.kuama.documentscanner.data.Line
import net.kuama.documentscanner.data.Lines
import net.kuama.documentscanner.support.InfallibleUseCase
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs

class FindQuadrilaterals : InfallibleUseCase<Lines?, FindQuadrilaterals.Params>() {

    class Params(val bitmap: Bitmap)

    override suspend fun run(params: Params): Lines {
        val original = Mat()
        val modified = Mat()

        Utils.bitmapToMat(params.bitmap, original)

        // Convert image from RGBA to GrayScale
        Imgproc.cvtColor(original, modified, Imgproc.COLOR_RGBA2GRAY)

        // Strong Gaussian Filter
        Imgproc.GaussianBlur(modified, modified, Size(51.0, 51.0), 0.0)

        // Canny Edge Detection
        Imgproc.Canny(modified, modified, 100.0, 200.0, 5, false)

        val lines = Mat()
        val houghThreshold = 75
        val groupSimilarThreshold = 45
        val angleThreshold = PI/4.0
        // OpenCV Hough Line Transform Tutorial https://docs.opencv.org/3.4/d9/db0/tutorial_hough_lines.html
        // val houghThreshold = (min(modified.size().width, modified.size().height) * 0.5 ).toInt()
        Imgproc.HoughLines(modified, lines, 1.0, Math.PI / 180.0, houghThreshold)
        val imgSize = modified.size()
        val xMax = imgSize.width - 1
        val yMax = imgSize.height - 1
        val uniqueLines = groupSimilarLines(lines, groupSimilarThreshold, xMax, yMax)
        val intersectionPoints = findIntersections(uniqueLines, xMax, yMax, angleThreshold)
        return Lines(uniqueLines, imgSize, intersectionPoints)
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

    private fun findIntersections(lines: List<Line>, xMax: Double, yMax: Double, angleThreshold: Double): Set<Point> {
        val intersections = mutableSetOf<Point>()
        for (i in lines.indices) {
            val line1 = lines[i]
            for (j in i + 1 until lines.size) {
                val line2 = lines[j]
                if (abs(line1.theta - line2.theta) < angleThreshold) {
                    continue
                }
                val p = line1.findIntersectionCords(line2)
                if (p.x in 0.0..xMax && p.y in 0.0..yMax) {
                    intersections.add(p);
                }
            }
        }
        return intersections
    }

}