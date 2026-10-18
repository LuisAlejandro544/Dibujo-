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
        case Tool::LAPIZ:
        case Tool::PINTAR:
        case Tool::DOBLE:
        default: return grosor;
    }
}

// Separación entre las dos líneas paralelas de la herramienta DOBLE: crece con
// el grosor para que el hueco entre ambas sea visible. La fórmula la replica
// `MotorKotlin` (mantener en paridad).
inline float separacionDoble(int grosor) {
    return static_cast<float>(grosor + 2) * 0.5f;
}

// Color de los píxeles fuera de la hoja en `renderizar` (gris claro). Debe
// coincidir con `MotorKotlin.FUERA` para que el render no dependa del backend.
constexpr uint32_t kFuera = 0xFFECECECu;

// Radio (px) del cierre de huecos del contorno que usa el balde: cierra huecos
// de hasta 2*kRadioCierre px. Coincide con `Lienzo.RADIO_CIERRE` (paridad).
constexpr int kRadioCierre = 3;

// Apila una semilla por cada tramo contiguo atravesable sin marcar en la fila
// `base` (índice del primer píxel de la fila). Si `bloqueado` es nullptr sólo
// se atraviesan los píxeles del color `origen`; si no, se atraviesa todo lo que
// no esté bloqueado. Es la clave del relleno por "spans": apilar un punto por
// tramo en lugar de un punto por píxel.
void agregarTramos(const std::vector<uint32_t>& hoja, const std::vector<uint8_t>& marca,
                   std::vector<int>& pila, int base, int izq, int der,
                   uint32_t origen, const std::vector<uint8_t>* bloqueado) {
    auto libre = [&](std::size_t i) -> bool {
        return bloqueado ? !(*bloqueado)[i] : hoja[i] == origen;
    };
    int x = izq;
    while (x <= der) {
        const std::size_t i = static_cast<std::size_t>(base) + x;
        if (!marca[i] && libre(i)) {
            pila.push_back(base + x);
            while (x <= der && libre(static_cast<std::size_t>(base) + x)) ++x;
        } else {
            ++x;
        }
    }
}

// Cierra los huecos pequeños del contorno: toma como barrera todo píxel que no
// sea de color `origen` y le aplica un cierre morfológico (dilatar y erosionar)
// con un cuadrado de lado 2*kRadioCierre+1. Fuera de la hoja se considera
// barrera. El cuadrado es separable, así que se dilata y erosiona en horizontal
// y en vertical (más rápido y mismo resultado). Replica `Lienzo.cerrarHuecos`.
void cerrarHuecos(const std::vector<uint32_t>& hoja, int ancho, int alto,
                  uint32_t origen, std::vector<uint8_t>& bloqueado) {
    const std::size_t n = hoja.size();
    std::vector<uint8_t> a(n, 0);
    std::vector<uint8_t> b(n, 0);
    // Dilatación horizontal.
    for (int y = 0; y < alto; ++y) {
        const std::size_t base = static_cast<std::size_t>(y) * ancho;
        for (int x = 0; x < ancho; ++x) {
            if (hoja[base + x] == origen) continue;
            const int desde = std::max(0, x - kRadioCierre);
            const int hasta = std::min(ancho - 1, x + kRadioCierre);
            for (int xx = desde; xx <= hasta; ++xx) a[base + xx] = 1;
        }
    }
    // Dilatación vertical.
    for (int y = 0; y < alto; ++y) {
        const std::size_t base = static_cast<std::size_t>(y) * ancho;
        for (int x = 0; x < ancho; ++x) {
            if (!a[base + x]) continue;
            const int desde = std::max(0, y - kRadioCierre);
            const int hasta = std::min(alto - 1, y + kRadioCierre);
            for (int yy = desde; yy <= hasta; ++yy) {
                b[static_cast<std::size_t>(yy) * ancho + x] = 1;
            }
        }
    }
    // Erosión horizontal (fuera de la hoja cuenta como barrera).
    std::fill(a.begin(), a.end(), 0);
    for (int y = 0; y < alto; ++y) {
        const std::size_t base = static_cast<std::size_t>(y) * ancho;
        for (int x = 0; x < ancho; ++x) {
            bool todo = true;
            for (int dx = -kRadioCierre; dx <= kRadioCierre; ++dx) {
                const int xx = x + dx;
                if (xx >= 0 && xx < ancho && !b[base + xx]) {
                    todo = false;
                    break;
                }
            }
            a[base + x] = todo ? 1 : 0;
        }
    }
    // Erosión vertical.
    std::fill(b.begin(), b.end(), 0);
    for (int y = 0; y < alto; ++y) {
        for (int x = 0; x < ancho; ++x) {
            bool todo = true;
            for (int dy = -kRadioCierre; dy <= kRadioCierre; ++dy) {
                const int yy = y + dy;
                if (yy >= 0 && yy < alto &&
                    !a[static_cast<std::size_t>(yy) * ancho + x]) {
                    todo = false;
                    break;
                }
            }
            b[static_cast<std::size_t>(y) * ancho + x] = todo ? 1 : 0;
        }
    }
    bloqueado.swap(b);
}

