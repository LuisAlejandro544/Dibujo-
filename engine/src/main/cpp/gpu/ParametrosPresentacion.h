#pragma once

#include <cstddef>
#include <cstdint>

namespace dibujo {
namespace gpu {

// Parámetros que recibe el shader de presentación en cada cuadro, con el
// mismo diseño que el bloque `push_constant` de Vulkan (std430) y que los
// `uniform` de OpenGL. Presentar en GPU es, literalmente, dibujar un
// triángulo que cubre la pantalla y, por fragmento, resolver
// `hoja = R(−rotación) · (pantalla + pan) / zoom` y leer el texel.
//
// El orden respeta la alineación std430 (vec4 a 16, vec2 a 8): si se
// reordena, hay que reordenar también el bloque GLSL. `rot` va seguido de
// relleno porque, como hay un vec4, el struct redondea a múltiplo de 16.
struct ParametrosPresentacion {
    float fuera[4];     // color RGBA (0..1) fuera de la hoja
    float pantalla[2];  // tamaño del backbuffer en px
    float hojaTam[2];   // tamaño de la hoja en px
    float pan[2];       // pan de la cámara en px de hoja
    float zoom;         // zoom de la cámara
    float flipY;        // 1.0 OpenGL (origen abajo), 0.0 Vulkan (origen arriba)
    float rot;          // rotación de la cámara en radianes
    float relleno[3];   // relleno std430 hasta múltiplo de 16
};

static_assert(sizeof(ParametrosPresentacion) == 64,
              "el bloque push_constant de Vulkan mide 64 bytes");

} // namespace gpu
} // namespace dibujo
