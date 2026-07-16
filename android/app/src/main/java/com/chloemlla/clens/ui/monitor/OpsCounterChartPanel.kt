package com.chloemlla.clens.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.mongo.OpsCounterPoint
import com.chloemlla.clens.core.mongo.OpsCounterSampleState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private enum class OpsSeries(val key: String, val label: String, val color: Color) {
    Insert("insert", "insert", Color(0xFF2563EB)),
    Query("query", "query", Color(0xFF059669)),
    Update("update", "update", Color(0xFFD97706)),
    Delete("delete", "delete", Color(0xFFDC2626)),
}

@Composable
fun OpsCounterChartPanel(
    sampleState: OpsCounterSampleState?,
    sampling: Boolean,
    error: String?,
    onVisibleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onVisibleChanged(true)
        onDispose { onVisibleChanged(false) }
    }

    val enabled = remember {
        mutableStateMapOf(
            OpsSeries.Insert.key to true,
            OpsSeries.Query.key to true,
            OpsSeries.Update.key to true,
            OpsSeries.Delete.key to true,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Ops Counter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (sampling) "采样中 · 约 5s" else "已暂停（离开管理页）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (sampling) "会话监控" else "历史/暂停",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OpsSeries.entries.forEach { series ->
                    FilterChip(
                        selected = enabled[series.key] == true,
                        onClick = { enabled[series.key] = !(enabled[series.key] ?: true) },
                        label = { Text(series.label) },
                        leadingIcon = {
                            Spacer(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(series.color, CircleShape),
                            )
                        },
                    )
                }
            }

            val points = sampleState?.points.orEmpty()
            if (points.isEmpty()) {
                Text(
                    text = error?.takeIf { it.isNotBlank() }
                        ?: "等待第二个采样点以计算 QPS…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            } else {
                val windowLabel = formatSampleWindow(points)
                val maxValue = chartMaxValue(points, enabled)
                Text(
                    text = windowLabel + " · 采样 " + points.size + " 点 · Ymax " + formatQps(maxValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MultiSeriesLineChart(
                    points = points,
                    enabledSeries = enabled,
                    maxValue = maxValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = formatQps(maxValue / 2.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = formatQps(maxValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            val current = sampleState?.current
            val peak = sampleState?.peak
            MetricGrid(
                currentInsert = current?.insertQps,
                currentQuery = current?.queryQps,
                currentUpdate = current?.updateQps,
                currentDelete = current?.deleteQps,
                peakInsert = peak?.insertQps,
                peakQuery = peak?.queryQps,
                peakUpdate = peak?.updateQps,
                peakDelete = peak?.deleteQps,
            )

            val active = sampleState?.connectionsActive
            val currentConn = sampleState?.connectionsCurrent
            val available = sampleState?.connectionsAvailable
            Text(
                text = buildString {
                    append("连接 active: ")
                    append(active?.toString() ?: "-")
                    append(" · current: ")
                    append(currentConn?.toString() ?: "-")
                    append(" · available: ")
                    append(available?.toString() ?: "-")
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!error.isNullOrBlank() && points.isNotEmpty()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MetricGrid(
    currentInsert: Double?,
    currentQuery: Double?,
    currentUpdate: Double?,
    currentDelete: Double?,
    peakInsert: Double?,
    peakQuery: Double?,
    peakUpdate: Double?,
    peakDelete: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricRow("insert", currentInsert, peakInsert, OpsSeries.Insert.color)
        MetricRow("query", currentQuery, peakQuery, OpsSeries.Query.color)
        MetricRow("update", currentUpdate, peakUpdate, OpsSeries.Update.color)
        MetricRow("delete", currentDelete, peakDelete, OpsSeries.Delete.color)
    }
}

@Composable
private fun MetricRow(name: String, current: Double?, peak: Double?, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
        Text(
            text = "当前 " + formatQps(current) + "  ·  峰值 " + formatQps(peak),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MultiSeriesLineChart(
    points: List<OpsCounterPoint>,
    enabledSeries: Map<String, Boolean>,
    maxValue: Double,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val axisColor = MaterialTheme.colorScheme.outline
    val peakColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val left = 12f
        val right = width - 12f
        val top = 10f
        val bottom = height - 10f
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)

        // horizontal grid + mid emphasis
        for (i in 0..4) {
            val y = top + chartHeight * i / 4f
            drawLine(
                color = if (i == 2) peakColor else gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = if (i == 2) 1.5f else 1f,
            )
        }
        drawLine(axisColor, Offset(left, top), Offset(left, bottom), strokeWidth = 1.5f)
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.5f)

        if (points.isEmpty()) return@Canvas

        val safeMax = max(1.0, maxValue)

        fun xAt(index: Int): Float {
            if (points.size == 1) return left + chartWidth / 2f
            return left + chartWidth * index / (points.size - 1).toFloat()
        }

        fun yAt(value: Double): Float {
            val ratio = (value / safeMax).toFloat().coerceIn(0f, 1f)
            return bottom - chartHeight * ratio
        }

        fun drawSeries(color: Color, selector: (OpsCounterPoint) -> Double) {
            if (points.size == 1) {
                val p = points.first()
                drawCircle(
                    color = color,
                    radius = 3.5f,
                    center = Offset(xAt(0), yAt(selector(p))),
                )
                return
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = xAt(index)
                val y = yAt(selector(point))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round),
            )
        }

        if (enabledSeries[OpsSeries.Insert.key] == true) {
            drawSeries(OpsSeries.Insert.color) { it.insertQps }
        }
        if (enabledSeries[OpsSeries.Query.key] == true) {
            drawSeries(OpsSeries.Query.color) { it.queryQps }
        }
        if (enabledSeries[OpsSeries.Update.key] == true) {
            drawSeries(OpsSeries.Update.color) { it.updateQps }
        }
        if (enabledSeries[OpsSeries.Delete.key] == true) {
            drawSeries(OpsSeries.Delete.color) { it.deleteQps }
        }
    }
}

private fun chartMaxValue(points: List<OpsCounterPoint>, enabledSeries: Map<String, Boolean>): Double {
    if (points.isEmpty()) return 1.0
    val raw = points.maxOf { point ->
        var m = 0.0
        if (enabledSeries[OpsSeries.Insert.key] == true) m = max(m, point.insertQps)
        if (enabledSeries[OpsSeries.Query.key] == true) m = max(m, point.queryQps)
        if (enabledSeries[OpsSeries.Update.key] == true) m = max(m, point.updateQps)
        if (enabledSeries[OpsSeries.Delete.key] == true) m = max(m, point.deleteQps)
        m
    }
    // Pad headroom so lines are not glued to the top edge.
    return max(1.0, raw * 1.15)
}

private fun formatSampleWindow(points: List<OpsCounterPoint>): String {
    if (points.isEmpty()) return "无采样"
    val first = points.first().timestampMillis
    val last = points.last().timestampMillis
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val spanSec = ((last - first).coerceAtLeast(0L) / 1000L)
    return fmt.format(Date(first)) + " → " + fmt.format(Date(last)) + "（" + spanSec + "s）"
}

private fun formatQps(value: Double?): String {
    if (value == null) return "-"
    return if (value >= 100.0) {
        String.format(Locale.US, "%.0f/s", value)
    } else {
        String.format(Locale.US, "%.2f/s", value)
    }
}
