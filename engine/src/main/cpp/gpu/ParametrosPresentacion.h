#pragma once

#include <cstddef>
#include <cstdint>

namespace dibujo {
namespace gpu {

// Parámetros que recibe el shader de presentación en cada cuadro, con el
// mismo diseño que el bloque `push_constant` de Vulkan (std430) y que los
// `uniform` de OpenGL. Presentar en GPU es, literalmente, dibujar un
// triángulo que cubre la pantalla y, por fragmento, resolver
// `hoja = (pantalla + pan) / zoom` y leer el texel correspondiente.
//
// El orden respeta la alineación std430 (vec4 a 16, vec2 a 8): si se
// reordena, hay que reordenar también el bloque GLSL.
struct ParametrosPresentacion {
    float fuera[4];     // color RGBA (0..1) fuera de la hoja
    float pantalla[2];  // tamaño del backbuffer en px
    float hojaTam[2];   // tamaño de la hoja en px
    float pan[2];       // pan de la cámara en px de hoja
    float zoom;         // zoom de la cámara
    float flipY;        // 1.0 OpenGL (origen abajo), 0.0 Vulkan (origen arriba)
};

static_assert(sizeof(ParametrosPresentacion) == 48,
              "el bloque push_constant de Vulkan mide 48 bytes");

} // namespace gpu
} // namespace dibujo
