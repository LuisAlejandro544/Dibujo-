#pragma once

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <vector>

#include "Types.h"

namespace dibujo {

// Instantánea de la hoja para el historial, **comprimida**.
//
// Antes cada paso guardaba una copia completa de la hoja (4 bytes por píxel):
// con una hoja de 1080×2000 son ~8,6 MB por paso y 30 pasos son ~260 MB; con
// una hoja de 4096×4096 son 64 MB por paso (el total pasa de 2 GB y la app se
// queda sin memoria antes de poder deshacer).
//
// Ahora se guarda con RLE: rachas de píxeles iguales, en pares (cantidad,
// color). En una hoja de dibujo —mucho fondo del mismo color— la reducción es
// enorme y el resultado es **exactamente el mismo**: no se pierde ni un píxel.
// Cuando el RLE no conviene (imagen ruidosa) se guarda la copia cruda, así el
// historial nunca ocupa más memoria que antes.
//
// La compresión es sin pérdida: deshacer y rehacer devuelven la hoja tal cual.
// Replica `InstantaneaHoja` de Kotlin (paridad en `MotorTest`).
struct Instantanea {
    // Datos crudos, o RLE (cantidad, color, cantidad, color, ...) si comprime.
    std::vector<uint32_t> datos;
    bool comprimido = false;
    std::size_t pixeles = 0;
};

// Comprime la hoja para guardarla en el historial (sin pérdida).
Instantanea comprimirHoja(const std::vector<uint32_t>& hoja);

// Restaura una instantánea sobre la hoja (sobrescribe lo que haya).
void restaurarHoja(const Instantanea& instantanea, std::vector<uint32_t>& hoja);

// Núcleo nativo de la app (C++): hoja, trazos, historial y cámara.
//
// La hoja es un buffer ARGB (0xAARRGGBB) con fondo blanco. Los trazos se
// rasterizan con Bresenham + disco grueso. El historial usa instantáneas
// comprimidas de la hoja. La cámara (pan/zoom/rotación) permite el "modo
// moverse libremente" sin alterar el contenido de la hoja.
class DibujoEngine {
public:
    DibujoEngine(int ancho, int alto, uint32_t fondo = argb(255, 255, 255, 255));

    // --- Lápiz ---
    void fijarColor(uint32_t color) { color_ = color; }
    void fijarGrosor(int grosor) { grosor_ = grosor < 1 ? 1 : grosor; }
    void fijarPuntaRedonda(bool redonda) { puntaRedonda_ = redonda; }
    void fijarHerramienta(Tool tool) { herramienta_ = tool; }
    void fijarTextura(Textura textura) { textura_ = textura; }
    uint32_t color() const { return color_; }
    int grosor() const { return grosor_; }
    bool puntaRedonda() const { return puntaRedonda_; }
    Tool herramienta() const { return herramienta_; }
    Textura textura() const { return textura_; }

    // --- Configuración del trazo doble (herramienta DOBLE) ---
    // El gesto dibuja dos trazos: el principal (con `herramientaPrincipal_`) y
    // el acompañante o "doble" (con `herramientaDoble_`, `colorDoble_` y
    // `ladoDoble_`). Replica `com.dibujo.core.Lapiz` de Kotlin (paridad).
    void fijarHerramientaPrincipal(Tool tool) { herramientaPrincipal_ = tool; }
    void fijarHerramientaDoble(ToolDoble tool) { herramientaDoble_ = tool; }
    void fijarLadoDoble(LadoDoble lado) { ladoDoble_ = lado; }
    void fijarColorDoble(uint32_t color) { colorDoble_ = color; }

    // Trazo asistido: al terminar un trazo se descarta su recorrido y se
    // dibuja una recta entre el primer y el último punto (PINTAR no cambia).
    void fijarAsistenciaRecta(bool activa) { asistenciaRecta_ = activa; }
    bool asistenciaRecta() const { return asistenciaRecta_; }

    // Simetría del trazo: al terminar, se dibujan también los reflejos del
    // trazo respecto del centro de la hoja. Replica `Simetria` de Kotlin
    // (paridad): el reflejo es una resta, sin trigonometría.
    void fijarSimetria(Simetria simetria) { simetria_ = simetria; }
    Simetria simetria() const { return simetria_; }

    // --- Trazo en curso (modo edición) ---
    void iniciarTrazo(float x, float y);
    void continuarTrazo(float x, float y);
    // Convierte el trazo en curso en píxeles reales de la hoja y lo guarda
    // en el historial. Devuelve false si no había trazo activo.
    bool terminarTrazo();
    void cancelarTrazo();

    // --- Historial ---
    bool deshacer();
    bool rehacer();
    bool puedeDeshacer() const { return !pasado_.empty(); }
    bool puedeRehacer() const { return !futuro_.empty(); }
    void limpiarHoja();  // borra todo (queda en el historial)
    // Tope de pasos configurable (por defecto 30). Al bajarlo se descartan
    // los snapshots más viejos. Replica `Historial.fijarMaximo` en Kotlin.
    void fijarLimiteHistorial(std::size_t maximo);
    std::size_t limiteHistorial() const { return maxHistorial_; }

    // "Limpiar trazos": adelgaza toda la tinta a un esqueleto de 1 px
    // (Zhang-Suen) y la redibuja con ancho uniforme `radio`. Devuelve false si
    // la hoja está vacía. Replica `Lienzo.limpiarTrazos` de Kotlin (paridad).
    bool limpiarTrazos(float radio = 1.0f);

