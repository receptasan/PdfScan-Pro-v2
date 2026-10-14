package com.example.pdfscan

import android.graphics.Color
import android.graphics.Rect

data class TextLine(
    val text: String,
    val boundingBox: Rect,
    val isBold: Boolean = false,
    val textColor: Int = Color.BLACK
)

data class TableCell(
    val row: Int,
    val col: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val boundingBox: Rect,
    val lines: List<TextLine> = emptyList(),
    val bgColor: Int = Color.WHITE
) {
    val fullText: String
        get() = lines.joinToString("\n") { it.text }
}

data class TableGrid(
    val rowCount: Int,
    val colCount: Int,
    val cells: List<TableCell>,
    val isBorderless: Boolean = false
)

object GridOps {
    fun buildGridWithMergedCells(
        hLines: List<Int>,
        vLines: List<Int>,
        hasHorizontalWall: (row: Int, col: Int) -> Boolean,
        hasVerticalWall: (row: Int, col: Int) -> Boolean
    ): List<TableCell> {
        val rows = hLines.size - 1
        val cols = vLines.size - 1
        if (rows <= 0 || cols <= 0) return emptyList()

        val visited = Array(rows) { BooleanArray(cols) }
        val resultCells = mutableListOf<TableCell>()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (visited[r][c]) continue
                var colSpan = 1
                while (c + colSpan < cols && !hasVerticalWall(r, c + colSpan)) {
                    colSpan++
                }
                var rowSpan = 1
                while (r + rowSpan < rows) {
                    var canExpandRow = true
                    for (cc in c until (c + colSpan)) {
                        if (hasHorizontalWall(r + rowSpan, cc)) {
                            canExpandRow = false
                            break
                        }
                    }
                    if (canExpandRow) rowSpan++ else break
                }
                for (rr in r until (r + rowSpan)) {
                    for (cc in c until (c + colSpan)) {
                        visited[rr][cc] = true
                    }
                }
                val rect = Rect(vLines[c], hLines[r], vLines[c + colSpan], hLines[r + rowSpan])
                resultCells.add(TableCell(row = r, col = c, rowSpan = rowSpan, colSpan = colSpan, boundingBox = rect))
            }
        }
        return resultCells
    }

    fun groupIntoMultiline(elements: List<TextLine>, lineOverlapThreshold: Float = 0.5f): List<TextLine> {
        if (elements.isEmpty()) return emptyList()
        val sortedByY = elements.sortedBy { it.boundingBox.centerY() }
        val groupedLines = mutableListOf<MutableList<TextLine>>()
        for (elem in sortedByY) {
            var added = false
            for (lineGroup in groupedLines) {
                val first = lineGroup.first()
                val overlap = calculateVerticalOverlap(first.boundingBox, elem.boundingBox)
                if (overlap >= lineOverlapThreshold) {
                    lineGroup.add(elem)
                    added = true
                    break
                }
            }
            if (!added) groupedLines.add(mutableListOf(elem))
        }
        return groupedLines.map { group ->
            group.sortBy { it.boundingBox.left }
            val combinedText = group.joinToString(" ") { it.text }
            val unionRect = Rect(group.first().boundingBox)
            group.forEach { unionRect.union(it.boundingBox) }
            val boldMajority = group.count { it.isBold } > (group.size / 2)
            val avgColor = group.first().textColor
            TextLine(text = combinedText, boundingBox = unionRect, isBold = boldMajority, textColor = avgColor)
        }
    }

    private fun calculateVerticalOverlap(r1: Rect, r2: Rect): Float {
        val overlapTop = maxOf(r1.top, r2.top)
        val overlapBottom = minOf(r1.bottom, r2.bottom)
        val overlapHeight = maxOf(0, overlapBottom - overlapTop)
        val minHeight = minOf(r1.height(), r2.height()).coerceAtLeast(1)
        return overlapHeight.toFloat() / minHeight.toFloat()
    }
}
