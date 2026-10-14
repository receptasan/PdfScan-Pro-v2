package com.example.pdfscan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs

object TableExtractor {
    fun extractTable(bitmap: Bitmap, ocrBlocks: List<TextLine>): TableGrid {
        val hLines = detectHorizontalLines(bitmap)
        val vLines = detectVerticalLines(bitmap)
        val rawCells: List<TableCell>
        val isBorderless: Boolean

        if (hLines.size >= 2 && vLines.size >= 2) {
            isBorderless = false
            rawCells = GridOps.buildGridWithMergedCells(
                hLines = hLines,
                vLines = vLines,
                hasHorizontalWall = { r, c -> checkHorizontalPixels(bitmap, hLines[r], vLines[c], vLines[c + 1]) },
                hasVerticalWall = { r, c -> checkVerticalPixels(bitmap, vLines[c], hLines[r], hLines[r + 1]) }
            )
        } else {
            isBorderless = true
            rawCells = detectBorderlessTable(bitmap.width, bitmap.height, ocrBlocks)
        }

        val enrichedCells = rawCells.map { cell ->
            val cellOcrLines = ocrBlocks.filter { block ->
                Rect.intersects(cell.boundingBox, block.boundingBox)
            }.map { block ->
                val (isBold, textColor) = analyzeTextStyleAndColor(bitmap, block.boundingBox)
                block.copy(isBold = isBold, textColor = textColor)
            }
            val multiLines = GridOps.groupIntoMultiline(cellOcrLines)
            val bgColor = extractCellBackgroundColor(bitmap, cell.boundingBox)
            cell.copy(lines = multiLines, bgColor = bgColor)
        }

        val maxRow = (enrichedCells.maxOfOrNull { it.row + it.rowSpan } ?: 1)
        val maxCol = (enrichedCells.maxOfOrNull { it.col + it.colSpan } ?: 1)
        return TableGrid(rowCount = maxRow, colCount = maxCol, cells = enrichedCells, isBorderless = isBorderless)
    }

    private fun detectBorderlessTable(width: Int, height: Int, blocks: List<TextLine>): List<TableCell> {
        if (blocks.isEmpty()) return emptyList()
        val yHist = IntArray(height)
        for (b in blocks) {
            for (y in b.boundingBox.top.coerceAtLeast(0) until b.boundingBox.bottom.coerceAtMost(height)) yHist[y]++
        }
        val virtualHLines = findProjectionCuts(yHist, minGapSize = 8)
        val xHist = IntArray(width)
        for (b in blocks) {
            for (x in b.boundingBox.left.coerceAtLeast(0) until b.boundingBox.right.coerceAtMost(width)) xHist[x]++
        }
        val virtualVLines = findProjectionCuts(xHist, minGapSize = 18)
        val cells = mutableListOf<TableCell>()
        for (r in 0 until virtualHLines.size - 1) {
            for (c in 0 until virtualVLines.size - 1) {
                cells.add(TableCell(row = r, col = c, rowSpan = 1, colSpan = 1, boundingBox = Rect(virtualVLines[c], virtualHLines[r], virtualVLines[c + 1], virtualHLines[r + 1])))
            }
        }
        return cells
    }

    private fun findProjectionCuts(hist: IntArray, minGapSize: Int): List<Int> {
        val cuts = mutableListOf<Int>()
        var inContent = false
        var zeroStart = 0
        for (i in hist.indices) {
            if (hist[i] > 0) {
                if (!inContent) {
                    val gap = i - zeroStart
                    if (cuts.isEmpty() || gap >= minGapSize) cuts.add((zeroStart + i) / 2)
                    inContent = true
                }
            } else if (inContent) {
                zeroStart = i
                inContent = false
            }
        }
        cuts.add(hist.size - 1)
        return if (cuts.size < 2) listOf(0, hist.size - 1) else cuts
    }

