package com.despacho.palletscanner.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import com.despacho.palletscanner.data.services.SignalRService
import com.despacho.palletscanner.data.services.ConfigurationService
import com.despacho.palletscanner.data.models.Pallet
import com.despacho.palletscanner.data.models.ServerConfiguration

class MainViewModel : ViewModel() {
    private val signalRService = SignalRService()

    // Exponer StateFlows para UI
    val connectionState = signalRService.connectionState
    val activeTrip = signalRService.activeTrip
    val palletProcessed = signalRService.palletProcessed
    val palletError = signalRService.palletError

    // CAMBIO CLAVE: Usar lista sincronizada del escritorio en lugar de lista local
    val scannedPallets: StateFlow<List<Pallet>> = signalRService.palletListFlow

    // Estado para mostrar el dialog de edición
    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _palletToEdit = MutableStateFlow<Pallet?>(null)
    val palletToEdit: StateFlow<Pallet?> = _palletToEdit.asStateFlow()

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        // SOLO observar pallets procesados para mostrar dialog de edición
        // NO agregar a lista local (ahora viene del escritorio)
        viewModelScope.launch {
            palletProcessed.collect { pallet ->
                pallet?.let {
                    Log.d(TAG, "📦 Pallet procesado recibido: ${it.numeroPallet}")
                    // SOLO mostrar dialog de edición, NO agregar a lista
                    showPalletEditDialog(it)
                }
            }
        }

        // NUEVO: Observar lista sincronizada del escritorio
        viewModelScope.launch {
            scannedPallets.collect { pallets ->
                Log.d(TAG, "📋 Lista sincronizada actualizada - Count: ${pallets.size}")
            }
        }
    }

    fun connectToServer(serverConfig: ServerConfiguration) {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Iniciando conexión al servidor: ${serverConfig.getFullUrl()}")
            val connected = signalRService.connect(serverConfig)
            Log.d(TAG, if (connected) "✅ Conexión exitosa" else "❌ Conexión falló")
        }
    }

    fun scanPallet(palletNumber: String) {
        viewModelScope.launch {
            Log.d(TAG, "📱 Enviando pallet escaneado: $palletNumber")
            val sent = signalRService.sendPalletNumber(palletNumber)
            if (!sent) {
                Log.w(TAG, "⚠️ No se pudo enviar el pallet - Sin conexión")
            }
        }
    }

    fun clearStates() {
        signalRService.clearStates()
        Log.d(TAG, "🧹 Estados limpiados")
    }

    // ELIMINADO: clearScannedPallets() ya que ahora la lista viene del escritorio

    // Método para mostrar el dialog de edición cuando se recibe un pallet procesado
    fun showPalletEditDialog(pallet: Pallet) {
        _palletToEdit.value = pallet
        _showEditDialog.value = true
        Log.d(TAG, "📝 Mostrando dialog de edición para pallet: ${pallet.numeroPallet}")
    }

    // Método para cerrar el dialog de edición
    fun dismissEditDialog() {
        _showEditDialog.value = false
        _palletToEdit.value = null
        Log.d(TAG, "❌ Dialog de edición cerrado")
    }

    // Método para guardar pallet editado
    fun savePalletEdits(editedPallet: Pallet) {
        viewModelScope.launch {
            Log.d(TAG, "💾 Guardando ediciones del pallet: ${editedPallet.numeroPallet}")
            val sent = signalRService.sendPalletWithEdits(editedPallet)
            if (sent) {
                Log.d(TAG, "✅ Ediciones enviadas exitosamente")
                dismissEditDialog()
                // NOTA: La lista se actualizará automáticamente via PalletListUpdated
            } else {
                Log.w(TAG, "⚠️ No se pudieron enviar las ediciones - Sin conexión")
            }
        }
    }

    // NUEVO: Método para obtener pallet específico de la lista sincronizada
    fun getPalletByNumber(palletNumber: String): Pallet? {
        return scannedPallets.value.find { it.numeroPallet == palletNumber }
    }

    // NUEVO: Método para obtener conteo total de pallets
    fun getTotalPalletsCount(): Int {
        return scannedPallets.value.size
    }
}