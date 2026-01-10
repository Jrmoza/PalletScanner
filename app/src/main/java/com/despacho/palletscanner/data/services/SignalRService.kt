package com.despacho.palletscanner.data.services

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.despacho.palletscanner.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Timer
import java.util.TimerTask
import kotlin.random.Random
import kotlinx.coroutines.withTimeout
import com.google.gson.GsonBuilder

class SignalRService {
    private var hubConnection: HubConnection? = null
    private val gson = GsonBuilder().create()
    private val deviceId = UUID.randomUUID().toString()

    // Propiedades para reconexión automática
    private var isReconnecting = false
    private var lastServerConfig: ServerConfiguration? = null
    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 5
    private var shouldReconnect = true


    private var healthCheckTimer: Timer? = null
    private var reconnectionTimer: Timer? = null
    private var lastSuccessfulConnection = 0L
    private val jitterRandom = Random.Default
    // Estados que la UI puede observar
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    // Agregar después de las otras propiedades StateFlow (línea ~35)
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    private val _activeTrip = MutableStateFlow<Trip?>(null)
    val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    private val _variedadesList = MutableStateFlow<List<String>>(emptyList())
    val variedadesList: StateFlow<List<String>> = _variedadesList.asStateFlow()

    private val _palletProcessed = MutableStateFlow<Pallet?>(null)
    val palletProcessed: StateFlow<Pallet?> = _palletProcessed.asStateFlow()

    private val _palletError = MutableStateFlow<String?>(null)
    val palletError: StateFlow<String?> = _palletError.asStateFlow()
    private val _palletInfoMessage = MutableStateFlow<String?>(null)

    val palletInfoMessage: StateFlow<String?> = _palletInfoMessage.asStateFlow()

    // Después de línea ~35 (después de _palletInfoMessage)
    private val _palletInfoResponse = MutableStateFlow<PalletInfoResponse?>(null)
    val palletInfoResponse: StateFlow<PalletInfoResponse?> = _palletInfoResponse.asStateFlow()

    private val _palletDeletionResult = MutableStateFlow<DeletionResult?>(null)
    val palletDeletionResult: StateFlow<DeletionResult?> = _palletDeletionResult.asStateFlow()


    // NUEVO: StateFlow para lista sincronizada de pallets del escritorio
    private val _palletListFlow = MutableStateFlow<List<Pallet>>(emptyList())

    private var onBicolorPackagingTypesReceived: ((List<String>) -> Unit)? = null
    val palletListFlow: StateFlow<List<Pallet>> = _palletListFlow.asStateFlow()

    companion object {
        private const val TAG = "SignalRService"
    }


    // Función principal para conectarse al servidor
    suspend fun connect(serverConfig: ServerConfiguration): Boolean = withContext(Dispatchers.IO) {
        try {
            lastServerConfig = serverConfig
            shouldReconnect = true

            val serverUrl = serverConfig.getFullUrl()
            Log.d(TAG, "🔄 Conectando a $serverUrl")

            hubConnection = HubConnectionBuilder.create(serverUrl).build()
            setupEventHandlers()

            hubConnection?.start()
            val connected = waitForConnection(serverConfig.connectionTimeout)

            withContext(Dispatchers.Main) {
                _connectionState.value = connected
                Log.d(TAG, "🔍 Estado UI actualizado: $connected")
            }

            if (connected) {
                reconnectionAttempts = 0
                requestActiveTrip()
                startConnectionMonitoring()
                startHealthCheckTimer()
            }

            connected
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error conectando: ${e.message}")
            withContext(Dispatchers.Main) {
                _connectionState.value = false
            }

            if (!isReconnecting && shouldReconnect) {
                attemptReconnection()
            }
            false
        }
    }

