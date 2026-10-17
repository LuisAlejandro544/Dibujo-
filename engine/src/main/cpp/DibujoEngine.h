#pragma once

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <vector>

#include "Types.h"

namespace dibujo {

// Núcleo nativo de la app (C++): hoja, trazos, historial y cámara.
//
// La hoja es un buffer ARGB (0xAARRGGBB) con fondo blanco. Los trazos se
// rasterizan con Bresenham + disco grueso. El historial usa snapshots
// limitados de la hoja. La cámara (pan/zoom) permite el "modo moverse
// libremente" sin alterar el contenido de la hoja.
class DibujoEngine {
public:
    DibujoEngine(int ancho, int alto, uint32_t fondo = argb(255, 255, 255, 255));

    // --- Lápiz ---
    void fijarColor(uint32_t color) { color_ = color; }
    void fijarGrosor(int grosor) { grosor_ = grosor < 1 ? 1 : grosor; }
    void fijarPuntaRedonda(bool redonda) { puntaRedonda_ = redonda; }
    void fijarHerramienta(Tool tool) { herramienta_ = tool; }
    uint32_t color() const { return color_; }
    int grosor() const { return grosor_; }
    bool puntaRedonda() const { return puntaRedonda_; }
    Tool herramienta() const { return herramienta_; }

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

    // --- Cámara (modo moverse libremente) ---
    void pan(float dx, float dy);       // desplaza la vista en px de hoja
    void zoom(float factor);            // >1 acercar, <1 alejar
    void centrarVista();
    float panX() const { return panX_; }
    float panY() const { return panY_; }
    float zoomFactor() const { return zoom_; }

    // --- Render ---
    // Pinta el viewport (vw x vh px) aplicando cámara (zoom + pan) sobre la
    // hoja. `destino` debe tener vw*vh píxeles.
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

    // Lee la cámara de forma consistente con la revisión.
    void leerCamara(float& panX, float& panY, float& zoom) const;

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

    // Cámara.
    float panX_ = 0.0f;
    float panY_ = 0.0f;
    float zoom_ = 1.0f;

    // Historial (snapshots de la hoja completa).
    std::vector<std::vector<uint32_t>> pasado_;
    std::vector<std::vector<uint32_t>> futuro_;
    // Igual que el límite de Historial en Kotlin: hojas grandes por snapshot.
    static constexpr std::size_t kMaxHistorial = 30;

    // Protege `hoja_` frente al hilo de render GPU y cuenta las revisiones.
    mutable std::mutex mutex_;
    uint64_t revision_ = 0;
};

} // namespace dibujo
