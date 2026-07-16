package com.chloemlla.clens.ui.query

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.mongo.VisualFilterClause
import com.chloemlla.clens.core.mongo.VisualFilterOp

@Composable
fun VisualQueryBuilder(
    clauses: List<VisualFilterClause>,
    suggestedFields: List<String>,
    enabled: Boolean,
    onClauseChange: (Int, VisualFilterClause) -> Unit,
    onAddClause: () -> Unit,
    onRemoveClause: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "可视化条件",
            style = MaterialTheme.typography.titleSmall,
        )
        if (suggestedFields.isNotEmpty()) {
            Text(
                text = "建议字段（点选填入）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestedFields.take(24).forEach { field ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (!enabled) return@FilterChip
                            val index = clauses.indexOfFirst { it.field.isBlank() }.takeIf { it >= 0 }
                                ?: clauses.lastIndex.coerceAtLeast(0)
                            val target = clauses.getOrNull(index) ?: VisualFilterClause()
                            onClauseChange(index, target.copy(field = field))
                        },
                        enabled = enabled,
                        label = { Text(field) },
                    )
                }
            }
        } else {
            Text(
                text = "暂无推断字段，可手动输入；执行查询后会根据结果补全。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        clauses.forEachIndexed { index, clause ->
            ClauseRow(
                index = index,
                clause = clause,
                enabled = enabled,
                canRemove = clauses.size > 1,
                onClauseChange = { onClauseChange(index, it) },
                onRemove = { onRemoveClause(index) },
            )
        }

        OutlinedButton(
            onClick = onAddClause,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("添加条件")
        }
    }
}

@Composable
private fun ClauseRow(
    index: Int,
    clause: VisualFilterClause,
    enabled: Boolean,
    canRemove: Boolean,
    onClauseChange: (VisualFilterClause) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "条件 ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = clause.field,
            onValueChange = { onClauseChange(clause.copy(field = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("字段") },
            placeholder = { Text("例如 status 或 user.name") },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisualFilterOp.entries.forEach { op ->
                FilterChip(
                    selected = clause.op == op,
                    onClick = { onClauseChange(clause.copy(op = op)) },
                    enabled = enabled,
                    label = { Text(op.label) },
                )
            }
        }
        OutlinedTextField(
            value = clause.value,
            onValueChange = { onClauseChange(clause.copy(value = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            label = {
                Text(
                    when (clause.op) {
                        VisualFilterOp.In -> "值（逗号分隔或 JSON 数组）"
                        VisualFilterOp.Exists -> "值（true/false）"
                        VisualFilterOp.Regex -> "正则表达式"
                        else -> "值"
                    },
                )
            },
            placeholder = {
                Text(
                    when (clause.op) {
                        VisualFilterOp.In -> "a, b 或 [\"a\",\"b\"]"
                        VisualFilterOp.Exists -> "true"
                        VisualFilterOp.Regex -> "^active"
                        else -> "支持字符串 / 数字 / true / null"
                    },
                )
            },
        )
        if (canRemove) {
            OutlinedButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除此条件")
            }
        }
    }
}
