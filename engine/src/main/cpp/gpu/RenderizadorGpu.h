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

// Estado del último cuadro dibujado. Distingue "no pasó nada, reintentá" de
// "el render GPU se perdió": sólo `FALLO` justifies caer al render por CPU.
// Los valores coinciden con los que devuelve `nativoGpuRender` (0/1/2) para
// que el puente JNI los reenvíe sin traducción.
enum class EstadoCuadro {
    PRESENTADO = 0, // el cuadro se presentó en pantalla
    REINTENTAR = 1, // error transitorio: reintentar más tarde, NO es fatal
    FALLO = 2,      // error fatal: el render GPU se perdió
};

// Renderizador GPU de la hoja. Recibe la ventana nativa (del `SurfaceView`)
// y lee la hoja/cámara del [DibujoEngine]; dibuja la hoja como textura
// aplicando la cámara en el shader. Una instancia pertenece a un único hilo
// de render (el que la crea, dibuja y destruye).
class RenderizadorGpu {
public:
    virtual ~RenderizadorGpu() = default;

    // Dibuja un cuadro y lo presenta. Devuelve el estado del cuadro:
    // `PRESENTADO` si se presentó, `REINTENTAR` si hubo un error transitorio
    // (superficie sin tamaño, cadena no lista: reintentar más tarde) y
    // `FALLO` sólo si el render GPU se perdió (el llamador cae a CPU).
    virtual EstadoCuadro render() = 0;

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