    // Configurar los manejadores de eventos
    private fun setupEventHandlers() {
        Log.d(TAG, "🔧 Configurando event handlers...")

        hubConnection?.apply {
            // Cuando llega información del viaje activo
            on("ActiveTripChanged", { tripId: String, tripData: Any ->
                Log.d(TAG, "🔄 Viaje activo recibido: TripId=$tripId")

                try {
                    val tripDataJson = gson.toJson(tripData)
                    val trip = gson.fromJson(tripDataJson, Trip::class.java)

                    GlobalScope.launch(Dispatchers.Main) {
                        _activeTrip.value = trip
                        Log.d(TAG, "✅ Viaje activo actualizado en UI: #${trip.numeroViaje}")
                    }

                    GlobalScope.launch {
                        joinTripGroup(tripId)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error procesando viaje: ${e.message}")
                }
            }, String::class.java, Any::class.java)
            // AGREGAR en setupEventHandlers() del SignalRService.kt:
            on("ActiveTripWithPallets", { tripData: Any, palletsData: Any ->
                Log.d(TAG, "🔄 Viaje activo con pallets recibido")

                try {
                    val tripDataJson = gson.toJson(tripData)
                    val trip = gson.fromJson(tripDataJson, Trip::class.java)

                    val palletsList = parsePalletListFromJson(palletsData)

                    GlobalScope.launch(Dispatchers.Main) {
                        _activeTrip.value = trip
                        _palletListFlow.value = palletsList
                        Log.d(TAG, "✅ Viaje activo y lista sincronizada - Trip: #${trip.numeroViaje}, Pallets: ${palletsList.size}")
                    }

                    GlobalScope.launch {
                        joinTripGroup(trip.viajeId.toString())
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error procesando viaje activo con pallets: ${e.message}")
                }
            }, Any::class.java, Any::class.java)
            // SOLO para escaneos NUEVOS - muestra ventana emergente
            on("PalletProcessed", { tripId: String, palletData: Any, deviceId: String ->
                if (deviceId == this@SignalRService.deviceId) {
                    Log.d(TAG, "📦 Pallet procesado recibido para mostrar ventana")

                    try {
                        val palletDataJson = gson.toJson(palletData)
                        val pallet = gson.fromJson(palletDataJson, Pallet::class.java)

                        GlobalScope.launch(Dispatchers.Main) {
                            _palletProcessed.value = pallet
                            Log.d(TAG, "✅ Ventana emergente activada para: ${pallet.numeroPallet}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parseando pallet procesado: ${e.message}")
                    }
                }
            }, String::class.java, Any::class.java, String::class.java)

            on("VariedadesListReceived", { variedadesData: Any ->
                Log.d(TAG, "📋 Lista de variedades recibida desde escritorio")

                try {
                    val variedadesList = parseVariedadesListFromJson(variedadesData)
                    GlobalScope.launch(Dispatchers.Main) {
                        _variedadesList.value = variedadesList
                        Log.d(TAG, "✅ Lista de variedades actualizada - Count: ${variedadesList.size}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parseando lista de variedades: ${e.message}")
                }
            }, Any::class.java)

            // NUEVO: Para sincronización de lista completa - NO muestra ventana
            on("PalletListUpdated", { palletsData: Any ->
                Log.d(TAG, "📋 Lista de pallets actualizada desde escritorio")

                try {
                    val palletsList = parsePalletListFromJson(palletsData)
                    GlobalScope.launch(Dispatchers.Main) {
                        _palletListFlow.value = palletsList
                        Log.d(TAG, "✅ Lista sincronizada - Count: ${palletsList.size}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parseando lista de pallets: ${e.message}")
                }
            }, Any::class.java)

            // Cuando hay un error
            on("PalletError", { errorMessage: String, deviceId: String ->
                if (deviceId == this@SignalRService.deviceId) {
                    Log.w(TAG, "❌ Error recibido: $errorMessage")
                    GlobalScope.launch(Dispatchers.Main) {
                        _palletError.value = errorMessage
                    }
                }
            }, String::class.java, String::class.java)
// NUEVO: Listener para mensajes informativos de pallets PC
            on("PalletInfo", { tripId: String, infoMessage: String, deviceId: String ->
                if (deviceId == this@SignalRService.deviceId) {
                    Log.d(TAG, "ℹ️ Mensaje informativo recibido: $infoMessage")
                    GlobalScope.launch(Dispatchers.Main) {
                        _palletInfoMessage.value = infoMessage
                    }
                }
            }, String::class.java, String::class.java, String::class.java)


            on("PalletOperationSuccess", { tripId: String, message: String, deviceId: String ->
                if (deviceId == this@SignalRService.deviceId) {
                    Log.d(TAG, "✅ Operación exitosa recibida: $message")
                    GlobalScope.launch(Dispatchers.Main) {
                        _successMessage.value = message
                    }
                }
            }, String::class.java, String::class.java, String::class.java)

            // NUEVO: Manejo del evento TripFinalized para sincronización automática
            on("TripFinalized", { tripId ->
                Log.d(TAG, "🏁 Viaje finalizado recibido: $tripId")

                // Limpiar inmediatamente la lista de pallets
                _palletListFlow.value = emptyList()
                Log.d(TAG, "🧹 Lista de pallets limpiada automáticamente")

                // Mostrar mensaje al usuario (opcional)
                GlobalScope.launch(Dispatchers.Main) {
                    _successMessage.value = "Viaje finalizado. Actualizando datos..."
                }

                // Solicitar automáticamente el nuevo viaje activo después de 1 segundo
                GlobalScope.launch(Dispatchers.IO) {
                    delay(1000)
                    try {
                        requestActiveTrip()
                        Log.d(TAG, "🔄 Solicitando nuevo viaje activo automáticamente")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error solicitando viaje activo tras finalización: ${e.message}")
                    }
                }
            }, String::class.java)
// NUEVO: Listener para recibir tipos de embalaje bicolor
            on("BicolorPackagingTypesReceived", { packagingTypesList: Any ->
                Log.d(TAG, "📋 Lista de tipos bicolor recibida desde escritorio")

                try {
                    val typesList = parseBicolorTypesFromJson(packagingTypesList)
                    onBicolorPackagingTypesReceived?.invoke(typesList)
                    Log.d(TAG, "✅ Tipos bicolor procesados: $typesList")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parseando tipos bicolor: ${e.message}")
                }
            }, Any::class.java)
            onClosed { exception ->
                Log.w(TAG, "🔌 Conexión cerrada: ${exception?.message}")
                GlobalScope.launch(Dispatchers.Main) {
                    _connectionState.value = false
                    _activeTrip.value = null
                }

                if (!isReconnecting && shouldReconnect) {
                    Log.d(TAG, "🔄 Iniciando proceso de reconexión automática...")
                    attemptReconnection()
                }
                // Después del handler "BicolorPackagingTypesReceived" (línea ~200)
                on("OnPalletInfoReceived", { palletDataJson: String, deviceId: String, success: Boolean, errorMessage: String ->
                    if (deviceId == this@SignalRService.deviceId) {
                        Log.d(TAG, "📦 Información de pallet recibida")

                        GlobalScope.launch(Dispatchers.Main) {
                            if (success && palletDataJson.isNotEmpty()) {
                                try {
                                    val response = gson.fromJson(palletDataJson, PalletInfoResponse::class.java)
                                    _palletInfoResponse.value = response
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error parseando respuesta: ${e.message}")
                                    _palletInfoResponse.value = null
                                }
                            } else {
                                _palletInfoResponse.value = null
                            }
                        }
                    }
                }, String::class.java, String::class.java, Boolean::class.java, String::class.java)

                on("OnPalletDeletionResult", { palletNumber: String, deviceId: String, success: Boolean, message: String ->
                    if (deviceId == this@SignalRService.deviceId) {
                        Log.d(TAG, "🗑️ Resultado de eliminación recibido")

                        GlobalScope.launch(Dispatchers.Main) {
                            _palletDeletionResult.value = DeletionResult(palletNumber, success, message)
                        }
                    }
                }, String::class.java, String::class.java, Boolean::class.java, String::class.java)
            }

        }
    }



    // NUEVO: Método helper para parsear lista de pallets
    private fun parsePalletListFromJson(palletsData: Any): List<Pallet> {
        return try {
            val json = gson.toJson(palletsData)
            val listType = object : TypeToken<List<Pallet>>() {}.type
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando lista de pallets: ${e.message}")
            emptyList()
        }
    }

    // Monitoreo continuo de la conexión
    private fun startConnectionMonitoring() {
        GlobalScope.launch(Dispatchers.IO) {
            while (shouldReconnect) {
                delay(5000)

                val currentState = hubConnection?.connectionState
                if (currentState == HubConnectionState.DISCONNECTED && !isReconnecting) {
                    Log.w(TAG, "🔌 Conexión perdida detectada, iniciando reconexión...")

                    withContext(Dispatchers.Main) {
                        _connectionState.value = false
                        _activeTrip.value = null
                    }

                    attemptReconnection()
                }
            }
        }
    }

    // Método para intentar reconexión automática
    private fun attemptReconnection() {
        if (isReconnecting || !shouldReconnect) return

        isReconnecting = true
        GlobalScope.launch(Dispatchers.IO) {
            Log.d(TAG, "🔄 Iniciando intentos de reconexión...")

            while (reconnectionAttempts < maxReconnectionAttempts && shouldReconnect) {
                reconnectionAttempts++
                val delayTime = 2000L * reconnectionAttempts

                Log.d(TAG, "🔄 Intento de reconexión $reconnectionAttempts/$maxReconnectionAttempts en ${delayTime}ms")

                try {
                    delay(delayTime)

                    lastServerConfig?.let { config ->
                        val reconnected = connectInternal(config)
                        if (reconnected) {
                            Log.d(TAG, "✅ Reconexión exitosa en intento $reconnectionAttempts")
                            reconnectionAttempts = 0
                            isReconnecting = false
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Fallo en reconexión $reconnectionAttempts: ${e.message}")
                }
            }

            Log.w(TAG, "⚠️ Se agotaron los intentos de reconexión ($maxReconnectionAttempts)")
            isReconnecting = false
        }
    }

    // Método interno de conexión para reconexiones
    private suspend fun connectInternal(serverConfig: ServerConfiguration): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverUrl = serverConfig.getFullUrl()

            hubConnection?.stop()
            hubConnection?.close()

            hubConnection = HubConnectionBuilder.create(serverUrl).build()
            setupEventHandlers()

            hubConnection?.start()
            val connected = waitForConnection(serverConfig.connectionTimeout)

            withContext(Dispatchers.Main) {
                _connectionState.value = connected
            }

            if (connected) {
                requestActiveTrip()
                startHealthCheckTimer()
            }

            connected
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en reconexión interna: ${e.message}")
            false
        }
    }

    // Esperar a que la conexión se establezca
    private suspend fun waitForConnection(timeout: Long): Boolean {
        var elapsedTime = 0L
        val checkInterval = 100L

        while (elapsedTime < timeout) {
            when (hubConnection?.connectionState) {
                HubConnectionState.CONNECTED -> {
                    Log.d(TAG, "✅ Conexión establecida")
                    return true
                }
                HubConnectionState.DISCONNECTED -> {
                    Log.e(TAG, "❌ Conexión falló")
                    return false
                }
                HubConnectionState.CONNECTING -> {
                    delay(checkInterval)
                    elapsedTime += checkInterval
                }
                null -> return false
            }
        }
        return false
    }

    // Solicitar información del viaje activo
     suspend fun requestActiveTrip() {
        try {
            hubConnection?.invoke("RequestActiveTrip", deviceId)
            Log.d(TAG, "✅ Solicitud de viaje activo enviada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando viaje activo: ${e.message}")
        }
    }

    // Unirse al grupo del viaje específico
    private suspend fun joinTripGroup(tripId: String) {
        try {
            hubConnection?.invoke("JoinTripGroup", tripId)
            Log.d(TAG, "✅ Unido al grupo del viaje: $tripId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error uniéndose al grupo: ${e.message}")
        }
    }

    // Enviar número de pallet escaneado al servidor
    suspend fun sendPalletNumber(palletNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("SendPalletNumber", palletNumber, deviceId)
                Log.d(TAG, "✅ Pallet enviado: $palletNumber")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando pallet: ${e.message}")
            return@withContext false
        }
    }

    // Enviar pallet editado al servidor
    suspend fun sendPalletWithEdits(pallet: Pallet): Boolean = withContext(Dispatchers.IO) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("SendPalletWithEdits",
                    pallet.numeroPallet,
                    pallet,
                    deviceId)
                Log.d(TAG, "✅ Pallet editado enviado: ${pallet.numeroPallet}")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando pallet editado: ${e.message}")
            return@withContext false
        }
    }

// NUEVO: Enviar solicitud de eliminación de pallet
    suspend fun deletePalletFromTrip(tripId: String, palletNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("DeletePalletFromMobile", tripId, palletNumber, deviceId)
                Log.d(TAG, "🗑️ Solicitud de eliminación enviada para pallet: $palletNumber")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando solicitud de eliminación: ${e.message}")
            throw e
        }
    }

    // Método para solicitar variedades
    suspend fun requestVariedades(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("SendVariedadesToMobile", deviceId)
                Log.d(TAG, "✅ Solicitud de variedades enviada")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando variedades: ${e.message}")
            return@withContext false
        }
    }

