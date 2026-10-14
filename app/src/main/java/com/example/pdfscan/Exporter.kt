package com.example.pdfscan

import android.graphics.Color
import java.io.File

object Exporter {
    fun exportToHtmlTable(grid: TableGrid, outputFile: File): File {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>body{font-family:sans-serif;padding:16px;} table{border-collapse:collapse;width:100%;} td{padding:8px;vertical-align:top;}</style></head><body>")
        sb.append("<h3>PDFScan Pro v2.1 - Çıkarılan Tablo (${if (grid.isBorderless) "Çizgisiz Mod" else "Çizgili/Birleşik Hücre Modu"})</h3>")
        val borderStyle = if (grid.isBorderless) "border-bottom:1px solid #ddd;" else "border:1px solid #444;"
        sb.append("<table>")
        val cellsByRow = grid.cells.groupBy { it.row }
        for (r in 0 until grid.rowCount) {
            sb.append("<tr>")
            val rowCells = cellsByRow[r]?.sortedBy { it.col } ?: emptyList()
            for (cell in rowCells) {
                val bgHex = colorToHex(cell.bgColor)
                sb.append("<td rowspan='${cell.rowSpan}' colspan='${cell.colSpan}' style='background-color:$bgHex; $borderStyle'>")
                cell.lines.forEachIndexed { index, line ->
                    val textHex = colorToHex(line.textColor)
                    val openBold = if (line.isBold) "<b>" else ""
                    val closeBold = if (line.isBold) "</b>" else ""
                    sb.append("<span style='color:$textHex;'>$openBold${escapeHtml(line.text)}$closeBold</span>")
                    if (index < cell.lines.size - 1) sb.append("<br/>")
                }
                sb.append("</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</table></body></html>")
        outputFile.writeText(sb.toString(), Charsets.UTF_8)
        return outputFile
    }
    private fun colorToHex(c: Int) = String.format("#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c))
    private fun escapeHtml(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
