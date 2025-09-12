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

    val variedadesList = signalRService.variedadesList
    val activeTrip = signalRService.activeTrip
    val palletProcessed = signalRService.palletProcessed
    val palletError = signalRService.palletError

    // CAMBIO CLAVE: Usar lista sincronizada del escritorio en lugar de lista local
    val scannedPallets: StateFlow<List<Pallet>> = signalRService.palletListFlow

    // NUEVO: Estados para eliminación de pallets
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _palletToDelete = MutableStateFlow<Pallet?>(null)
    val palletToDelete: StateFlow<Pallet?> = _palletToDelete.asStateFlow()

    // Exponer StateFlow de mensajes de éxito del SignalRService
    val successMessage = signalRService.successMessage

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
        viewModelScope.launch {
            palletProcessed.collect { pallet ->
                pallet?.let {
                    Log.d(TAG, "📦 Pallet procesado recibido: ${it.numeroPallet}")
                    // SOLO mostrar dialog de edición, NO agregar a lista
                    showPalletEditDialog(it)
                }
            }
        }

        // NUEVO: Observar mensajes de éxito (SEPARADO del observador anterior)
        viewModelScope.launch {
            successMessage.collect { message ->
                message?.let {
                    Log.d(TAG, "✅ Mensaje de éxito recibido: $it")
                    // El mensaje se mostrará en la UI y luego se limpiará automáticamente
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
    // Método para cargar variedades al mostrar dialog
    fun loadVariedadesForDialog() {
        viewModelScope.launch {
            val success = signalRService.requestVariedades()
            if (!success) {
                Log.w(TAG, "⚠️ No se pudieron solicitar las variedades - Sin conexión")
            }
        }
    }
    // NUEVO: Método para mostrar dialog de confirmación de eliminación
    fun showDeleteConfirmationDialog(pallet: Pallet) {
        _palletToDelete.value = pallet
        _showDeleteDialog.value = true
        Log.d(TAG, "🗑️ Mostrando dialog de eliminación para pallet: ${pallet.numeroPallet}")
    }

    // NUEVO: Método para cerrar el dialog de eliminación
    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
        _palletToDelete.value = null
        Log.d(TAG, "❌ Dialog de eliminación cerrado")
    }

    // NUEVO: Método para ejecutar eliminación de pallet
    fun deletePallet(pallet: Pallet) {
        viewModelScope.launch {
            try {
                val currentTrip = activeTrip.value
                if (currentTrip != null) {
                    Log.d(TAG, "🗑️ Iniciando eliminación del pallet: ${pallet.numeroPallet}")

                    val success = signalRService.deletePalletFromTrip(
                        currentTrip.viajeId.toString(),
                        pallet.numeroPallet
                    )

                    if (success) {
                        Log.d(TAG, "✅ Solicitud de eliminación enviada exitosamente")
                        dismissDeleteDialog()
                        // NOTA: La lista se actualizará automáticamente via PalletListUpdated
                    } else {
                        Log.w(TAG, "⚠️ No se pudo enviar la solicitud de eliminación - Sin conexión")
                    }
                } else {
                    Log.w(TAG, "⚠️ No hay viaje activo para eliminar pallet")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al eliminar pallet: ${e.message}")
            }
        }
    }

    // NUEVO: Método para limpiar mensajes de éxito
    fun clearSuccessMessage() {
        signalRService.clearSuccessMessage()
    }
    // Método para forzar reconexión desde UI
    fun forceReconnect() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Forzando reconexión desde UI...")
            val reconnected = signalRService.forceReconnect()
            Log.d(TAG, if (reconnected) "✅ Reconexión forzada exitosa" else "❌ Reconexión forzada falló")
        }
    }
}