    // Helper para parsear variedades
    private fun parseVariedadesListFromJson(variedadesData: Any): List<String> {
        return try {
            val json = gson.toJson(variedadesData)
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando lista de variedades: ${e.message}")
            emptyList()
        }
    }
    // NUEVO: Helper para parsear tipos bicolor
    private fun parseBicolorTypesFromJson(typesData: Any): List<String> {
        return try {
            val json = gson.toJson(typesData)
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando tipos bicolor: ${e.message}")
            emptyList()
        }
    }
    // NUEVO: Calcular delay con backoff exponencial y jitter
    private fun calculateBackoffDelay(attempt: Int): Long {
        val baseDelay = 1000L // 1 segundo base
        val maxDelay = 30000L // 30 segundos máximo

        // Backoff exponencial: 2^attempt * baseDelay
        val exponentialDelay = (baseDelay * Math.pow(2.0, attempt.toDouble())).toLong()

        // Aplicar límite máximo
        val cappedDelay = minOf(exponentialDelay, maxDelay)

        // Agregar jitter (±25% del delay)
        val jitter = (cappedDelay * 0.25 * (jitterRandom.nextDouble() - 0.5)).toLong()
        val finalDelay = cappedDelay + jitter

        Log.d(TAG, "🕐 Backoff calculado: intento $attempt, delay ${finalDelay}ms")
        return maxOf(finalDelay, 1000L) // Mínimo 1 segundo
    }

