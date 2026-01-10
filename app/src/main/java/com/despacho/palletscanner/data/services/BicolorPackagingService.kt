package com.despacho.palletscanner.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BicolorPackagingService {
    // Lista privada que almacena los tipos de embalaje bicolor
    private val _bicolorTypes = MutableStateFlow<List<String>>(emptyList())

    // Lista pública de solo lectura para observar los cambios
    val bicolorTypes: StateFlow<List<String>> = _bicolorTypes.asStateFlow()

    /**
     * Actualiza la lista de tipos de embalaje bicolor
     * Este método será llamado cuando recibamos la lista del servidor
     */
    fun updateBicolorTypes(types: List<String>) {
        _bicolorTypes.value = types
        println("📋 Tipos bicolor actualizados: $types")
    }

    /**
     * Verifica si un embalaje específico es de tipo bicolor
     * @param packaging El código del embalaje a verificar
     * @return true si es bicolor, false si no
     */
    fun isBicolorPackaging(packaging: String): Boolean {
        val isBicolor = _bicolorTypes.value.any {
            it.equals(packaging, ignoreCase = true)
        }
        println("🔍 Verificando embalaje '$packaging': ${if (isBicolor) "ES BICOLOR" else "NO es bicolor"}")
        return isBicolor
    }

    /**
     * Obtiene la lista actual de tipos bicolor
     */
    fun getCurrentBicolorTypes(): List<String> {
        return _bicolorTypes.value
    }
}