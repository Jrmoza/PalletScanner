package com.despacho.palletscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.despacho.palletscanner.data.models.ServerConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    onNavigateBack: () -> Unit,
    onSaveConfiguration: (ServerConfiguration) -> Unit,
    currentConfig: ServerConfiguration = ServerConfiguration()
) {
    var serverUrl by remember { mutableStateOf(currentConfig.serverUrl) }
    var port by remember { mutableStateOf(currentConfig.port) }
    var hubPath by remember { mutableStateOf(currentConfig.hubPath) }
    var connectionTimeout by remember { mutableStateOf(currentConfig.connectionTimeout.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configuración del Servidor",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("IP del Servidor") },
            placeholder = { Text("192.168.0.103") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Puerto") },
            placeholder = { Text("7146") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = hubPath,
            onValueChange = { hubPath = it },
            label = { Text("Ruta del Hub") },
            placeholder = { Text("/pallethub") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = connectionTimeout,
            onValueChange = { connectionTimeout = it },
            label = { Text("Timeout de Conexión (ms)") },
            placeholder = { Text("10000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    val config = ServerConfiguration(
                        serverUrl = serverUrl,
                        port = port,
                        hubPath = hubPath,
                        connectionTimeout = connectionTimeout.toLongOrNull() ?: 10000L
                    )
                    onSaveConfiguration(config)
                },
                modifier = Modifier.weight(1f),
                enabled = serverUrl.isNotEmpty() && port.isNotEmpty()
            ) {
                Text("Guardar")
            }
        }
    }
}