// Inunda desde `semilla` con el mismo algoritmo de tramos horizontales de
// Kotlin. Devuelve true si la región alcanza el borde de la hoja (fondo
// abierto) y deja en `marca` los píxeles alcanzados.
bool inundar(const std::vector<uint32_t>& hoja, int ancho, int alto,
             int semilla, uint32_t origen, const std::vector<uint8_t>* bloqueado,
             std::vector<uint8_t>& marca) {
    marca.assign(hoja.size(), 0);
    std::vector<int> pila;
    pila.push_back(semilla);
    bool tocaBorde = false;
    auto libre = [&](std::size_t i) -> bool {
        return bloqueado ? !(*bloqueado)[i] : hoja[i] == origen;
    };
    while (!pila.empty()) {
        const int i = pila.back();
        pila.pop_back();
        if (marca[static_cast<std::size_t>(i)] ||
            !libre(static_cast<std::size_t>(i))) {
            continue;
        }
        const int cy = i / ancho;
        const int cx = i % ancho;
        const int base = cy * ancho;
        int izq = cx;
        while (izq > 0 && libre(static_cast<std::size_t>(base) + izq - 1)) --izq;
        int der = cx;
        while (der < ancho - 1 && libre(static_cast<std::size_t>(base) + der + 1)) ++der;
        for (int xx = izq; xx <= der; ++xx) {
            marca[static_cast<std::size_t>(base) + xx] = 1;
        }
        if (izq == 0 || der == ancho - 1 || cy == 0 || cy == alto - 1) tocaBorde = true;
        if (cy > 0) agregarTramos(hoja, marca, pila, base - ancho, izq, der, origen, bloqueado);
        if (cy < alto - 1) agregarTramos(hoja, marca, pila, base + ancho, izq, der, origen, bloqueado);
    }
    return tocaBorde;
}

// Relleno por inundación a partir de (x, y), limitado a la región CERRADA que
// lo contiene. Primero inunda la región exacta del color tocado; si el relleno
// se escapa hacia el fondo por un hueco del contorno, cierra huecos pequeños
// (morfología) y reintenta. Si aun así alcanza el borde de la hoja (fondo
// abierto) no se pinta nada y devuelve false. Replica `Lienzo.rellenar` de
// Kotlin, que usa el mismo algoritmo, para mantener la paridad píxel a píxel.
bool rellenar(std::vector<uint32_t>& hoja, int ancho, int alto,
              int x, int y, uint32_t color) {
    if (x < 0 || y < 0 || x >= ancho || y >= alto) return false;
    const int semilla = y * ancho + x;
    const uint32_t origen = hoja[static_cast<std::size_t>(semilla)];
    if (origen == color) return false;

    std::vector<uint8_t> marca;
    if (!inundar(hoja, ancho, alto, semilla, origen, nullptr, marca)) {
        for (std::size_t i = 0; i < marca.size(); ++i) {
            if (marca[i]) hoja[i] = color;
        }
        return true;
    }

    std::vector<uint8_t> bloqueado;
    cerrarHuecos(hoja, ancho, alto, origen, bloqueado);
    if (inundar(hoja, ancho, alto, semilla, origen, &bloqueado, marca)) return false;
    bool pinto = false;
    for (std::size_t i = 0; i < marca.size(); ++i) {
        if (marca[i] && hoja[i] == origen) {
            hoja[i] = color;
            pinto = true;
        }
    }
    return pinto;
}

