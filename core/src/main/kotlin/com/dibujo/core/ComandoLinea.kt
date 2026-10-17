package com.dibujo.core

/**
 * Línea de comando tokenizada para la consola.
 *
 * Formatos admitidos: `d`, `3d` (mover 3 pasos), `d 3`, `4` (color directo).
 */
class ComandoLinea(texto: String) {
    val esVacia: Boolean
    val letra: String
    val numero: Int?

    init {
        val tokens = texto.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            esVacia = true
            letra = ""
            numero = null
        } else {
            esVacia = false
            val primera = tokens[0]
            val conteoPegado = Regex("^(\\d+)(.)$").find(primera)
            when {
                conteoPegado != null -> {
                    letra = conteoPegado.groupValues[2]
                    numero = conteoPegado.groupValues[1].toInt()
                }
                tokens.size >= 2 && tokens[1].toIntOrNull() != null -> {
                    letra = primera.first().toString()
                    numero = tokens[1].toIntOrNull()
                }
                else -> {
                    letra = primera.first().toString()
                    numero = null
                }
            }
        }
    }
}
