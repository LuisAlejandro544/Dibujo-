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

// Mezcla `color` sobre el píxel (x, y) con cobertura `alpha` (0..255).
// Misma aritmética entera que `Lienzo.pintarMezcla` en Kotlin para no romper
// la paridad píxel a píxel.
inline void pintarMezcla(std::vector<uint32_t>& hoja, int ancho, int alto,
                         int x, int y, uint32_t color, int alpha) {
    if (x < 0 || y < 0 || x >= ancho || y >= alto) return;
    if (alpha >= 255) {
        hoja[static_cast<std::size_t>(y) * ancho + x] = color;
        return;
    }
    if (alpha <= 0) return;
    uint32_t& destino = hoja[static_cast<std::size_t>(y) * ancho + x];
    const uint32_t a = static_cast<uint32_t>(alpha);
    const uint32_t inv = static_cast<uint32_t>(255 - alpha);
    const uint32_t r = ((((color >> 16) & 0xFFu) * a + ((destino >> 16) & 0xFFu) * inv + 127u) / 255u);
    const uint32_t g = ((((color >> 8) & 0xFFu) * a + ((destino >> 8) & 0xFFu) * inv + 127u) / 255u);
    const uint32_t b = (((color & 0xFFu) * a + (destino & 0xFFu) * inv + 127u) / 255u);
    destino = 0xFF000000u | (r << 16) | (g << 8) | b;
}

// Pinta un disco suavizado centrado en (cx, cy) con radio r.
// La cobertura se calcula analíticamente (0.5 + r − distancia) usando double,
// igual que el respaldo Kotlin, para que el antialiasing sea idéntico.
void pintarDisco(std::vector<uint32_t>& hoja, int ancho, int alto,
                 float cx, float cy, float radio, uint32_t color) {
    const float margen = radio + 1.0f;
    const int x0 = static_cast<int>(std::floor(cx - margen));
    const int x1 = static_cast<int>(std::ceil(cx + margen));
    const int y0 = static_cast<int>(std::floor(cy - margen));
    const int y1 = static_cast<int>(std::ceil(cy + margen));
    const double r = static_cast<double>(radio);
    const double cxD = static_cast<double>(cx);
    const double cyD = static_cast<double>(cy);
    for (int y = y0; y <= y1; ++y) {
        if (y < 0 || y >= alto) continue;
        for (int x = x0; x <= x1; ++x) {
            if (x < 0 || x >= ancho) continue;
            const double dx = static_cast<double>(x) - cxD;
            const double dy = static_cast<double>(y) - cyD;
            const double dist = std::sqrt(dx * dx + dy * dy);
            double cobertura = 0.5 + r - dist;
            if (cobertura <= 0.0) continue;
            if (cobertura > 1.0) cobertura = 1.0;
            const int alpha = static_cast<int>(std::lround(cobertura * 255.0));
            pintarMezcla(hoja, ancho, alto, x, y, color, alpha);
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
    // La hoja la lee también el hilo de render GPU: bloquear la mutación.
    std::lock_guard<std::mutex> bloqueo(mutex_);
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
    ++revision_;
    return true;
}

void DibujoEngine::cancelarTrazo() {
    trazo_.clear();
    trazoActivo_ = false;
}

bool DibujoEngine::deshacer() {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    if (pasado_.empty()) return false;
    futuro_.push_back(hoja_);
    hoja_ = pasado_.back();
    pasado_.pop_back();
    ++revision_;
    return true;
}

bool DibujoEngine::rehacer() {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    if (futuro_.empty()) return false;
    pasado_.push_back(hoja_);
    hoja_ = futuro_.back();
    futuro_.pop_back();
    ++revision_;
    return true;
}

void DibujoEngine::limpiarHoja() {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    pasado_.push_back(hoja_);
    if (pasado_.size() > kMaxHistorial) pasado_.erase(pasado_.begin());
    futuro_.clear();
    std::fill(hoja_.begin(), hoja_.end(), fondo_);
    ++revision_;
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
    // Este camino corre en el hilo de la UI (el mismo que muta la hoja), así
    // que no necesita bloqueo; lee `hoja_` directamente para no bloquear por
    // píxel. El hilo de render GPU usa `copiarHoja`, que sí bloquea.
    for (int y = 0; y < vh; ++y) {
        for (int x = 0; x < vw; ++x) {
            // Coordenada de hoja que corresponde al píxel de pantalla.
            const float hx = (static_cast<float>(x) + panX_) / zoom_;
            const float hy = (static_cast<float>(y) + panY_) / zoom_;
            const int ix = static_cast<int>(std::floor(hx));
            const int iy = static_cast<int>(std::floor(hy));
            destino[y * vw + x] =
                (ix >= 0 && iy >= 0 && ix < ancho_ && iy < alto_)
                    ? hoja_[static_cast<std::size_t>(iy) * ancho_ + ix]
                    : fondo_;
        }
    }
}

uint64_t DibujoEngine::revision() const {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    return revision_;
}

uint64_t DibujoEngine::copiarHoja(uint32_t* destino, std::size_t capacidad) const {
    if (destino == nullptr) return 0;
    std::lock_guard<std::mutex> bloqueo(mutex_);
    if (capacidad < hoja_.size()) return 0;
    std::copy(hoja_.begin(), hoja_.end(), destino);
    return revision_;
}

void DibujoEngine::leerCamara(float& panX, float& panY, float& zoom) const {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    panX = panX_;
    panY = panY_;
    zoom = zoom_;
}

} // namespace dibujo