// Condiciones comunes del adelgazado de Zhang-Suen: 2 <= B <= 6 y exactamente
// un cruce 0->1 (A == 1). Replica `Lienzo.condicionesZhangSuen` de Kotlin.
inline bool condicionesZhangSuen(int p2, int p3, int p4, int p5,
                                 int p6, int p7, int p8, int p9) {
    const int b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9;
    if (b < 2 || b > 6) return false;
    int a = 0;
    if (!p2 && p3) ++a;
    if (!p3 && p4) ++a;
    if (!p4 && p5) ++a;
    if (!p5 && p6) ++a;
    if (!p6 && p7) ++a;
    if (!p7 && p8) ++a;
    if (!p8 && p9) ++a;
    if (!p9 && p2) ++a;
    return a == 1;
}

// Adelgazado morfológico de Zhang-Suen: deja cada región de tinta reducida a
// una línea de 1 px. Sólo usa enteros 0/1, por eso replica exactamente
// `Lienzo.adelgazar` de Kotlin (paridad píxel a píxel).
void adelgazar(std::vector<uint8_t>& img, int w, int h) {
    if (w < 3 || h < 3) return;
    std::vector<int> borrar;
    bool cambio = true;
    while (cambio) {
        cambio = false;
        // Sub-iteración 0 (borde norte/este) y 1 (borde sur/oeste).
        for (int sub = 0; sub < 2; ++sub) {
            borrar.clear();
            for (int y = 1; y < h - 1; ++y) {
                const int fila = y * w;
                for (int x = 1; x < w - 1; ++x) {
                    const int i = fila + x;
                    if (!img[static_cast<std::size_t>(i)]) continue;
                    const int p2 = img[static_cast<std::size_t>(i - w)];
                    const int p3 = img[static_cast<std::size_t>(i - w + 1)];
                    const int p4 = img[static_cast<std::size_t>(i + 1)];
                    const int p5 = img[static_cast<std::size_t>(i + w + 1)];
                    const int p6 = img[static_cast<std::size_t>(i + w)];
                    const int p7 = img[static_cast<std::size_t>(i + w - 1)];
                    const int p8 = img[static_cast<std::size_t>(i - 1)];
                    const int p9 = img[static_cast<std::size_t>(i - w - 1)];
                    if (!condicionesZhangSuen(p2, p3, p4, p5, p6, p7, p8, p9)) continue;
                    if (sub == 0) {
                        if (p2 && p4 && p6) continue;
                        if (p4 && p6 && p8) continue;
                    } else {
                        if (p2 && p4 && p8) continue;
                        if (p2 && p6 && p8) continue;
                    }
                    borrar.push_back(i);
                }
            }
            for (int i : borrar) img[static_cast<std::size_t>(i)] = 0;
            if (!borrar.empty()) cambio = true;
        }
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

// Línea gruesa doble: dibuja dos líneas paralelas separadas `separacion` px
// respecto a la normal del segmento. La herramienta DOBLE usa esto para trazar
// dos plumas a la vez. Misma aritmética que `Lienzo.lineaDoble` en Kotlin.
void lineaDoble(std::vector<uint32_t>& hoja, int ancho, int alto,
                float x0, float y0, float x1, float y1,
                float radio, bool redonda, uint32_t color, float separacion) {
    const float dx = x1 - x0;
    const float dy = y1 - y0;
    const double largo = std::sqrt(static_cast<double>(dx) * dx +
                                   static_cast<double>(dy) * dy);
    if (largo < 1e-6) return;
    const float nx = static_cast<float>(-dy / largo);
    const float ny = static_cast<float>(dx / largo);
    const float ox = nx * separacion;
    const float oy = ny * separacion;
    lineaGruesa(hoja, ancho, alto, x0 + ox, y0 + oy, x1 + ox, y1 + oy, radio, redonda, color);
    lineaGruesa(hoja, ancho, alto, x0 - ox, y0 - oy, x1 - ox, y1 - oy, radio, redonda, color);
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

    if (herramienta_ == Tool::PINTAR) {
        // Balde: rellena la región donde se tocó con el color del lápiz.
        rellenar(hoja_, ancho_, alto_,
                 static_cast<int>(std::lround(trazo_[0])),
                 static_cast<int>(std::lround(trazo_[1])), color_);
    } else if (herramienta_ == Tool::DOBLE) {
        // Doble lápiz: dos líneas paralelas a la vez.
        const float separacion = separacionDoble(grosor);
        if (trazo_.size() == 2) {
            if (puntaRedonda_) {
                pintarDisco(hoja_, ancho_, alto_, trazo_[0], trazo_[1], radio, color);
            } else {
                lineaGruesa(hoja_, ancho_, alto_, trazo_[0], trazo_[1],
                            trazo_[0], trazo_[1], radio, false, color);
            }
        } else {
            for (std::size_t i = 0; i + 3 < trazo_.size(); i += 2) {
                lineaDoble(hoja_, ancho_, alto_, trazo_[i], trazo_[i + 1],
                           trazo_[i + 2], trazo_[i + 3], radio, puntaRedonda_,
                           color, separacion);
            }
        }
    } else if (trazo_.size() == 2) {
        // Punto aislado.
        if (puntaRedonda_) {
            pintarDisco(hoja_, ancho_, alto_, trazo_[0], trazo_[1], radio, color);
        } else {
            lineaGruesa(hoja_, ancho_, alto_, trazo_[0], trazo_[1],
                        trazo_[0], trazo_[1], radio, false, color);
        }
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

bool DibujoEngine::limpiarTrazos(float radio) {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    const std::size_t n = hoja_.size();
    std::vector<uint8_t> tinta(n, 0);
    bool hay = false;
    for (std::size_t i = 0; i < n; ++i) {
        if (hoja_[i] != fondo_) {
            tinta[i] = 1;
            hay = true;
        }
    }
    if (!hay) return false;

    // Snapshot para undo y copia de colores: el esqueleto es un subconjunto de
    // la tinta, así que cada píxel conserva el color que tenía (`origen`).
    std::vector<uint32_t> origen = hoja_;
    pasado_.push_back(hoja_);
    if (pasado_.size() > kMaxHistorial) pasado_.erase(pasado_.begin());
    futuro_.clear();

    adelgazar(tinta, ancho_, alto_);
    std::fill(hoja_.begin(), hoja_.end(), fondo_);
    for (int y = 0; y < alto_; ++y) {
        for (int x = 0; x < ancho_; ++x) {
            const std::size_t i = static_cast<std::size_t>(y) * ancho_ + x;
            if (tinta[i]) {
                pintarDisco(hoja_, ancho_, alto_,
                            static_cast<float>(x), static_cast<float>(y), radio,
                            origen[i]);
            }
        }
    }
    ++revision_;
    return true;
}

bool DibujoEngine::reemplazarHoja(const uint32_t* datos, std::size_t n) {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    if (datos == nullptr || n != hoja_.size()) return false;
    pasado_.push_back(hoja_);
    if (pasado_.size() > kMaxHistorial) pasado_.erase(pasado_.begin());
    futuro_.clear();
    std::copy(datos, datos + n, hoja_.begin());
    ++revision_;
    return true;
}

void DibujoEngine::expandir(int izquierda, int arriba, int derecha, int abajo) {
    const int izq = std::max(0, izquierda);
    const int arr = std::max(0, arriba);
    const int der = std::max(0, derecha);
    const int aba = std::max(0, abajo);
    if (izq == 0 && arr == 0 && der == 0 && aba == 0) return;

    std::lock_guard<std::mutex> bloqueo(mutex_);
    const int nuevoAncho = std::min(kMaxLado, ancho_ + izq + der);
    const int nuevoAlto = std::min(kMaxLado, alto_ + arr + aba);
    if (nuevoAncho == ancho_ && nuevoAlto == alto_) return;

    std::vector<uint32_t> nuevo(static_cast<std::size_t>(nuevoAncho) *
                                    static_cast<std::size_t>(nuevoAlto),
                                fondo_);
    const int copiaAncho = std::max(0, std::min(ancho_, nuevoAncho - izq));
    const int copiaAlto = std::max(0, std::min(alto_, nuevoAlto - arr));
    for (int y = 0; y < copiaAlto; ++y) {
        for (int x = 0; x < copiaAncho; ++x) {
            nuevo[static_cast<std::size_t>(y + arr) * nuevoAncho + (x + izq)] =
                hoja_[static_cast<std::size_t>(y) * ancho_ + x];
        }
    }

    hoja_.swap(nuevo);
    ancho_ = nuevoAncho;
    alto_ = nuevoAlto;
    pasado_.clear();
    futuro_.clear();
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
    zoom_ = std::min(kZoomMax, std::max(kZoomMin, zoom_ * factor));
}

void DibujoEngine::rotar(float delta) {
    rotacion_ += delta;
    // Normaliza a (-pi, pi] para no acumular vueltas enteras (estabilidad de
    // float en sesiones largas). Sumar 2pi con la misma fórmula que Kotlin.
    constexpr float kDosPi = 6.283185307179586f;
    while (rotacion_ > 3.141592653589793f) rotacion_ -= kDosPi;
    while (rotacion_ <= -3.141592653589793f) rotacion_ += kDosPi;
}

void DibujoEngine::centrarVista() {
    panX_ = 0.0f;
    panY_ = 0.0f;
    zoom_ = 1.0f;
    rotacion_ = 0.0f;
}

void DibujoEngine::renderizar(uint32_t* destino, int vw, int vh) const {
    if (destino == nullptr || vw <= 0 || vh <= 0) return;
    // Este camino corre en el hilo de la UI (el mismo que muta la hoja), así
    // que no necesita bloqueo; lee `hoja_` directamente para no bloquear por
    // píxel. El hilo de render GPU usa `copiarHoja`, que sí bloquea.
    const float c = static_cast<float>(std::cos(static_cast<double>(rotacion_)));
    const float s = static_cast<float>(std::sin(static_cast<double>(rotacion_)));
    for (int y = 0; y < vh; ++y) {
        for (int x = 0; x < vw; ++x) {
            // En coordenadas centradas en el pan: e = pantalla + pan.
            const float ex = static_cast<float>(x) + panX_;
            const float ey = static_cast<float>(y) + panY_;
            // R(−rot) aplicado a e, luego des-escalado por el zoom.
            const float hx = (c * ex + s * ey) / zoom_;
            const float hy = (-s * ex + c * ey) / zoom_;
            const int ix = static_cast<int>(std::floor(hx));
            const int iy = static_cast<int>(std::floor(hy));
            destino[y * vw + x] =
                (ix >= 0 && iy >= 0 && ix < ancho_ && iy < alto_)
                    ? hoja_[static_cast<std::size_t>(iy) * ancho_ + ix]
                    : kFuera;
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

void DibujoEngine::leerCamara(float& panX, float& panY, float& zoom, float& rotacion) const {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    panX = panX_;
    panY = panY_;
    zoom = zoom_;
    rotacion = rotacion_;
}

} // namespace dibujo
