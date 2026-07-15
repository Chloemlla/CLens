package com.chloemlla.clens.core.crash

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashBreadcrumbTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

object CrashBreadcrumbs {
    private const val MAX_EVENTS = 40
    private val events = ArrayDeque<String>(MAX_EVENTS)

    @Synchronized
    fun record(event: String) {
        val sanitized = CrashReportSanitizer.sanitize(event).take(180)
        if (sanitized.isBlank()) return
        if (events.size >= MAX_EVENTS) {
            events.removeFirst()
        }
        val time = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(crashBreadcrumbTimeFormatter)
        events.addLast("$time  $sanitized")
    }

    @Synchronized
    fun snapshot(): List<String> = events.toList()
}