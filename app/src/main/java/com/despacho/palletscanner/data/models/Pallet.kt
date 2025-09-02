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
    val pesoTotal: Double = 0.0
)