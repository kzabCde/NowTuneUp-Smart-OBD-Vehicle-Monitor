package com.nowtuneup.app.ui.dashboard.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditor(config: DashboardConfig, drivingMode: Boolean, onSave: (DashboardConfig) -> Unit, onCancel: () -> Unit) {
    var draft by remember(config) { mutableStateOf(config) }
    if (drivingMode) AlertDialog(onDismissRequest = onCancel, confirmButton = { TextButton(onClick = onCancel) { Text("OK") } }, title = { Text("Park before editing") }, text = { Text("Dashboard layout is locked while Driving Mode is active.") })
    else Scaffold(topBar = { TopAppBar(title = { Text("Dashboard Editor") }, navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } }, actions = { TextButton(onClick = { onSave(draft) }) { Text("Save") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(draft.name, { draft = draft.copy(name = it.take(40)) }, label = { Text("Dashboard name") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("Dashboard mode", style = MaterialTheme.typography.titleMedium); Row { DashboardMode.entries.forEach { mode -> FilterChip(draft.mode == mode, { draft = draft.copy(mode = mode, portrait = draft.portrait.copy(widgets = draft.portrait.widgets.map { it.copy(type = if (mode == DashboardMode.DIGITAL) DashboardWidgetType.DIGITAL else if (mode == DashboardMode.ANALOG) DashboardWidgetType.ANALOG else it.type) })) }, { Text(mode.name) }, modifier = Modifier.padding(end = 6.dp)) } } }
            item { Text("Portrait columns: ${draft.portrait.columns}"); Slider(draft.portrait.columns.toFloat(), { draft = draft.copy(portrait = draft.portrait.copy(columns = it.toInt())) }, valueRange = 1f..4f, steps = 2) }
            item { Text("Widgets", style = MaterialTheme.typography.titleMedium) }
            items(draft.portrait.widgets, key = { it.id }) { widget -> ListItem(headlineContent = { Text(widget.title) }, supportingContent = { Text("${widget.type.name} · ${widget.unit.name}") }, trailingContent = { TextButton(onClick = { draft = draft.copy(portrait = draft.portrait.copy(widgets = draft.portrait.widgets - widget)) }) { Text("Remove") } }) }
            item { Button(onClick = { val candidate = DashboardDefaults.presets.first().portrait.widgets.firstOrNull { source -> draft.portrait.widgets.none { it.pid == source.pid } }; if (candidate != null) draft = draft.copy(portrait = draft.portrait.copy(widgets = draft.portrait.widgets + candidate.copy(id = "${candidate.id}-${System.currentTimeMillis()}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Add widget") } }
            item { OutlinedButton(onClick = { draft = DashboardDefaults.presets.first().copy(id = config.id, name = config.name) }, modifier = Modifier.fillMaxWidth()) { Text("Reset dashboard") } }
        }
    }
}
