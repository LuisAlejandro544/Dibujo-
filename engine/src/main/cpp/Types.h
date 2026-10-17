#pragma once

#include <cstdint>

namespace dibujo {

// Lápices disponibles. El valor entero viaja por JNI.
// El orden debe coincidir con `com.dibujo.core.Herramienta`.
enum class Tool : int {
    LAPIZ = 0,        // lápiz clásico
    PORTAMINA = 1,    // trazo fino
    CARBONCILLO = 2,  // trazo grueso y suave
    BORRADOR = 3,     // borra hacia el color de la hoja
    PINTAR = 4,       // balde: rellena la región tocada
};

// Modos de interacción: editar (dibujar) o moverse libremente por la hoja.
enum class Modo : int {
    EDICION = 0,
    MOVER = 1,
};

// Color ARGB (0xAARRGGBB) + utilidades.
inline uint32_t argb(int a, int r, int g, int b) {
    return (static_cast<uint32_t>(a & 0xFF) << 24) |
           (static_cast<uint32_t>(r & 0xFF) << 16) |
           (static_cast<uint32_t>(g & 0xFF) << 8) |
           static_cast<uint32_t>(b & 0xFF);
}

} // namespace dibujo
