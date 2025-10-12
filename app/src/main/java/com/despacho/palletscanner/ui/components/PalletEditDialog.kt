package com.despacho.palletscanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.despacho.palletscanner.data.models.Pallet
import com.despacho.palletscanner.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalletEditDialog(
    pallet: Pallet,
    onSave: (Pallet) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    // Estados locales para edición - CAMPOS ORIGINALES
    var variedad by remember { mutableStateOf(pallet.variedad) }
    var calibre by remember { mutableStateOf(pallet.calibre) }
    var embalaje by remember { mutableStateOf(pallet.embalaje) }
    var numeroDeCajas by remember { mutableStateOf(pallet.numeroDeCajas.toString()) }

    // NUEVOS: Estados para campos bicolor E50G6CB
    var segundaVariedad by remember { mutableStateOf(pallet.segundaVariedad ?: "") }
    var cajasSegundaVariedad by remember { mutableStateOf(pallet.cajasSegundaVariedad.toString()) }

    // Estados para dropdowns
    var expandedVariedad by remember { mutableStateOf(false) }
    var expandedSegundaVariedad by remember { mutableStateOf(false) }

    // Observar lista de variedades
    val variedadesList by viewModel.variedadesList.collectAsState()

    // Detectar si es pallet bicolor
    val isBicolor = pallet.isBicolor

    // Estado de scroll para el contenido
    val scrollState = rememberScrollState()

    // Cargar variedades al mostrar el dialog
    LaunchedEffect(Unit) {
        viewModel.loadVariedadesForDialog()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f) // Limitar altura máxima al 90% de la pantalla
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // CONTENIDO SCROLLEABLE
                Column(
                    modifier = Modifier
                        .weight(1f) // Toma todo el espacio disponible menos los botones
                        .verticalScroll(scrollState) // Hacer scrolleable
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Título con indicador bicolor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Editar Pallet: ${pallet.numeroPallet}",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        if (isBicolor) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = "BICOLOR",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Campo de variedad principal con lista desplegable
                    ExposedDropdownMenuBox(
                        expanded = expandedVariedad,
                        onExpandedChange = { expandedVariedad = !expandedVariedad }
                    ) {
                        OutlinedTextField(
                            value = variedad,
                            onValueChange = { variedad = it },
                            label = { Text(if (isBicolor) "Primera Variedad" else "Variedad") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVariedad)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expandedVariedad,
                            onDismissRequest = { expandedVariedad = false }
                        ) {
                            variedadesList.forEach { variedadOption ->
                                DropdownMenuItem(
                                    text = { Text(variedadOption) },
                                    onClick = {
                                        variedad = variedadOption
                                        expandedVariedad = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = calibre,
                        onValueChange = { calibre = it },
                        label = { Text("Calibre") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = embalaje,
                        onValueChange = { embalaje = it },
                        label = { Text("Embalaje") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = numeroDeCajas,
                        onValueChange = { numeroDeCajas = it },
                        label = { Text(if (isBicolor) "Cajas Primera Variedad" else "Número de Cajas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // SECCIÓN BICOLOR -
                    if (isBicolor) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Información Bicolor",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                // Segunda variedad con dropdown
                                ExposedDropdownMenuBox(
                                    expanded = expandedSegundaVariedad,
                                    onExpandedChange = { expandedSegundaVariedad = !expandedSegundaVariedad }
                                ) {
                                    OutlinedTextField(
                                        value = segundaVariedad,
                                        onValueChange = { segundaVariedad = it },
                                        label = { Text("Segunda Variedad") },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSegundaVariedad)
                                        },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expandedSegundaVariedad,
                                        onDismissRequest = { expandedSegundaVariedad = false }
                                    ) {
                                        variedadesList.forEach { variedadOption ->
                                            DropdownMenuItem(
                                                text = { Text(variedadOption) },
                                                onClick = {
                                                    segundaVariedad = variedadOption
                                                    expandedSegundaVariedad = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Cajas segunda variedad
                                OutlinedTextField(
                                    value = cajasSegundaVariedad,
                                    onValueChange = { cajasSegundaVariedad = it },
                                    label = { Text("Cajas Segunda Variedad") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Resumen de totales para bicolor
                                val totalCajas = (numeroDeCajas.toIntOrNull() ?: 0) + (cajasSegundaVariedad.toIntOrNull() ?: 0)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Resumen Bicolor:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Total Cajas: $totalCajas",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Variedades: $variedad + $segundaVariedad",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Información no editable
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Peso Unitario: ${pallet.pesoUnitario} kg")
                            Text("Peso Total: ${pallet.pesoTotal} kg")
                        }
                    }
                }

                // BOTONES FIJOS EN LA PARTE INFERIOR
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                val editedPallet = if (isBicolor) {
                                    // Para pallets bicolor E50G6CB
                                    pallet.copy(
                                        variedad = variedad,
                                        calibre = calibre,
                                        embalaje = embalaje,
                                        numeroDeCajas = numeroDeCajas.toIntOrNull() ?: pallet.numeroDeCajas,
                                        segundaVariedad = segundaVariedad.takeIf { it.isNotBlank() },
                                        cajasSegundaVariedad = cajasSegundaVariedad.toIntOrNull() ?: 0
                                    )
                                } else {
                                    // Para pallets normales
                                    pallet.copy(
                                        variedad = variedad,
                                        calibre = calibre,
                                        embalaje = embalaje,
                                        numeroDeCajas = numeroDeCajas.toIntOrNull() ?: pallet.numeroDeCajas,
                                        segundaVariedad = null,
                                        cajasSegundaVariedad = 0
                                    )
                                }
                                onSave(editedPallet)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}