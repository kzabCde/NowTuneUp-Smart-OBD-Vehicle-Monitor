package com.nowtuneup.app.ui.dashboard.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditor(
    config: DashboardConfig,
    drivingMode: Boolean,
    onSave: (DashboardConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(config) { mutableStateOf(config) }

    if (drivingMode) {
        AlertDialog(
            onDismissRequest = onCancel,
            confirmButton = { TextButton(onClick = onCancel) { Text("OK") } },
            title = { Text("Park before editing") },
            text = { Text("Dashboard layout is locked while Driving Mode is active.") },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard editor") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text("Dashboard name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                Text("Display mode", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DashboardMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.mode == mode,
                            onClick = { draft = draft.applyMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            }
            item {
                Text("Portrait columns: ${draft.portrait.columns}")
                Slider(
                    value = draft.portrait.columns.toFloat(),
                    onValueChange = {
                        draft = draft.copy(
                            portrait = draft.portrait.copy(columns = it.toInt().coerceIn(1, 4)),
                        )
                    },
                    valueRange = 1f..4f,
                    steps = 2,
                )
            }
            item {
                Text("Widgets", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose each widget style and width. Changes appear after Save.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(draft.portrait.widgets, key = { it.id }) { widget ->
                WidgetEditorCard(
                    widget = widget,
                    columns = draft.portrait.columns,
                    onChange = { changed ->
                        draft = draft.copy(
                            portrait = draft.portrait.copy(
                                widgets = draft.portrait.widgets.map {
                                    if (it.id == changed.id) changed else it
                                },
                            ),
                        )
                    },
                    onRemove = {
                        draft = draft.copy(
                            portrait = draft.portrait.copy(
                                widgets = draft.portrait.widgets.filterNot { it.id == widget.id },
                            ),
                        )
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        val source = DashboardDefaults.presets
                            .flatMap { it.portrait.widgets }
                            .firstOrNull { candidate ->
                                draft.portrait.widgets.none { it.pid == candidate.pid }
                            }
                        if (source != null) {
                            draft = draft.copy(
                                portrait = draft.portrait.copy(
                                    widgets = draft.portrait.widgets + source.copy(
                                        id = "${source.id}-${System.currentTimeMillis()}",
                                    ),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add available widget") }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val default = DashboardDefaults.presets.first()
                        draft = default.copy(id = config.id, name = config.name)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset dashboard") }
            }
        }
    }
}

@Composable
private fun WidgetEditorCard(
    widget: DashboardWidgetConfig,
    columns: Int,
    onChange: (DashboardWidgetConfig) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(widget.title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DashboardWidgetType.entries.forEach { type ->
                    FilterChip(
                        selected = widget.type == type,
                        onClick = { onChange(widget.copy(type = type)) },
                        label = { Text(type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Width: ${widget.columnSpan.coerceIn(1, columns)} of $columns")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            onChange(widget.copy(columnSpan = (widget.columnSpan - 1).coerceAtLeast(1)))
                        },
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = {
                            onChange(widget.copy(columnSpan = (widget.columnSpan + 1).coerceAtMost(columns)))
                        },
                    ) { Text("+") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

private fun DashboardConfig.applyMode(mode: DashboardMode): DashboardConfig {
    val type = when (mode) {
        DashboardMode.DIGITAL -> DashboardWidgetType.DIGITAL
        DashboardMode.ANALOG -> DashboardWidgetType.ANALOG
        DashboardMode.HYBRID -> null
    }
    return copy(
        mode = mode,
        portrait = portrait.copy(
            widgets = portrait.widgets.map { widget ->
                type?.let { widget.copy(type = it) } ?: widget
            },
        ),
    )
}
