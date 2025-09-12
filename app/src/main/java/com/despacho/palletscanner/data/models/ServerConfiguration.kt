package com.despacho.palletscanner.data.models

data class ServerConfiguration(
    val serverUrl: String = "",           // IP del servidor (ej: 192.168.0.103)
    val port: String = "7164",           // Puerto del servidor
    val hubPath: String = "/pallethub",  // Ruta del SignalR Hub
    val connectionTimeout: Long = 10000L // Tiempo límite de conexión
) {
    // Función que construye la URL completa
    fun getFullUrl(): String = "http://$serverUrl:$port$hubPath"

    // Función que verifica si la configuración es válida
    fun isValid(): Boolean = serverUrl.isNotEmpty() && port.isNotEmpty()
}