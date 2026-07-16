package com.chloemlla.clens.core.importdata

/**
 * Parsed CSV table with a required header row.
 * [rows] values are raw cell strings (quotes already unescaped).
 */
data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
) {
    val columnCount: Int get() = headers.size
    val rowCount: Int get() = rows.size
}