    fun extractCellBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val right = (rect.right - 1).coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val bottom = (rect.bottom - 1).coerceIn(0, bitmap.height - 1)
        if (right <= left || bottom <= top) return Color.WHITE
        val rList = mutableListOf<Int>(); val gList = mutableListOf<Int>(); val bList = mutableListOf<Int>()
        val stepX = ((right - left) / 10).coerceAtLeast(1)
        for (x in left..right step stepX) {
            val pTop = bitmap.getPixel(x, (top + 2).coerceAtMost(bottom))
            val pBottom = bitmap.getPixel(x, (bottom - 2).coerceAtLeast(top))
            rList.add(Color.red(pTop)); gList.add(Color.green(pTop)); bList.add(Color.blue(pTop))
            rList.add(Color.red(pBottom)); gList.add(Color.green(pBottom)); bList.add(Color.blue(pBottom))
        }
        rList.sort(); gList.sort(); bList.sort()
        val mid = rList.size / 2
        return Color.rgb(rList[mid], gList[mid], bList[mid])
    }

    fun analyzeTextStyleAndColor(bitmap: Bitmap, rect: Rect): Pair<Boolean, Int> {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val right = rect.right.coerceIn(1, bitmap.width)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val bottom = rect.bottom.coerceIn(1, bitmap.height)
        val w = right - left; val h = bottom - top
        if (w <= 2 || h <= 2) return Pair(false, Color.BLACK)
        var darkPixelCount = 0; var edgeTransitions = 0
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        val bgColor = extractCellBackgroundColor(bitmap, rect)
        val bgLum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)).toInt()
        for (y in top until bottom) {
            var prevIsForeground = false
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val isForeground = abs(bgLum - lum) > 45
                if (isForeground) { darkPixelCount++; sumR += r; sumG += g; sumB += b }
                if (isForeground != prevIsForeground) edgeTransitions++
                prevIsForeground = isForeground
            }
        }
        if (darkPixelCount == 0) return Pair(false, Color.BLACK)
        val strokeWidthRatio = darkPixelCount.toFloat() / edgeTransitions.coerceAtLeast(1).toFloat()
        val density = darkPixelCount.toFloat() / (w * h).toFloat()
        val isBold = strokeWidthRatio > 1.35f && density > 0.24f
        val avgColor = Color.rgb((sumR / darkPixelCount).toInt().coerceIn(0, 255), (sumG / darkPixelCount).toInt().coerceIn(0, 255), (sumB / darkPixelCount).toInt().coerceIn(0, 255))
        return Pair(isBold, avgColor)
    }

    private fun detectHorizontalLines(bitmap: Bitmap): List<Int> {
        val lines = mutableListOf<Int>()
        val w = bitmap.width; val h = bitmap.height; val threshold = (w * 0.55).toInt()
        var y = 0
        while (y < h) {
            var darkCount = 0
            for (x in 0 until w step 2) {
                val p = bitmap.getPixel(x, y)
                if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 150) darkCount += 2
            }
            if (darkCount >= threshold) { lines.add(y); y += 6 } else y++
        }
        return lines
    }

    private fun detectVerticalLines(bitmap: Bitmap): List<Int> {
        val lines = mutableListOf<Int>()
        val w = bitmap.width; val h = bitmap.height; val threshold = (h * 0.45).toInt()
        var x = 0
        while (x < w) {
            var darkCount = 0
            for (y in 0 until h step 2) {
                val p = bitmap.getPixel(x, y)
                if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 150) darkCount += 2
            }
            if (darkCount >= threshold) { lines.add(x); x += 6 } else x++
        }
        return lines
    }

    private fun checkHorizontalPixels(bitmap: Bitmap, y: Int, xStart: Int, xEnd: Int): Boolean {
        val safeY = y.coerceIn(0, bitmap.height - 1)
        val start = xStart.coerceIn(0, bitmap.width - 1); val end = xEnd.coerceIn(0, bitmap.width - 1)
        if (end <= start) return false
        var dark = 0
        for (x in start until end) {
            val p = bitmap.getPixel(x, safeY)
            if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 165) dark++
        }
        return dark.toFloat() / (end - start).toFloat() > 0.5f
    }

    private fun checkVerticalPixels(bitmap: Bitmap, x: Int, yStart: Int, yEnd: Int): Boolean {
        val safeX = x.coerceIn(0, bitmap.width - 1)
        val start = yStart.coerceIn(0, bitmap.height - 1); val end = yEnd.coerceIn(0, bitmap.height - 1)
        if (end <= start) return false
        var dark = 0
        for (y in start until end) {
            val p = bitmap.getPixel(safeX, y)
            if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 165) dark++
        }
        return dark.toFloat() / (end - start).toFloat() > 0.5f
    }
}
