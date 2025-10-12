package com.despacho.palletscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.despacho.palletscanner.data.models.Pallet
import com.despacho.palletscanner.viewmodels.MainViewModel
import com.despacho.palletscanner.ui.components.PalletEditDialog
import com.despacho.palletscanner.ui.components.PalletInfoDialog  // ✅ NUEVO: Importar el diálogo informativo
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalletListScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val scannedPallets by viewModel.scannedPallets.collectAsState()
    val activeTrip by viewModel.activeTrip.collectAsState()

    // NUEVOS StateFlows para el dialog de edición
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val palletToEdit by viewModel.palletToEdit.collectAsState()

    // NUEVOS StateFlows para el dialog de eliminación
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val palletToDelete by viewModel.palletToDelete.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    // ✅ NUEVO: Observar mensajes informativos de pallets PC
    val showInfoDialog by viewModel.showInfoDialog.collectAsState()
    val infoDialogMessage by viewModel.infoDialogMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista de Pallets") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Información del viaje
            activeTrip?.let { trip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Viaje #${trip.numeroViaje}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Guía: ${trip.numeroGuia}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Total Pallets: ${scannedPallets.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de pallets
            if (scannedPallets.isEmpty()) {
                // Estado vacío
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No hay pallets escaneados",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Los pallets aparecerán aquí cuando los escanees",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Lista de pallets
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scannedPallets) { pallet ->
                        PalletCard(
                            pallet = pallet,
                            onEditClick = { palletToEdit ->
                                viewModel.showPalletEditDialog(palletToEdit)
                            },
                            onDeleteClick = { palletToDelete ->
                                viewModel.showDeleteConfirmationDialog(palletToDelete)
                            }
                        )
                    }
                }
            }
        }

        // NUEVO: Dialog de edición de pallet
        if (showEditDialog && palletToEdit != null) {
            PalletEditDialog(
                pallet = palletToEdit!!,
                viewModel = viewModel,
                onSave = { editedPallet ->
                    viewModel.savePalletEdits(editedPallet)
                },
                onDismiss = {
                    viewModel.dismissEditDialog()
                }
            )
        }

        // NUEVO: Dialog de confirmación de eliminación
        if (showDeleteDialog && palletToDelete != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteDialog() },
                title = { Text("Confirmar Eliminación") },
                text = {
                    Text("¿Está seguro de eliminar el pallet ${palletToDelete!!.numeroPallet}? Esta acción no se puede deshacer.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePallet(palletToDelete!!)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.dismissDeleteDialog() }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // ✅ NUEVO: Dialog informativo para pallets PC monocolor
        if (showInfoDialog && infoDialogMessage != null) {
            PalletInfoDialog(
                message = infoDialogMessage!!,
                onDismiss = { viewModel.dismissInfoDialog() }
            )
        }

        // NUEVO: Mostrar mensajes de éxito
        LaunchedEffect(successMessage) {
            successMessage?.let { message ->
                Log.d("PalletListScreen", "✅ $message")
                viewModel.clearSuccessMessage()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PalletCard(
    pallet: Pallet,
    onEditClick: (Pallet) -> Unit,
    onDeleteClick: (Pallet) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Tap normal - no hace nada */ },
                onLongClick = { onDeleteClick(pallet) }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        // NUEVO: Color diferente para pallets bicolor
        colors = CardDefaults.cardColors(
            containerColor = if (pallet.isBicolor) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Encabezado del pallet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = pallet.numeroPallet,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // NUEVO: Indicador de tipo de pallet
                    if (pallet.isBicolor) {
                        Text(
                            text = "BICOLOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onEditClick(pallet) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Editar", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Información del pallet en dos columnas
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Columna izquierda
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // NUEVO: Mostrar variedades usando la propiedad calculada
                    InfoRow("Variedad:", pallet.varietyDisplay)
                    InfoRow("Calibre:", pallet.calibre)
                    InfoRow("Embalaje:", pallet.embalaje)
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Columna derecha
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // NUEVO: Mostrar cajas usando la propiedad calculada
                    InfoRow("N° Cajas:", pallet.totalCajasDisplay)
                    InfoRow("Peso Unit.:", "${pallet.pesoUnitario} kg")
                    InfoRow("Peso Total:", "${pallet.pesoTotal} kg")
                }
            }

            // NUEVO: Información adicional para pallets bicolor
            if (pallet.isBicolor && !pallet.segundaVariedad.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Desglose Bicolor:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        InfoRow("Var. 1:", "${pallet.variedad} (${pallet.numeroDeCajas})")
                        InfoRow("Var. 2:", "${pallet.segundaVariedad} (${pallet.cajasSegundaVariedad})")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifEmpty { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}