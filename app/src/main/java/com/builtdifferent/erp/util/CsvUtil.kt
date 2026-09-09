package com.builtdifferent.erp.util

import java.io.File
import java.io.FileWriter

/**
 * A hand-rolled CSV writer/reader rather than a library dependency: the
 * quoting rule needed here is the single standard one (wrap in double
 * quotes and escape embedded quotes by doubling them), and pulling in a
 * full CSV library for that is unnecessary weight in an offline-only app.
 */
object CsvUtil {

    fun escape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun writeCsv(file: File, header: List<String>, rows: List<List<String>>) {
        FileWriter(file).use { writer ->
            writer.append(header.joinToString(",") { escape(it) }).append("\n")
            rows.forEach { row ->
                writer.append(row.joinToString(",") { escape(it) }).append("\n")
            }
        }
    }

    /** Parses one CSV line respecting quoted fields with embedded commas —
     * NOT a full RFC 4180 parser (no multi-line quoted fields), which is
     * sufficient for the flat master-data exports this app produces and
     * re-imports (parties, products). */
    fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    fun readCsv(file: File): Pair<List<String>, List<List<String>>> {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList<String>() to emptyList()
        val header = parseLine(lines.first())
        val rows = lines.drop(1).map(::parseLine)
        return header to rows
    }
}
