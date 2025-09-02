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

class SignalRService {
    private var hubConnection: HubConnection? = null
    private val gson = Gson()
    private val deviceId = UUID.randomUUID().toString()

    // Propiedades para reconexión automática
    private var isReconnecting = false
    private var lastServerConfig: ServerConfiguration? = null
    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 5
    private var shouldReconnect = true

    // Estados que la UI puede observar
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _activeTrip = MutableStateFlow<Trip?>(null)
    val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    private val _palletProcessed = MutableStateFlow<Pallet?>(null)
    val palletProcessed: StateFlow<Pallet?> = _palletProcessed.asStateFlow()

    private val _palletError = MutableStateFlow<String?>(null)
    val palletError: StateFlow<String?> = _palletError.asStateFlow()

    // NUEVO: StateFlow para lista sincronizada de pallets del escritorio
    private val _palletListFlow = MutableStateFlow<List<Pallet>>(emptyList())
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
    private suspend fun requestActiveTrip() {
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
}