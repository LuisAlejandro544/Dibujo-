#pragma once

#include <cstdint>

#include "../DibujoEngine.h"

// `ANativeWindow` es una estructura opaca del NDK; se reenvía aquí para no
// arrastrar cabeceras de Android a toda la traducción.
struct ANativeWindow;

namespace dibujo {
namespace gpu {

// Backend pedido al renderizador. Coincide con el ordinal del enum
// `com.dibujo.core.BackendRender` en Kotlin (AUTOMATICO, OPENGL, VULKAN).
enum class Backend : int {
    AUTOMATICO = 0,
    OPENGL = 1,
    VULKAN = 2,
};

// Renderizador GPU de la hoja. Recibe la ventana nativa (del `SurfaceView`)
// y lee la hoja/cámara del [DibujoEngine]; dibuja la hoja como textura
// aplicando la cámara en el shader. Una instancia pertenece a un único hilo
// de render (el que la crea, dibuja y destruye).
class RenderizadorGpu {
public:
    virtual ~RenderizadorGpu() = default;

    // Dibuja un cuadro y lo presenta. Devuelve false si el contexto se perdió
    // o la superficie quedó inválida (el llamador debe recrear o caer a CPU).
    virtual bool render() = 0;

    // Notifica un cambio de tamaño del backbuffer (px). Se llama en el hilo de
    // render antes del próximo `render()`.
    virtual void redimensionar(int ancho, int alto) = 0;

    // Descripción legible del renderizador y del equipo (para diagnóstico).
    virtual const char* nombre() const = 0;

    // Crea el renderizador del backend pedido sobre `ventana`, leyendo la hoja
    // y la cámara de `motor`. Devuelve nullptr si el backend no está
    // disponible; el llamador cae al render por CPU.
    static RenderizadorGpu* crear(Backend backend, ANativeWindow* ventana,
                                  DibujoEngine* motor);
};

} // namespace gpu
} // namespace dibujo
