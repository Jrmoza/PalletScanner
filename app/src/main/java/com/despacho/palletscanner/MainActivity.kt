package com.despacho.palletscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.despacho.palletscanner.data.services.ConfigurationService
import com.despacho.palletscanner.ui.Navigation
import com.despacho.palletscanner.ui.theme.PalletScannerTheme
import com.despacho.palletscanner.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var configurationService: ConfigurationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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