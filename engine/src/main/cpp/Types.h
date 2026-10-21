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
    DOBLE = 5,        // doble lápiz: dos trazos paralelos a la vez
    PLUMA = 6,        // pluma vectorial (la dibuja la app; el motor no la usa)
};

// Texturas del trazo. El valor entero viaja por JNI y el orden debe coincidir
// con `com.dibujo.core.Textura` (el C++ replica el mismo patrón determinista).
enum class Textura : int {
    LISA = 0,
    GRANO = 1,
    RUGOSA = 2,
    TRAMA = 3,
    SALPICADA = 4,
};

// Modos de interacción: editar (dibujar) o moverse libremente por la hoja.
enum class Modo : int {
    EDICION = 0,
    MOVER = 1,
};

// Simetría del trazo: el motor dibuja el trazo y sus reflejos respecto del
// centro de la hoja. El valor entero viaja por JNI y el orden debe coincidir
// con `com.dibujo.core.Simetria`.
enum class Simetria : int {
    NINGUNA = 0,
    VERTICAL = 1,
    HORIZONTAL = 2,
    CUADRADA = 3,
};

// Color ARGB (0xAARRGGBB) + utilidades.
inline uint32_t argb(int a, int r, int g, int b) {
    return (static_cast<uint32_t>(a & 0xFF) << 24) |
           (static_cast<uint32_t>(r & 0xFF) << 16) |
           (static_cast<uint32_t>(g & 0xFF) << 8) |
           static_cast<uint32_t>(b & 0xFF);
}

} // namespace dibujo
