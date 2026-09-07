package com.nowtuneup.app.ui.vehicle

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.vehicle.VehicleProfileRepository
import com.nowtuneup.app.domain.model.VehicleProfileRecord
import com.nowtuneup.app.ui.motion.NowTuneUpLogoMark
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VehicleProfileUiEntryPoint {
    fun vehicleProfileRepository(): VehicleProfileRepository
}

@Composable
fun rememberVehicleProfileRepository(): VehicleProfileRepository {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VehicleProfileUiEntryPoint::class.java,
        ).vehicleProfileRepository()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleProfilesScreen(
    repository: VehicleProfileRepository,
    onContinue: (() -> Unit)? = null,
    showBackAction: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val profiles by repository.profiles.collectAsState()
    val active = profiles.firstOrNull { it.isActive }
    var editor by remember { mutableStateOf<VehicleProfileRecord?>(null) }
    var deleteCandidate by remember { mutableStateOf<VehicleProfileRecord?>(null) }

    editor?.let { profile ->
        VehicleProfileEditorDialog(
            initial = profile,
            isNew = profile.id.isBlank(),
            onDismiss = { editor = null },
            onSave = { saved ->
                if (profile.id.isBlank()) repository.create(saved) else repository.update(saved)
                editor = null
            },
        )
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete vehicle profile?") },
            text = {
                Text(
                    if (profiles.size == 1) {
                        "${profile.displayName} is your last vehicle. Deleting it returns NowTuneUp to vehicle setup."
                    } else {
                        "${profile.displayName} will be removed from this device."
                    },
                )
            },
            confirmButton = {
                Button(onClick = { repository.delete(profile.id); deleteCandidate = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vehicles", fontWeight = FontWeight.Black)
                        Text(
                            if (profiles.isEmpty()) "Create your first vehicle profile" else "${profiles.size} user-created vehicle${if (profiles.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    if (showBackAction && onBack != null) TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    IconButton(onClick = { editor = VehicleProfileRecord(userCreated = true) }) {
                        Icon(Icons.Default.Add, contentDescription = "Create vehicle profile")
                    }
                },
            )
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            VehicleProfileEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onCreate = { editor = VehicleProfileRecord(userCreated = true) },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Active vehicle", style = MaterialTheme.typography.labelLarge)
                            Text(active?.displayName ?: "Choose a vehicle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text(
                                "Only vehicles you create are stored. VIN detection and OBD scans never create a vehicle automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    VehicleProfileCard(
                        profile = profile,
                        onActivate = { repository.setActive(profile.id) },
                        onEdit = { editor = profile },
                        onDuplicate = { repository.duplicate(profile.id) },
                        onDelete = { deleteCandidate = profile },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { editor = VehicleProfileRecord(userCreated = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Create another vehicle")
                    }
                }
                if (onContinue != null) {
                    item {
                        Button(onClick = onContinue, enabled = active != null, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("  Continue to NowTuneUp")
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun VehicleProfileEmptyState(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NowTuneUpLogoMark(modifier = Modifier.height(112.dp).fillMaxWidth(0.48f))
                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                Text("No vehicles yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Create your first vehicle profile to start using NowTuneUp. No demo vehicle, detected VIN, or sample vehicle will be created for you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Create Vehicle Profile")
                }
            }
        }
    }
}

@Composable
private fun VehicleProfileCard(
    profile: VehicleProfileRecord,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        listOf(profile.brand, profile.model, profile.year).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (profile.isActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Active") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    )
                }
            }
            Text(
                listOf(profile.engine, profile.fuelType, profile.transmission).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            profile.vin.takeIf { it.isNotBlank() }?.let { Text("VIN $it", style = MaterialTheme.typography.labelSmall) }
            profile.notes.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!profile.isActive) {
                    FilledTonalButton(onClick = onActivate, modifier = Modifier.weight(1f)) { Text("Use") }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${profile.displayName}") }
                IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate ${profile.displayName}") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.displayName}") }
            }
        }
    }
}

@Composable
private fun VehicleProfileEditorDialog(
    initial: VehicleProfileRecord,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (VehicleProfileRecord) -> Unit,
) {
    var displayName by remember(initial.id) { mutableStateOf(initial.displayName) }
    var brand by remember(initial.id) { mutableStateOf(initial.brand) }
    var model by remember(initial.id) { mutableStateOf(initial.model) }
    var year by remember(initial.id) { mutableStateOf(initial.year) }
    var engine by remember(initial.id) { mutableStateOf(initial.engine) }
    var fuelType by remember(initial.id) { mutableStateOf(initial.fuelType) }
    var transmission by remember(initial.id) { mutableStateOf(initial.transmission) }
    var vin by remember(initial.id) { mutableStateOf(initial.vin) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }

    val requiredValid = listOf(displayName, brand, model, year, engine, fuelType, transmission).all { it.isNotBlank() }
    val yearValid = year.length == 4 && year.all { it.isDigit() }
    val vinNormalized = vin.trim().uppercase()
    val vinValid = vinNormalized.isBlank() || vinNormalized.length == 17
    val canSave = requiredValid && yearValid && vinValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Create Vehicle Profile" else "Edit Vehicle Profile", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("VIN is optional and never creates another profile automatically.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(displayName, { displayName = it }, label = { Text("Vehicle name / nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = year,
                    onValueChange = { value -> year = value.filter { it.isDigit() }.take(4) },
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = year.isNotBlank() && !yearValid,
                    supportingText = { if (year.isNotBlank() && !yearValid) Text("Enter a 4-digit year") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(engine, { engine = it }, label = { Text("Engine") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fuelType, { fuelType = it }, label = { Text("Fuel type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(transmission, { transmission = it }, label = { Text("Transmission") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(17) },
                    label = { Text("VIN (optional)") },
                    isError = vin.isNotBlank() && !vinValid,
                    supportingText = { if (vin.isNotBlank() && !vinValid) Text("VIN must contain 17 characters") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        initial.copy(
                            displayName = displayName.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            year = year.trim(),
                            engine = engine.trim(),
                            fuelType = fuelType.trim(),
                            transmission = transmission.trim(),
                            vin = vinNormalized,
                            notes = notes.trim(),
                            userCreated = true,
                        ),
                    )
                },
            ) { Text(if (isNew) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
