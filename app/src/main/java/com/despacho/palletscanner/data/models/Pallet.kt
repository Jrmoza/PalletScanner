package com.despacho.palletscanner.data.models

import com.google.gson.annotations.SerializedName

data class Pallet(
    @SerializedName("numeroPallet")
    val numeroPallet: String = "",

    @SerializedName("variedad")
    val variedad: String = "",

    @SerializedName("calibre")
    val calibre: String = "",

    @SerializedName("embalaje")
    val embalaje: String = "",

    @SerializedName("numeroDeCajas")
    val numeroDeCajas: Int = 0,

    @SerializedName("pesoUnitario")
    val pesoUnitario: Double = 0.0,

    @SerializedName("pesoTotal")
    val pesoTotal: Double = 0.0,

    // NUEVOS CAMPOS PARA PALLETS BICOLOR E50G6CB
    @SerializedName("esBicolor")
    val esBicolor: Boolean = false,

    @SerializedName("segundaVariedad")
    val segundaVariedad: String? = null,

    @SerializedName("cajasSegundaVariedad")
    val cajasSegundaVariedad: Int = 0
) {
    // Propiedades calculadas para compatibilidad con la lógica existente
    val varietyDisplay: String
        get() = if (esBicolor && !segundaVariedad.isNullOrEmpty()) {
            "$variedad + $segundaVariedad"
        } else {
            variedad
        }

    val totalCajasDisplay: String
        get() = if (esBicolor) {
            "Total: ${numeroDeCajas + cajasSegundaVariedad} (${numeroDeCajas}+${cajasSegundaVariedad})"
        } else {
            numeroDeCajas.toString()
        }

    val tipoDisplay: String
        get() = if (esBicolor) "BICOLOR" else "NORMAL"

    // Función para verificar si es embalaje bicolor E50G6CB
    val isE50G6CB: Boolean
        get() = embalaje.equals("E50G6CB", ignoreCase = true)
}