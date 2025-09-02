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
import com.despacho.palletscanner.data.models.Pallet
import com.despacho.palletscanner.viewmodels.MainViewModel
import com.despacho.palletscanner.ui.components.PalletEditDialog

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
                                // NUEVO: Usar el ViewModel para mostrar el dialog de edición
                                viewModel.showPalletEditDialog(palletToEdit)
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
                onSave = { editedPallet ->
                    viewModel.savePalletEdits(editedPallet)
                },
                onDismiss = {
                    viewModel.dismissEditDialog()
                }
            )
        }
    }
}

@Composable
private fun PalletCard(
    pallet: Pallet,
    onEditClick: (Pallet) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Text(
                    text = pallet.numeroPallet,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

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
                    InfoRow("Variedad:", pallet.variedad)
                    InfoRow("Calibre:", pallet.calibre)
                    InfoRow("Embalaje:", pallet.embalaje)
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Columna derecha
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    InfoRow("N° Cajas:", pallet.numeroDeCajas.toString())
                    InfoRow("Peso Unit.:", "${pallet.pesoUnitario} kg")
                    InfoRow("Peso Total:", "${pallet.pesoTotal} kg")
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