package com.despacho.palletscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.despacho.palletscanner.viewmodels.MainViewModel
import com.despacho.palletscanner.ui.components.PalletEditDialog
import com.despacho.palletscanner.scanner.BarcodeScanner
import androidx.compose.material.icons.filled.Refresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToPalletList: () -> Unit,
    onNavigateToConfiguration: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val activeTrip by viewModel.activeTrip.collectAsState()
    val palletProcessed by viewModel.palletProcessed.collectAsState()
    val palletError by viewModel.palletError.collectAsState()
    val scannedPallets by viewModel.scannedPallets.collectAsState()

    // NUEVOS StateFlows para el dialog de edición
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val palletToEdit by viewModel.palletToEdit.collectAsState()

    var manualInput by remember { mutableStateOf("") }
    // NUEVO: Estado para mostrar cámara
    var showCamera by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pallet Scanner") },
                actions = {
                    IconButton(onClick = onNavigateToConfiguration) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Estado de conexión
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (connectionState)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = if (connectionState) "✅ Conectado" else "❌ Desconectado",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Información del viaje activo
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Viaje Activo",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    activeTrip?.let { trip ->
                        Text("Número: ${trip.numeroViaje}")
                        Text("Guía: ${trip.numeroGuia}")
                        Text("Responsable: ${trip.responsable}")
                        Text("Fecha: ${trip.fecha}")
                        Text("Estado: ${trip.estado}")
                    } ?: run {
                        Text(
                            text = "No hay viaje activo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Contador de pallets
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Pallets Escaneados: ${scannedPallets.size}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Sección de escaneo ACTUALIZADA
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Escanear Pallet",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it.uppercase() },
                        label = { Text("Número de Pallet") },
                        placeholder = { Text("Ingrese o escanee el código") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectionState && activeTrip != null
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // NUEVO: Botones de escaneo manual y cámara
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (manualInput.isNotBlank()) {
                                    viewModel.scanPallet(manualInput)
                                    manualInput = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = connectionState && activeTrip != null && manualInput.isNotBlank()
                        ) {
                            Text("Escanear Manual")
                        }

                        Button(
                            onClick = { showCamera = true },
                            modifier = Modifier.weight(1f),
                            enabled = connectionState && activeTrip != null
                        ) {
                            Text("📷 Cámara")
                        }
                    }
                }
            }

            // Mostrar errores si los hay
            palletError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Botón para ver lista de pallets
            Button(
                onClick = onNavigateToPalletList,
                modifier = Modifier.fillMaxWidth(),
                enabled = scannedPallets.isNotEmpty()
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Lista de Pallets (${scannedPallets.size})")
            }
            Button(
                onClick = {
                    viewModel.forceReconnect()
                },
                modifier = Modifier.fillMaxWidth(),
                //enabled = !connectionState,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🔄 Sincronizar")
            }
        }
        // NUEVO: Botón de sincronización (AGREGAR AQUÍ)



        // Dialog de edición de pallet
        if (showEditDialog && palletToEdit != null) {
            PalletEditDialog(
                pallet = palletToEdit!!,
                viewModel = viewModel, // AGREGAR ESTA LÍNEA
                onSave = { editedPallet ->
                    viewModel.savePalletEdits(editedPallet)
                },
                onDismiss = {
                    viewModel.dismissEditDialog()
                }
            )
        }

        // NUEVO: Scanner de cámara
        if (showCamera) {
            BarcodeScanner(
                onBarcodeDetected = { barcode ->
                    viewModel.scanPallet(barcode)
                    showCamera = false
                },
                onClose = { showCamera = false }
            )
        }
    }
}