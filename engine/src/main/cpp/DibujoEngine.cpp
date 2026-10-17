#include "DibujoEngine.h"

#include <cmath>
#include <algorithm>

namespace dibujo {

namespace {

// Grosor base (px de hoja) según la herramienta.
inline int grosorHerramienta(Tool tool, int grosor) {
    switch (tool) {
        case Tool::PORTAMINA: return std::max(1, grosor / 2);
        case Tool::CARBONCILLO: return grosor + 2;
        case Tool::BORRADOR: return grosor + 4;
        case Tool::LAPIZ: default: return grosor;
    }
}

// Pinta un disco centrado en (cx, cy) con radio r.
// Métrica de cuadrícula (como en el respaldo Kotlin): x, y son índices.
void pintarDisco(std::vector<uint32_t>& hoja, int ancho, int alto,
                 float cx, float cy, float radio, uint32_t color) {
    const int x0 = static_cast<int>(std::floor(cx - radio));
    const int x1 = static_cast<int>(std::ceil(cx + radio));
    const int y0 = static_cast<int>(std::floor(cy - radio));
    const int y1 = static_cast<int>(std::ceil(cy + radio));
    const float r2 = radio * radio;
    for (int y = y0; y <= y1; ++y) {
        if (y < 0 || y >= alto) continue;
        for (int x = x0; x <= x1; ++x) {
            if (x < 0 || x >= ancho) continue;
            const float dx = static_cast<float>(x) - cx;
            const float dy = static_cast<float>(y) - cy;
            if (dx * dx + dy * dy <= r2) hoja[y * ancho + x] = color;
        }
    }
}

// Línea gruesa: Bresenham entre dos puntos con disco en cada paso.
void lineaGruesa(std::vector<uint32_t>& hoja, int ancho, int alto,
                 float x0, float y0, float x1, float y1,
                 float radio, bool redonda, uint32_t color) {
    const int pasos = std::max(1, static_cast<int>(std::ceil(
        std::max(std::abs(x1 - x0), std::abs(y1 - y0)))));
    for (int i = 0; i <= pasos; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(pasos);
        const float x = x0 + (x1 - x0) * t;
        const float y = y0 + (y1 - y0) * t;
        if (redonda) {
            pintarDisco(hoja, ancho, alto, x, y, radio, color);
        } else {
            // Punta cuadrada: bloque radio x radio.
            const int px = static_cast<int>(std::lround(x));
            const int py = static_cast<int>(std::lround(y));
            const int r = static_cast<int>(std::lround(radio));
            for (int oy = -r; oy <= r; ++oy) {
                for (int ox = -r; ox <= r; ++ox) {
                    const int xx = px + ox;
                    const int yy = py + oy;
                    if (xx >= 0 && yy >= 0 && xx < ancho && yy < alto) {
                        hoja[yy * ancho + xx] = color;
                    }
                }
            }
        }
    }
}

} // namespace

DibujoEngine::DibujoEngine(int ancho, int alto, uint32_t fondo)
    : ancho_(ancho), alto_(alto), fondo_(fondo),
      hoja_(static_cast<std::size_t>(ancho) * static_cast<std::size_t>(alto), fondo) {}

void DibujoEngine::iniciarTrazo(float x, float y) {
    cancelarTrazo();
    trazoActivo_ = true;
    trazo_.push_back(x);
    trazo_.push_back(y);
}

void DibujoEngine::continuarTrazo(float x, float y) {
    if (!trazoActivo_) return;
    // Decimación: solo acepta puntos separados al menos 1 px de hoja.
    const std::size_t n = trazo_.size();
    if (n >= 2) {
        const float dx = x - trazo_[n - 2];
        const float dy = y - trazo_[n - 1];
        if (dx * dx + dy * dy < 1.0f) return;
    }
    trazo_.push_back(x);
    trazo_.push_back(y);
}

bool DibujoEngine::terminarTrazo() {
    if (!trazoActivo_ || trazo_.empty()) {
        cancelarTrazo();
        return false;
    }
    // Snapshot para undo antes de aplicar el trazo.
    pasado_.push_back(hoja_);
    if (pasado_.size() > kMaxHistorial) pasado_.erase(pasado_.begin());
    futuro_.clear();

    const bool borrar = herramienta_ == Tool::BORRADOR;
    const uint32_t color = borrar ? fondo_ : color_;
    const int grosor = grosorHerramienta(herramienta_, grosor_);
    const float radio = static_cast<float>(grosor) * 0.5f;

    if (trazo_.size() == 2) {
        // Punto aislado.
        pintarDisco(hoja_, ancho_, alto_, trazo_[0], trazo_[1], radio, color);
    } else {
        for (std::size_t i = 0; i + 3 < trazo_.size(); i += 2) {
            lineaGruesa(hoja_, ancho_, alto_, trazo_[i], trazo_[i + 1],
                        trazo_[i + 2], trazo_[i + 3], radio, puntaRedonda_, color);
        }
    }

    trazo_.clear();
    trazoActivo_ = false;
    return true;
}

void DibujoEngine::cancelarTrazo() {
    trazo_.clear();
    trazoActivo_ = false;
}

bool DibujoEngine::deshacer() {
    if (pasado_.empty()) return false;
    futuro_.push_back(hoja_);
    hoja_ = pasado_.back();
    pasado_.pop_back();
    return true;
}

bool DibujoEngine::rehacer() {
    if (futuro_.empty()) return false;
    pasado_.push_back(hoja_);
    hoja_ = futuro_.back();
    futuro_.pop_back();
    return true;
}

void DibujoEngine::limpiarHoja() {
    if (futuro_.empty()) {
        // Limpieza desde el estado actual: es deshacible.
    }
    pasado_.push_back(hoja_);
    if (pasado_.size() > kMaxHistorial) pasado_.erase(pasado_.begin());
    futuro_.clear();
    std::fill(hoja_.begin(), hoja_.end(), fondo_);
}

void DibujoEngine::pan(float dx, float dy) {
    panX_ += dx;
    panY_ += dy;
    // Limita el pan para no perder la hoja del todo.
    const float maxX = static_cast<float>(ancho_) * zoom_;
    const float maxY = static_cast<float>(alto_) * zoom_;
    if (panX_ > maxX) panX_ = maxX;
    if (panY_ > maxY) panY_ = maxY;
    if (panX_ < -maxX) panX_ = -maxX;
    if (panY_ < -maxY) panY_ = -maxY;
}

void DibujoEngine::zoom(float factor) {
    if (factor <= 0.0f) return;
    zoom_ = std::min(8.0f, std::max(0.25f, zoom_ * factor));
}

void DibujoEngine::centrarVista() {
    panX_ = 0.0f;
    panY_ = 0.0f;
    zoom_ = 1.0f;
}

void DibujoEngine::renderizar(uint32_t* destino, int vw, int vh) const {
    if (destino == nullptr || vw <= 0 || vh <= 0) return;
    for (int y = 0; y < vh; ++y) {
        for (int x = 0; x < vw; ++x) {
            // Coordenada de hoja que corresponde al píxel de pantalla.
            const float hx = (static_cast<float>(x) + panX_) / zoom_;
            const float hy = (static_cast<float>(y) + panY_) / zoom_;
            const int ix = static_cast<int>(std::floor(hx));
            const int iy = static_cast<int>(std::floor(hy));
            destino[y * vw + x] = pixelHoja(ix, iy);
        }
    }
}

} // namespace dibujo
