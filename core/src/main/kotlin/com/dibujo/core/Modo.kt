package com.dibujo.core

/** Modos de interacción con la hoja. */
enum class Modo {
    /** Modo edición: el cursor dibuja. */
    EDICION,

    /** Modo libre: las teclas mueven la vista (pan/zoom) sin dibujar. */
    MOVER,
}
