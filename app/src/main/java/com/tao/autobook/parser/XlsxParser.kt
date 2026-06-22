package com.tao.autobook.parser

import java.util.zip.ZipInputStream

/**
 * 解析 xlsx 文件为 CSV 文本（不依赖 Apache POI）
 */
object XlsxParser {

    fun parse(bytes: ByteArray): String {
        return try {
            val zip = ZipInputStream(bytes.inputStream())
            val xmlMap = mutableMapOf<String, String>()
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") {
                    xmlMap[entry.name] = zip.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zip.nextEntry
            }
            zip.close()

            val ss = parseSharedStrings(xmlMap["xl/sharedStrings.xml"] ?: return "")
            val sheet = xmlMap["xl/worksheets/sheet1.xml"] ?: return ""
            parseSheet(sheet, ss)
        } catch (_: Exception) { "" }
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val result = mutableListOf<String>()
        val siRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        for (siMatch in siRegex.findAll(xml)) {
            val text = tRegex.findAll(siMatch.value).joinToString("") {
                it.groupValues[1]
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
            }
            result.add(text)
        }
        return result
    }

    private fun parseSheet(sheetXml: String, sharedStrings: List<String>): String {
        val result = StringBuilder()
        val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        // t="s" = shared string cell
        val sharedCellRegex = Regex("""<c[^>]*r="([A-Z]+)(\d+)"[^>]*t="s"[^>]*><v>(\d+)</v></c>""")
        // All cells with <v>...</v> (catches both numeric and typed cells)
        val anyCellRegex = Regex("""<c[^>]*r="([A-Z]+)(\d+)"[^>]*>(?:<v>)?([^<]*)(?:</v>)?""")

        for (rowMatch in rowRegex.findAll(sheetXml)) {
            val cells = mutableMapOf<Int, String>()
            // First pass: shared string cells
            for (m in sharedCellRegex.findAll(rowMatch.value)) {
                val col = colToInt(m.groupValues[1])
                val idx = m.groupValues[3].toIntOrNull() ?: continue
                if (idx in sharedStrings.indices) cells[col] = sharedStrings[idx]
            }
            // Second pass: numeric/other cells (only if not already set by shared string)
            for (m in anyCellRegex.findAll(rowMatch.value)) {
                val col = colToInt(m.groupValues[1])
                if (col !in cells) {
                    val rawValue = m.groupValues[3]
                    if (rawValue.isNotBlank()) {
                        // Check if this is an Excel date serial number (40000-55000 = 2009-2050)
                        val dbl = rawValue.toDoubleOrNull()
                        if (dbl != null && dbl >= 40000 && dbl <= 55000) {
                            cells[col] = excelSerialToDateTime(dbl)
                        } else {
                            cells[col] = rawValue
                        }
                    }
                }
            }
            if (cells.isNotEmpty()) {
                result.appendLine(cells.toSortedMap().values.joinToString(","))
            }
        }
        return result.toString().trimEnd()
    }

    private fun excelSerialToDateTime(serial: Double): String {
        val days = serial.toLong()
        val timeFraction = serial - days
        // Excel stores local time (UTC+8 for Chinese users)
        val millis = (days - 25569L) * 86400000L + (timeFraction * 86400000.0).toLong()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return String.format(
            "%04d-%02d-%02d %02d:%02d:%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }

    private fun colToInt(col: String): Int {
        var result = 0
        for (c in col) result = result * 26 + (c - 'A' + 1)
        return result
    }
}
