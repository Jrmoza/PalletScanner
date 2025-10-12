package com.despacho.palletscanner.data.models

import com.google.gson.annotations.SerializedName

data class Trip(
    @SerializedName("tripId")
    val viajeId: Int = 0,

    @SerializedName("numeroViaje")
    val numeroViaje: String = "",

    @SerializedName("numeroGuia")
    val numeroGuia: String = "",

    @SerializedName("fecha")
    val fecha: String = "",

    @SerializedName("responsable")
    val responsable: String = "",

    @SerializedName("estado")
    val estado: String = ""
) {
    // Función para mostrar información resumida
    fun getDisplayInfo(): String {
        return "Viaje #$numeroViaje - Guía: $numeroGuia - $fecha - $responsable"
    }
}