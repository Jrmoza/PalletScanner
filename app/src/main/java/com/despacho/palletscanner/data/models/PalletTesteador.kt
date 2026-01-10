package com.despacho.palletscanner.data.models

import com.google.gson.annotations.SerializedName

data class PalletInfoResponse(
    @SerializedName("Pallet")
    val pallet: PalletTesteadorInfo?,
    @SerializedName("Lotes")
    val lotes: List<LoteInfo>,
    @SerializedName("EstadoValidacion")
    val estadoValidacion: String,
    @SerializedName("Incompleto")
    val incompleto: Boolean = false,
    @SerializedName("Mensaje")
    val mensaje: String? = null,
    @SerializedName("TablasConRegistros")
    val tablasConRegistros: List<String>? = null
)

data class PalletTesteadorInfo(
    @SerializedName("NumeroPallet")
    val numeroPallet: String,
    @SerializedName("NumeroDeCajas")
    val numeroDeCajas: Int,
    @SerializedName("Calibre")
    val calibre: String,
    @SerializedName("Embalaje")
    val embalaje: String,
    @SerializedName("Variedad")
    val variedad: String
)

data class LoteInfo(
    @SerializedName("CodigoCuartel")
    val codigoCuartel: String,
    @SerializedName("CSGPredio")
    val csgPredio: String,
    @SerializedName("NombrePredio")
    val nombrePredio: String,
    @SerializedName("NombreProductor")
    val nombreProductor: String,
    @SerializedName("CalibreLote")
    val calibreLote: String,
    @SerializedName("EmbalajeLote")
    val embalajeLote: String,
    @SerializedName("VariedadLote")
    val variedadLote: String,
    @SerializedName("CantidadCajas")
    val cantidadCajas: Int,
    @SerializedName("EsMinoritario")
    val esMinoritario: Boolean,
    @SerializedName("CalibreMayoritario")
    val calibreMayoritario: String?,
    @SerializedName("EmbalajeMayoritario")
    val embalajeMayoritario: String?,
    @SerializedName("VariedadMayoritaria")
    val variedadMayoritaria: String?
)

data class DeletionResult(
    val palletNumber: String,
    val success: Boolean,
    val message: String
)