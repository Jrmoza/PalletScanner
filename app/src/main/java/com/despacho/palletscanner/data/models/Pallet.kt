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

    // CAMPO UNIFICADO PARA DETECCIÓN BICOLOR DINÁMICA
    @SerializedName("esBicolor")
    var isBicolor: Boolean = false,

    @SerializedName("segundaVariedad")
    val segundaVariedad: String? = null,

    @SerializedName("cajasSegundaVariedad")
    val cajasSegundaVariedad: Int = 0
) {
    // Propiedades calculadas usando la propiedad unificada
    val varietyDisplay: String
        get() = if (isBicolor && !segundaVariedad.isNullOrEmpty()) {
            "$variedad + $segundaVariedad"
        } else {
            variedad
        }

    val totalCajasDisplay: String
        get() = if (isBicolor) {
            "Total: ${numeroDeCajas + cajasSegundaVariedad} (${numeroDeCajas}+${cajasSegundaVariedad})"
        } else {
            numeroDeCajas.toString()
        }

    val tipoDisplay: String
        get() = if (isBicolor) "BICOLOR" else "NORMAL"
}
