package com.chloemlla.clens.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Path chips: Database / Collection / Document. Clicking a level clears deeper selection.
 */
@Composable
internal fun BrowseBreadcrumb(
    database: String,
    collection: String,
    documentLabel: String,
    enabled: Boolean = true,
    onClearToRoot: () -> Unit,
    onClearToDatabase: () -> Unit,
    onClearToCollection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "路径",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = database.isBlank(),
            onClick = onClearToRoot,
            enabled = enabled,
            label = { Text("库") },
        )
        if (database.isNotBlank()) {
            BreadcrumbSeparator()
            FilterChip(
                selected = collection.isBlank(),
                onClick = onClearToDatabase,
                enabled = enabled,
                label = {
                    Text(
                        text = database,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (collection.isNotBlank()) {
            BreadcrumbSeparator()
            FilterChip(
                selected = documentLabel.isBlank(),
                onClick = onClearToCollection,
                enabled = enabled,
                label = {
                    Text(
                        text = collection,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (documentLabel.isNotBlank()) {
            BreadcrumbSeparator()
            FilterChip(
                selected = true,
                onClick = { /* already at document */ },
                enabled = enabled,
                label = {
                    Text(
                        text = documentLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun BreadcrumbSeparator() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