    // AGREGAR después del método calculateBackoffDelay() (línea ~593)
    private suspend fun performHealthCheck(): Boolean {
        return try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                // Enviar ping con timeout de 5 segundos
                withContext(Dispatchers.IO) {
                    withTimeout(5000) {
                        hubConnection?.invoke("Ping")
                    }
                }
                Log.d(TAG, "🏥 Health check exitoso")
                true
            } else {
                Log.w(TAG, "⚠️ Health check falló - Sin conexión")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Health check falló: ${e.message}")
            false
        }
    }

    // AGREGAR después del método performHealthCheck()
    private fun startHealthCheckTimer() {
        healthCheckTimer?.cancel()
        healthCheckTimer = Timer()
        healthCheckTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                GlobalScope.launch {
                    if (!performHealthCheck()) {
                        Log.w(TAG, "🏥 Health check falló - iniciando reconexión")
                        reconnectWithBackoff()
                    }
                }
            }
        }, 30000, 30000) // Cada 30 segundos
    }
    // AGREGAR después del método startHealthCheckTimer()
    private suspend fun reconnectWithBackoff() {
        if (isReconnecting || !shouldReconnect) return

        isReconnecting = true

        try {
            while (reconnectionAttempts < maxReconnectionAttempts && shouldReconnect) {
                val delay = calculateBackoffDelay(reconnectionAttempts)
                Log.d(TAG, "🔄 Reconexión robusta intento ${reconnectionAttempts + 1}/${maxReconnectionAttempts} en ${delay}ms")

                delay(delay)

                try {
                    lastServerConfig?.let { config ->
                        val reconnected = connectInternal(config)
                        if (reconnected) {
                            Log.d(TAG, "✅ Reconexión robusta exitosa después de ${reconnectionAttempts + 1} intentos")
                            reconnectionAttempts = 0
                            lastSuccessfulConnection = System.currentTimeMillis()
                            startHealthCheckTimer()
                            return
                        }
                    }
                } catch (ex: Exception) {
                    reconnectionAttempts++
                    Log.w(TAG, "❌ Intento de reconexión ${reconnectionAttempts} falló: ${ex.message}")
                }
            }
        } finally {
            isReconnecting = false
        }
    }

    fun isConnected(): Boolean {
        return hubConnection?.connectionState == HubConnectionState.CONNECTED
    }
    // Métodos públicos para control de reconexión
    suspend fun forceReconnect(): Boolean {
        Log.d(TAG, "🔄 Forzando reconexión...")
        lastServerConfig?.let { config ->
            return connectInternal(config)
        }
        return false
    }

    fun stopReconnection() {
        shouldReconnect = false
        isReconnecting = false
    }

    fun isReconnecting(): Boolean = isReconnecting

    // Limpiar estados temporales
    fun clearStates() {
        _palletProcessed.value = null
        _palletError.value = null
    }
    // Agregar después del método clearStates()
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    // NUEVO: Configurar callback para tipos bicolor
    fun setBicolorPackagingTypesCallback(callback: (List<String>) -> Unit) {
        onBicolorPackagingTypesReceived = callback
        Log.d(TAG, "📋 Callback de tipos bicolor configurado")
    }
    // NUEVO: Solicitar tipos de embalaje bicolor al servidor
    suspend fun requestBicolorPackagingTypes() {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("RequestBicolorPackagingTypes", deviceId)
                Log.d(TAG, "📱 Solicitando tipos de embalaje bicolor al servidor")
            } else {
                Log.w(TAG, "⚠️ No se puede solicitar tipos bicolor - Sin conexión")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando tipos bicolor: ${e.message}")
        }
    }
    // Antes de la última llave de la clase SignalRService

    /// <summary>
/// Solicita información de un pallet al escritorio (Testeador)
/// </summary>
    suspend fun requestPalletInfo(palletNumber: String) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("RequestPalletInfo", palletNumber, deviceId)
                Log.d(TAG, "🔍 Solicitud de info de pallet enviada: $palletNumber")
            } else {
                Log.w(TAG, "⚠️ No se puede solicitar info - Sin conexión")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando info de pallet: ${e.message}")
        }
    }

    /// <summary>
/// Solicita eliminación de un pallet al escritorio (Testeador)
/// </summary>
    suspend fun requestPalletDeletion(palletNumber: String) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                hubConnection?.invoke("RequestPalletDeletion", palletNumber, deviceId)
                Log.d(TAG, "🗑️ Solicitud de eliminación enviada: $palletNumber")
            } else {
                Log.w(TAG, "⚠️ No se puede solicitar eliminación - Sin conexión")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando eliminación: ${e.message}")
        }
    }

    /// <summary>
/// Limpia los estados del Testeador
/// </summary>
    fun clearTesteadorStates() {
        _palletInfoResponse.value = null
        _palletDeletionResult.value = null
    }
}