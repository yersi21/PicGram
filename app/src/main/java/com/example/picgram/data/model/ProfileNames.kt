package com.example.picgram.data.model

/** Shared validation; names need not be unique and are never used as account IDs. */
object ProfileNames {
    const val MAX_LENGTH = 80

    fun validatedName(value: String): String {
        val name = value.trim()
        require(name.length in 2..MAX_LENGTH && name.none { it.isISOControl() }) {
            "Escribe un nombre de 2 a 80 caracteres, sin saltos de línea."
        }
        return name
    }

    fun searchTerm(value: String): String = value.trim().removePrefix("@").trim()
}
