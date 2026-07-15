package com.chloemlla.clens.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.chloemlla.clens.R
import com.chloemlla.clens.core.crash.CrashReport
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashReportScreenTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private const val CRASH_STACK_COLLAPSED_LINES = 18
private const val CRASH_EVENT_VISIBLE_COUNT = 12

@Composable
internal fun CrashReportScreen(
    report: CrashReport,
    onContinue: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var stackExpanded by rememberSaveable(report.crashedAtMillis) { mutableStateOf(false) }
    var shareOptionsVisible by rememberSaveable(report.crashedAtMillis) { mutableStateOf(false) }
    val formattedTime = remember(report.crashedAtMillis) {
        Instant.ofEpochMilli(report.crashedAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(crashReportScreenTimeFormatter)
    }
    val stackLineCount = remember(report.stackTrace) {
        report.stackTrace.lineSequence().count()
    }
    val stackPreview = remember(report.stackTrace, stackExpanded) {
        if (stackExpanded) {
            report.stackTrace
        } else {
            report.stackTrace.lineSequence().take(CRASH_STACK_COLLAPSED_LINES).joinToString("\n")
        }
    }
    val systemInfo = remember(report.systemInfo) {
        report.systemInfo.lines().mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.crash_report_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.crash_report_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.Info, stringResource(R.string.crash_report_summary))
                CrashMetaRow(stringResource(R.string.crash_report_id), report.reportId)
                CrashMetaRow(stringResource(R.string.crash_report_time), formattedTime)
                CrashMetaRow(stringResource(R.string.crash_report_exception_type), report.exceptionType)
                CrashMetaRow(stringResource(R.string.crash_report_root_cause), report.rootCause)
                CrashMetaRow(stringResource(R.string.crash_report_thread), report.threadName)
                CrashMetaRow(stringResource(R.string.crash_report_process), report.processName)
            }

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.Devices, stringResource(R.string.crash_report_system_info))
                systemInfo.forEach { (label, value) ->
                    CrashMetaRow(label, value)
                }
            }

            if (report.recentEvents.isNotEmpty()) {
                CrashReportCard {
                    CrashReportSectionHeader(Icons.Outlined.Info, stringResource(R.string.crash_report_recent_events))
                    report.recentEvents.take(CRASH_EVENT_VISIBLE_COUNT).forEach { event ->
                        Text(
                            text = event,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.WarningAmber, stringResource(R.string.crash_report_stack_trace))
                Text(
                    text = pluralStringResource(R.plurals.crash_report_stack_hint, stackLineCount, stackLineCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = stackPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = { stackExpanded = !stackExpanded }) {
                    Icon(
                        imageVector = if (stackExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(
                            if (stackExpanded) R.string.crash_report_collapse_stack else R.string.crash_report_show_full_stack,
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.crash_report_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CrashReportActionPanel(
                onCopyId = {
                    copyText(context, report.reportId)
                    toast(context, context.getString(R.string.crash_report_id_copied))
                },
                onCopyReport = {
                    copyText(context, report.toClipboardText())
                    toast(context, context.getString(R.string.crash_report_copied))
                },
                onShare = { shareOptionsVisible = true },
                onClearAndContinue = {
                    toast(context, context.getString(R.string.crash_report_cleared))
                    onContinue?.invoke()
                },
            )
        }
    }

    if (shareOptionsVisible) {
        AlertDialog(
            onDismissRequest = { shareOptionsVisible = false },
            title = { Text(stringResource(R.string.crash_report_share_options_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.crash_report_share_options_message))
                    OutlinedButton(
                        onClick = {
                            shareOptionsVisible = false
                            shareAsText(context, report)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.crash_report_share_as_text))
                    }
                    Text(
                        text = stringResource(R.string.crash_report_share_as_text_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            shareOptionsVisible = false
                            shareAsFile(context, report)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.crash_report_share_as_file))
                    }
                    Text(
                        text = stringResource(R.string.crash_report_share_as_file_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shareOptionsVisible = false }) {
                    Text(stringResource(R.string.crash_report_share_cancel))
                }
            },
        )
    }
}

@Composable
private fun CrashReportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun CrashReportSectionHeader(icon: ImageVector, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CrashMetaRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrashReportActionPanel(
    onCopyId: () -> Unit,
    onCopyReport: () -> Unit,
    onShare: () -> Unit,
    onClearAndContinue: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onCopyId, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_copy_id))
            }
            OutlinedButton(onClick = onCopyReport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_copy))
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_share))
            }
            Button(onClick = onClearAndContinue, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_clear_and_continue))
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("clens-crash-report", text))
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun shareAsText(context: Context, report: CrashReport) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_share_subject))
            putExtra(Intent.EXTRA_TEXT, report.toClipboardText())
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.crash_report_share)))
    }.onFailure {
        toast(context, context.getString(R.string.crash_report_share_failed))
    }
}

private fun shareAsFile(context: Context, report: CrashReport) {
    runCatching {
        val file = File(context.cacheDir, "clens-crash-${report.reportId}.txt")
        file.writeText(report.toClipboardText(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.crash_report_share)))
    }.onFailure {
        toast(context, context.getString(R.string.crash_report_share_failed))
    }
}