    // --- Tamaño de la hoja ---
    // Expande la hoja agregando píxeles nuevos (rellenos con `fondo_`) en cada
    // lado; el contenido conserva sus coordenadas. Acota a kMaxLado por lado y
    // limpia el historial (los snapshots viejos tienen otras dimensiones).
    void expandir(int izquierda, int arriba, int derecha, int abajo);

    // --- Cámara (modo moverse libremente) ---
    void pan(float dx, float dy);       // desplaza la vista en px de hoja
    void zoom(float factor);            // >1 acercar, <1 alejar
    void rotar(float delta);            // gira la hoja delta radianes
    void centrarVista();
    float panX() const { return panX_; }
    float panY() const { return panY_; }
    float zoomFactor() const { return zoom_; }
    float rotacion() const { return rotacion_; }

    // --- Render ---
    // Pinta el viewport (vw x vh px) aplicando cámara (zoom + rotación + pan)
    // sobre la hoja. `destino` debe tener vw*vh píxeles.
    void renderizar(uint32_t* destino, int vw, int vh) const;

    // Dimensiones de la hoja.
    int ancho() const { return ancho_; }
    int alto() const { return alto_; }
    uint32_t fondo() const { return fondo_; }

    // Color del píxel de la hoja en coordenadas de hoja (sin cámara).
    uint32_t pixelHoja(int x, int y) const {
        std::lock_guard<std::mutex> bloqueo(mutex_);
        if (x < 0 || y < 0 || x >= ancho_ || y >= alto_) return fondo_;
        return hoja_[y * ancho_ + x];
    }

    // --- Render GPU ---
    // La hoja es compartida con el hilo de render GPU (OpenGL/Vulkan). Para no
    // exponer `hoja_` a carreras de datos, el render lee siempre a través de
    // estas operaciones, que toman un bloqueo. Las mutaciones de la hoja
    // (trazo, deshacer, rehacer, limpiar) también lo toman.

    // Revisión de la hoja: sube con cada cambio de contenido. El render GPU la
    // usa para subir la textura solo cuando hace falta.
    uint64_t revision() const;

    // Copia la hoja completa a `destino` (debe tener ancho*alto celdas) bajo
    // bloqueo. Devuelve la revisión copiada, o 0 si el destino no alcanza.
    uint64_t copiarHoja(uint32_t* destino, std::size_t capacidad) const;

    // Reemplaza la hoja completa por `datos` (ancho*alto ARGB) y guarda el
    // estado anterior en el historial (deshacer). `n` debe ser exactamente
    // ancho*alto; si no, no toca nada y devuelve false. La usa el limpiador de
    // trazos con modelo IA (LiteRT) del lado Android. Replica
    // `MotorKotlin.reemplazarHoja` (paridad).
    bool reemplazarHoja(const uint32_t* datos, std::size_t n);

    // Transforma el contenido dentro de la misma hoja: lo voltea
    // (horizontal/vertical), lo desplaza y lo escala alrededor del centro. La
    // zona no cubierta queda con `fondo_`. Guarda el estado anterior en el
    // historial. Devuelve false si no hay nada que transformar. Replica
    // `Lienzo.transformar` (paridad píxel a píxel).
    bool transformar(bool voltearHorizontal, bool voltearVertical,
                     float desplazamientoX, float desplazamientoY, float escala);

    // Lee la cámara de forma consistente con la revisión.
    void leerCamara(float& panX, float& panY, float& zoom, float& rotacion) const;

private:
    void rasterizarTrazo(const std::vector<float>& puntos, uint32_t color,
                         int grosor, bool redonda, bool borrar);

    int ancho_;
    int alto_;
    uint32_t fondo_;
    std::vector<uint32_t> hoja_;

    // Trazo en curso (coordenadas de hoja, ya suavizado/decimado).
    std::vector<float> trazo_;
    bool trazoActivo_ = false;

    // Lápiz actual.
    uint32_t color_;
    int grosor_ = 2;
    bool puntaRedonda_ = true;
    Tool herramienta_ = Tool::LAPIZ;
    Textura textura_ = Textura::LISA;
    bool asistenciaRecta_ = false;
    Simetria simetria_ = Simetria::NINGUNA;

    // Configuración del trazo doble (herramienta DOBLE).
    Tool herramientaPrincipal_ = Tool::LAPIZ;
    ToolDoble herramientaDoble_ = ToolDoble::LAPIZ;
    LadoDoble ladoDoble_ = LadoDoble::IZQUIERDA;
    uint32_t colorDoble_ = 0xFF000000u;

    // Cámara.
    float panX_ = 0.0f;
    float panY_ = 0.0f;
    float zoom_ = 1.0f;
    float rotacion_ = 0.0f;  // radianes

    // Límites de zoom (coinciden con MotorKotlin): acercar mucho como en las
    // apps profesionales sin perder precisión de float.
    static constexpr float kZoomMin = 0.05f;
    static constexpr float kZoomMax = 64.0f;

    // Historial (instantáneas comprimidas de la hoja completa).
    std::vector<Instantanea> pasado_;
    std::vector<Instantanea> futuro_;
    // Igual que el límite de Historial en Kotlin: hojas grandes por snapshot.
    static constexpr std::size_t kMaxHistorialPorDefecto = 30;
    std::size_t maxHistorial_ = kMaxHistorialPorDefecto;

    // Lado máximo de la hoja (coincide con Lienzo.MAX_LADO en Kotlin).
    static constexpr int kMaxLado = 4096;

    // Protege `hoja_` frente al hilo de render GPU y cuenta las revisiones.
    mutable std::mutex mutex_;
    uint64_t revision_ = 0;
};

} // namespace dibujo
