package com.despacho.palletscanner.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import com.despacho.palletscanner.data.services.SignalRService
import com.despacho.palletscanner.data.services.ConfigurationService
import com.despacho.palletscanner.data.services.BicolorPackagingService
import com.despacho.palletscanner.data.models.Pallet
import com.despacho.palletscanner.data.models.ServerConfiguration

class MainViewModel(
    private val bicolorPackagingService: BicolorPackagingService = BicolorPackagingService()
) : ViewModel() {
    private val signalRService = SignalRService()

    // Exponer StateFlows para UI
    val connectionState = signalRService.connectionState

    val variedadesList = signalRService.variedadesList
    val activeTrip = signalRService.activeTrip
    val palletProcessed = signalRService.palletProcessed
    val palletError = signalRService.palletError

    val palletInfoMessage = signalRService.palletInfoMessage
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

    // NUEVO: Estados específicos para pallets bicolor
    private val _showBicolorFields = MutableStateFlow(false)
    val showBicolorFields: StateFlow<Boolean> = _showBicolorFields.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()

    private val _infoDialogMessage = MutableStateFlow<String?>(null)
    val infoDialogMessage: StateFlow<String?> = _infoDialogMessage.asStateFlow()
    companion object {
        private const val TAG = "MainViewModel"
        // ELIMINADO: private const val BICOLOR_EMBALAJE = "E50G6CB" - Ya no hardcodeamos
    }

    init {
        // NUEVO: Solicitar tipos de embalaje bicolor al conectar
        viewModelScope.launch {
            signalRService.requestBicolorPackagingTypes()
        }

        // NUEVO: Configurar callback para recibir tipos bicolor del servidor
        signalRService.setBicolorPackagingTypesCallback { types ->
            bicolorPackagingService.updateBicolorTypes(types)
            Log.d(TAG, "📋 Tipos de embalaje bicolor actualizados: $types")
        }

        // SOLO observar pallets procesados para mostrar dialog de edición
        viewModelScope.launch {
            palletProcessed.collect { pallet ->
                pallet?.let {
                    Log.d(TAG, "📦 Pallet procesado recibido: ${it.numeroPallet}")

                    // NUEVO: Detectar si es pallet bicolor usando servicio dinámico
                    val isBicolor = checkIfBicolor(it)
                    it.isBicolor = isBicolor

                    if (isBicolor) {
                        Log.d(TAG, "🎨 Pallet bicolor detectado: ${it.numeroPallet} (${it.embalaje})")
                        _showBicolorFields.value = true
                    } else {
                        _showBicolorFields.value = false
                    }

                    // SOLO mostrar dialog de edición, NO agregar a lista
                    showPalletEditDialog(it)
                }
            }
        }
        // Monitor mensajes informativos de pallets PC
        viewModelScope.launch {
            palletInfoMessage.collect { message ->
                message?.let {
                    _infoDialogMessage.value = it
                    _showInfoDialog.value = true
                }
            }
        }
        // NUEVO: Observar lista sincronizada del escritorio
        viewModelScope.launch {
            scannedPallets.collect { pallets ->
                Log.d(TAG, "📋 Lista sincronizada actualizada - Count: ${pallets.size}")

                // NUEVO: Contar pallets bicolor para estadísticas usando detección dinámica
                val bicolorCount = pallets.count { pallet ->
                    bicolorPackagingService.isBicolorPackaging(pallet.embalaje)
                }
                if (bicolorCount > 0) {
                    Log.d(TAG, "🎨 Pallets bicolor en lista: $bicolorCount")
                }
            }
        }

        // NUEVO: Observar mensajes de finalización de viaje para sincronización automática
        viewModelScope.launch {
            successMessage.collect { message ->
                message?.let {
                    Log.d(TAG, "✅ Mensaje del sistema recibido: $it")

                    // Detectar si es un mensaje de viaje finalizado
                    if (it.contains("Viaje finalizado", ignoreCase = true)) {
                        Log.d(TAG, "🏁 Detectado mensaje de viaje finalizado - Iniciando sincronización automática")

                        // Solicitar automáticamente el nuevo viaje activo después de un breve delay
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1500) // Esperar 1.5 segundos
                            Log.d(TAG, "🔄 Solicitando datos del nuevo viaje activo automáticamente...")
                            signalRService.requestActiveTrip()
                        }
                    }
                }
            }
        }
    }

    // NUEVO: Función para detectar si un pallet es bicolor usando el servicio dinámico
    private fun checkIfBicolor(pallet: Pallet): Boolean {
        val isBicolor = bicolorPackagingService.isBicolorPackaging(pallet.embalaje)
        Log.d(TAG, "🔍 Verificando pallet ${pallet.numeroPallet} con embalaje '${pallet.embalaje}': ${if (isBicolor) "ES BICOLOR" else "NO es bicolor"}")
        return isBicolor
    }

    fun connectToServer(serverConfig: ServerConfiguration) {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Iniciando conexión al servidor: ${serverConfig.getFullUrl()}")
            val connected = signalRService.connect(serverConfig)
            Log.d(TAG, if (connected) "✅ Conexión exitosa" else "❌ Conexión falló")

            // NUEVO: Solicitar tipos bicolor después de conectar
            if (connected) {
                signalRService.requestBicolorPackagingTypes()
            }
        }
    }

    fun scanPallet(palletNumber: String) {
        viewModelScope.launch {
            signalRService.clearStates()
            Log.d(TAG, "📱 Enviando pallet escaneado: $palletNumber")
            val sent = signalRService.sendPalletNumber(palletNumber)
            if (!sent) {
                Log.w(TAG, "⚠️ No se pudo enviar el pallet - Sin conexión")
            }
        }
    }

    fun clearStates() {
        signalRService.clearStates()
        _showBicolorFields.value = false // NUEVO: Limpiar estado bicolor
        Log.d(TAG, "🧹 Estados limpiados")
    }

    // Método para mostrar el dialog de edición cuando se recibe un pallet procesado
    fun showPalletEditDialog(pallet: Pallet) {
        _palletToEdit.value = pallet
        _showEditDialog.value = true

        // NUEVO: Configurar campos bicolor usando detección dinámica
        val isBicolor = bicolorPackagingService.isBicolorPackaging(pallet.embalaje)
        pallet.isBicolor = isBicolor
        _showBicolorFields.value = isBicolor

        Log.d(TAG, "📝 Mostrando dialog de edición para pallet: ${pallet.numeroPallet} (Bicolor: $isBicolor)")
    }

    // Método para cerrar el dialog de edición
    fun dismissEditDialog() {
        _showEditDialog.value = false
        _palletToEdit.value = null
        _showBicolorFields.value = false // NUEVO: Ocultar campos bicolor
        Log.d(TAG, "❌ Dialog de edición cerrado")
    }

    // NUEVO: Método para validar datos de pallet bicolor antes de guardar (ACTUALIZADO)
    private fun validateBicolorPallet(pallet: Pallet): Boolean {
        // CAMBIO: Usar detección dinámica en lugar de hardcodeada
        if (!bicolorPackagingService.isBicolorPackaging(pallet.embalaje)) return true // No es bicolor, validación normal

        // Validar que tenga segunda variedad y cajas para ambas variedades
        if (pallet.segundaVariedad.isNullOrBlank()) {
            Log.w(TAG, "⚠️ Pallet bicolor sin segunda variedad")
            return false
        }

        if (pallet.numeroDeCajas <= 0 || pallet.cajasSegundaVariedad <= 0) {
            Log.w(TAG, "⚠️ Pallet bicolor con cantidades de cajas inválidas")
            return false
        }

        return true
    }

    // Método para guardar pallet editado (ACTUALIZADO para bicolor dinámico)
    fun savePalletEdits(editedPallet: Pallet) {
        viewModelScope.launch {
            Log.d(TAG, "💾 Guardando ediciones del pallet: ${editedPallet.numeroPallet}")

            // NUEVO: Validar datos bicolor antes de enviar
            if (!validateBicolorPallet(editedPallet)) {
                Log.e(TAG, "❌ Validación de pallet bicolor falló")
                return@launch
            }

            // CAMBIO: Usar detección dinámica para logging
            if (bicolorPackagingService.isBicolorPackaging(editedPallet.embalaje)) {
                Log.d(TAG, "🎨 Guardando pallet bicolor - Variedad 1: ${editedPallet.variedad} (${editedPallet.numeroDeCajas}), Variedad 2: ${editedPallet.segundaVariedad} (${editedPallet.cajasSegundaVariedad})")
            }

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

    // NUEVO: Método para obtener conteo de pallets bicolor (ACTUALIZADO)
    fun getBicolorPalletsCount(): Int {
        return scannedPallets.value.count { pallet ->
            bicolorPackagingService.isBicolorPackaging(pallet.embalaje)
        }
    }

    // NUEVO: Método para obtener total de cajas considerando pallets bicolor (ACTUALIZADO)
    fun getTotalCajasCount(): Int {
        return scannedPallets.value.sumOf { pallet ->
            if (bicolorPackagingService.isBicolorPackaging(pallet.embalaje)) {
                pallet.numeroDeCajas + pallet.cajasSegundaVariedad
            } else {
                pallet.numeroDeCajas
            }
        }
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

    // NUEVO: Método para obtener estadísticas completas de pallets
    fun getPalletStatistics(): Map<String, Int> {
        val pallets = scannedPallets.value
        val bicolorCount = pallets.count { pallet ->
            bicolorPackagingService.isBicolorPackaging(pallet.embalaje)
        }
        val normalCount = pallets.size - bicolorCount

        return mapOf(
            "total" to pallets.size,
            "bicolor" to bicolorCount,
            "normal" to normalCount,
            "totalCajas" to getTotalCajasCount()
        )
    }

    // NUEVO: Método para verificar si hay tipos bicolor cargados
    fun hasBicolorTypesLoaded(): Boolean {
        return bicolorPackagingService.getCurrentBicolorTypes().isNotEmpty()
    }

    // NUEVO: Método para obtener tipos bicolor actuales (para debugging)
    fun getCurrentBicolorTypes(): List<String> {
        return bicolorPackagingService.getCurrentBicolorTypes()
    }

    // NUEVO: Método para refrescar tipos bicolor manualmente
    fun refreshBicolorTypes() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Refrescando tipos de embalaje bicolor manualmente...")
            signalRService.requestBicolorPackagingTypes()
        }
    }
    fun dismissInfoDialog() {
        _showInfoDialog.value = false
        _infoDialogMessage.value = null
    }
    // Cleanup al destruir el ViewModel
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 MainViewModel destruido - Limpiando recursos")
    }
}