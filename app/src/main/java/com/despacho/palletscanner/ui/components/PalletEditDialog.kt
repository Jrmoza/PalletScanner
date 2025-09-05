package com.despacho.palletscanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Estados locales para edición
    var variedad by remember { mutableStateOf(pallet.variedad) }
    var calibre by remember { mutableStateOf(pallet.calibre) }
    var embalaje by remember { mutableStateOf(pallet.embalaje) }
    var numeroDeCajas by remember { mutableStateOf(pallet.numeroDeCajas.toString()) }
    // NUEVO: Estado para el dropdown de variedades
    var expandedVariedad by remember { mutableStateOf(false) }

    // NUEVO: Observar lista de variedades
    val variedadesList by viewModel.variedadesList.collectAsState()

    // NUEVO: Cargar variedades al mostrar el dialog
    LaunchedEffect(Unit) {
        viewModel.loadVariedadesForDialog()
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Título
                Text(
                    text = "Editar Pallet: ${pallet.numeroPallet}",
                    style = MaterialTheme.typography.headlineSmall
                )

            // Campo de variedad con lista desplegable
                ExposedDropdownMenuBox(
                    expanded = expandedVariedad,
                    onExpandedChange = { expandedVariedad = !expandedVariedad }
                ) {
                    OutlinedTextField(
                        value = variedad,
                        onValueChange = { variedad = it },
                        label = { Text("Variedad") },
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
                    label = { Text("Número de Cajas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

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

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            val editedPallet = pallet.copy(
                                variedad = variedad,
                                calibre = calibre,
                                embalaje = embalaje,
                                numeroDeCajas = numeroDeCajas.toIntOrNull() ?: pallet.numeroDeCajas
                            )
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