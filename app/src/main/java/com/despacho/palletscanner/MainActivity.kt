package com.despacho.palletscanner

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.despacho.palletscanner.data.services.ConfigurationService
import com.despacho.palletscanner.data.services.BicolorPackagingService
import com.despacho.palletscanner.data.services.SignalRService
import com.despacho.palletscanner.ui.Navigation
import com.despacho.palletscanner.ui.theme.PalletScannerTheme
import com.despacho.palletscanner.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    // NUEVO: Declarar servicios necesarios
    private val bicolorPackagingService = BicolorPackagingService()
    private val signalRService = SignalRService()

    // NUEVO: Crear ViewModel con dependencias
    private val viewModel: MainViewModel by lazy {
        MainViewModel(bicolorPackagingService)
    }

    private lateinit var configurationService: ConfigurationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NUEVO: Configurar callback para recibir tipos bicolor del servidor
        signalRService.setBicolorPackagingTypesCallback { types ->
            bicolorPackagingService.updateBicolorTypes(types)
            Log.d("MainActivity", "✅ Tipos bicolor actualizados: $types")
        }

        // Inicializar el servicio de configuración
        configurationService = ConfigurationService(this)

        setContent {
            PalletScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation(
                        viewModel = viewModel,
                        configurationService = configurationService
                    )
                }
            }
        }
    }
}