#include "DibujoEngine.h"

#include <cmath>
#include <algorithm>

namespace dibujo {

// Instantánea comprimida de la hoja: RLE de píxeles iguales (cantidad, color)
// con respaldo crudo cuando el RLE no conviene. Sin pérdida: la hoja se
// restaura idéntica. Replica `InstantaneaHoja.capturar` de Kotlin (paridad).
Instantanea comprimirHoja(const std::vector<uint32_t>& hoja) {
    Instantanea instantanea;
    instantanea.pixeles = hoja.size();
    if (hoja.empty()) return instantanea;
    // Primera pasada: cuántas rachas de píxeles iguales hay.
    std::size_t rachas = 0;
    for (std::size_t i = 0; i < hoja.size(); ++i) {
        if (i == 0 || hoja[i] != hoja[i - 1]) ++rachas;
    }
    // El RLE ocupa 2 enteros por racha: sólo conviene si no supera el tamaño
    // crudo (si no, se guarda la copia tal cual).
    if (rachas * 2 <= hoja.size()) {
        instantanea.comprimido = true;
        instantanea.datos.reserve(rachas * 2);
        std::size_t inicio = 0;
        while (inicio < hoja.size()) {
            std::size_t fin = inicio + 1;
            while (fin < hoja.size() && hoja[fin] == hoja[inicio]) ++fin;
            instantanea.datos.push_back(static_cast<uint32_t>(fin - inicio));
            instantanea.datos.push_back(hoja[inicio]);
            inicio = fin;
        }
    } else {
        instantanea.datos = hoja;
    }
    return instantanea;
}

// Restaura una instantánea sobre la hoja (sobrescribe lo que haya).
void restaurarHoja(const Instantanea& instantanea, std::vector<uint32_t>& hoja) {
    if (!instantanea.comprimido) {
        const std::size_t n = std::min(instantanea.datos.size(), hoja.size());
        std::copy(instantanea.datos.begin(), instantanea.datos.begin() + n, hoja.begin());
        return;
    }
    std::size_t i = 0;
    std::size_t destino = 0;
    while (i + 1 < instantanea.datos.size() && destino < hoja.size()) {
        const std::size_t cuenta = instantanea.datos[i];
        const uint32_t color = instantanea.datos[i + 1];
        i += 2;
        for (std::size_t k = 0; k < cuenta && destino < hoja.size(); ++k) {
            hoja[destino++] = color;
        }
    }
}

namespace {

// Refleja los puntos de la polilínea respecto del centro de la hoja: en
// vertical, `x' = (ancho - 1) - x`; en horizontal, `y' = (alto - 1) - y`.
// Replica `MotorKotlin.reflejar` (paridad): sólo restas, sin trigonometría.
std::vector<float> reflejarTrazo(const std::vector<float>& puntos, int ancho,
                                 int alto, bool ejeX, bool ejeY) {
    std::vector<float> salida(puntos.size());
    for (std::size_t i = 0; i + 1 < puntos.size(); i += 2) {
        float x = puntos[i];
        float y = puntos[i + 1];
        if (ejeX) x = static_cast<float>(ancho - 1) - x;
        if (ejeY) y = static_cast<float>(alto - 1) - y;
        salida[i] = x;
        salida[i + 1] = y;
    }
    return salida;
}

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

// Herramienta real que usa el trazo acompañante ("la doble"). El pincel
// vectorial no lo dibuja el motor (lo crea la app como objeto vivo), pero
// conserva su grosor base.
inline Tool herramientaDeDoble(ToolDoble tool) {
    switch (tool) {
        case ToolDoble::PORTAMINA: return Tool::PORTAMINA;
        case ToolDoble::CARBONCILLO: return Tool::CARBONCILLO;
        case ToolDoble::PLUMA: return Tool::PLUMA;
        case ToolDoble::LAPIZ:
        default: return Tool::LAPIZ;
    }
}

// Separación entre el trazo principal y el acompañante de la herramienta
// DOBLE: crece con el grosor para que el hueco entre ambos sea visible. La
// fórmula la replica `TrazoDoble.separacion` de Kotlin (paridad).
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

// Ruido entero determinista por coordenada. Usa uint32_t (desborde definido)
// y la misma mezcla que `Textura.ruido` de Kotlin para no romper la paridad.
inline uint32_t ruidoTextura(int x, int y) {
    uint32_t h = static_cast<uint32_t>(
        static_cast<int64_t>(x) * 374761393LL +
        static_cast<int64_t>(y) * 668265263LL);
    h = (h ^ (h >> 13)) * 1274126177u;
    h ^= h >> 16;
    return h & 0xFFu;
}

// Factor de cobertura (0..255) del píxel (x, y) para una textura. Réplica
// exacta de `Textura.factor` en Kotlin (paridad píxel a píxel).
inline int factorTextura(Textura textura, int x, int y) {
    switch (textura) {
        case Textura::LISA:
            return 255;
        case Textura::GRANO:
            return 145 + static_cast<int>(ruidoTextura(x, y)) * 110 / 255;
        case Textura::RUGOSA: {
            const int n = static_cast<int>(ruidoTextura(x >> 2, y >> 2));
            return 120 + n * 135 / 255;
        }
        case Textura::TRAMA:
            return (((x + y) % 7) < 2) ? 255 : 165;
        case Textura::SALPICADA:
            return (ruidoTextura(x, y) < 38u) ? 70 : 255;
        default:
            return 255;
    }
}

// Aplica la textura a una cobertura (0..255). LISA no la altera.
inline int aplicarTextura(Textura textura, int alpha, int x, int y) {
    if (textura == Textura::LISA || alpha <= 0) return alpha;
    return (alpha * factorTextura(textura, x, y) + 127) / 255;
}

// Interpola un canal entre 4 texeles y redondea a entero. La asociación de las
// operaciones y `floor(v + 0.5)` coinciden con `MotorKotlin.canalBilineal`.
inline int canalBilineal(int c00, int c10, int c01, int c11,
                         double fx, double fy) {
    const double arriba = static_cast<double>(c00) +
                          static_cast<double>(c10 - c00) * fx;
    const double abajo = static_cast<double>(c01) +
                         static_cast<double>(c11 - c01) * fx;
    const double v = arriba + (abajo - arriba) * fy;
    return static_cast<int>(std::floor(v + 0.5));
}

// Pinta un disco suavizado centrado en (cx, cy) con radio r.
// La cobertura se calcula analíticamente (0.5 + r − distancia) usando double,
// igual que el respaldo Kotlin, para que el antialiasing sea idéntico.
void pintarDisco(std::vector<uint32_t>& hoja, int ancho, int alto,
                 float cx, float cy, float radio, uint32_t color,
                 Textura textura = Textura::LISA) {
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
            pintarMezcla(hoja, ancho, alto, x, y, color,
                         aplicarTextura(textura, alpha, x, y));
        }
    }
}

inline int alphaCobertura(double cobertura) {
    if (cobertura <= 0.0) return 0;
    if (cobertura > 1.0) return 255;
    return static_cast<int>(std::floor(cobertura * 255.0 + 0.5));
}

// Acumula en `cobertura` la cobertura máxima (0..255) del segmento
// (ax, ay)-(bx, by) con radio `radio`. La distancia punto-segmento va en
// double y el redondeo con `floor(v + 0.5)` coincide con el Kotlin.
void cubrirSegmento(std::vector<uint8_t>& cobertura, int anchoCaja,
                    int bx0, int by0, int bx1, int by1,
                    float ax, float ay, float bx, float by, float radio) {
    const float margen = radio + 1.0f;
    const int x0 = std::min(bx1, std::max(bx0, static_cast<int>(
        std::floor(static_cast<double>(ax - margen)))));
    const int x1 = std::min(bx1, std::max(bx0, static_cast<int>(
        std::ceil(static_cast<double>(ax + margen)))));
    const int y0 = std::min(by1, std::max(by0, static_cast<int>(
        std::floor(static_cast<double>(ay - margen)))));
    const int y1 = std::min(by1, std::max(by0, static_cast<int>(
        std::ceil(static_cast<double>(ay + margen)))));
    const int x2 = std::min(bx1, std::max(bx0, static_cast<int>(
        std::floor(static_cast<double>(bx - margen)))));
    const int x3 = std::min(bx1, std::max(bx0, static_cast<int>(
        std::ceil(static_cast<double>(bx + margen)))));
    const int y2 = std::min(by1, std::max(by0, static_cast<int>(
        std::floor(static_cast<double>(by - margen)))));
    const int y3 = std::min(by1, std::max(by0, static_cast<int>(
        std::ceil(static_cast<double>(by + margen)))));
    const int desdeX = std::min(x0, x2);
    const int hastaX = std::max(x1, x3);
    const int desdeY = std::min(y0, y2);
    const int hastaY = std::max(y1, y3);
    if (desdeX > hastaX || desdeY > hastaY) return;
    const double axD = static_cast<double>(ax);
    const double ayD = static_cast<double>(ay);
    const double dx = static_cast<double>(bx) - axD;
    const double dy = static_cast<double>(by) - ayD;
    const double largo2 = dx * dx + dy * dy;
    const double r = static_cast<double>(radio);
    for (int y = desdeY; y <= hastaY; ++y) {
        const int fila = (y - by0) * anchoCaja;
        const double pdy = static_cast<double>(y) - ayD;
        for (int x = desdeX; x <= hastaX; ++x) {
            const double pdx = static_cast<double>(x) - axD;
            double dist;
            if (largo2 <= 1e-12) {
                dist = std::sqrt(pdx * pdx + pdy * pdy);
            } else {
                double t = (pdx * dx + pdy * dy) / largo2;
                if (t < 0.0) t = 0.0;
                if (t > 1.0) t = 1.0;
                const double qx = pdx - t * dx;
                const double qy = pdy - t * dy;
                dist = std::sqrt(qx * qx + qy * qy);
            }
            const double cob = 0.5 + r - dist;
            if (cob <= 0.0) continue;
            const int alpha = alphaCobertura(cob);
            const int idx = fila + (x - bx0);
            if (alpha > cobertura[static_cast<std::size_t>(idx)]) {
                cobertura[static_cast<std::size_t>(idx)] = static_cast<uint8_t>(alpha);
            }
        }
    }
}

void lineaGruesa(std::vector<uint32_t>& hoja, int ancho, int alto,
                 float x0, float y0, float x1, float y1,
                 float radio, bool redonda, uint32_t color,
                 Textura textura);

// Polilínea con cobertura máxima por píxel y una sola mezcla: los segmentos
// que comparten vértice ya no oscurecen el empalme por doble mezcla. Replica
// `Lienzo.polilineaGruesa` (paridad píxel a píxel).
void polilineaGruesa(std::vector<uint32_t>& hoja, int ancho, int alto,
                     const float* puntos, int cantidad, float radio,
                     bool redonda, uint32_t color,
                     Textura textura = Textura::LISA) {
    if (cantidad < 2) return;
    if (!redonda) {
        for (int i = 0; i + 3 < cantidad; i += 2) {
            lineaGruesa(hoja, ancho, alto, puntos[i], puntos[i + 1],
                        puntos[i + 2], puntos[i + 3], radio, false, color, textura);
        }
        if (cantidad == 2) {
            const int px = static_cast<int>(std::lround(puntos[0]));
            const int py = static_cast<int>(std::lround(puntos[1]));
            const int r = static_cast<int>(std::lround(radio));
            for (int oy = -r; oy <= r; ++oy) {
                for (int ox = -r; ox <= r; ++ox) {
                    const int xx = px + ox;
                    const int yy = py + oy;
                    pintarMezcla(hoja, ancho, alto, xx, yy, color,
                                 aplicarTextura(textura, 255, xx, yy));
                }
            }
        }
        return;
    }
    if (cantidad == 2) {
        pintarDisco(hoja, ancho, alto, puntos[0], puntos[1], radio, color, textura);
        return;
    }
    float minX = puntos[0];
    float minY = puntos[1];
    float maxX = puntos[0];
    float maxY = puntos[1];
    for (int i = 2; i + 1 < cantidad; i += 2) {
        minX = std::min(minX, puntos[i]);
        maxX = std::max(maxX, puntos[i]);
        minY = std::min(minY, puntos[i + 1]);
        maxY = std::max(maxY, puntos[i + 1]);
    }
    const float margen = radio + 1.0f;
    const int bx0 = std::max(0, static_cast<int>(
        std::floor(static_cast<double>(minX - margen))));
    const int by0 = std::max(0, static_cast<int>(
        std::floor(static_cast<double>(minY - margen))));
    const int bx1 = std::min(ancho - 1, static_cast<int>(
        std::ceil(static_cast<double>(maxX + margen))));
    const int by1 = std::min(alto - 1, static_cast<int>(
        std::ceil(static_cast<double>(maxY + margen))));
    if (bx0 > bx1 || by0 > by1) return;
    const int anchoCaja = bx1 - bx0 + 1;
    const int altoCaja = by1 - by0 + 1;
    std::vector<uint8_t> cobertura(
        static_cast<std::size_t>(anchoCaja) * static_cast<std::size_t>(altoCaja), 0);
    for (int i = 0; i + 3 < cantidad; i += 2) {
        cubrirSegmento(cobertura, anchoCaja, bx0, by0, bx1, by1,
                       puntos[i], puntos[i + 1], puntos[i + 2], puntos[i + 3], radio);
    }
    for (int y = by0; y <= by1; ++y) {
        const int fila = (y - by0) * anchoCaja;
        for (int x = bx0; x <= bx1; ++x) {
            const int alpha = cobertura[static_cast<std::size_t>(fila + (x - bx0))];
            if (alpha > 0) {
                pintarMezcla(hoja, ancho, alto, x, y, color,
                             aplicarTextura(textura, alpha, x, y));
            }
        }
    }
}

// Línea gruesa: con punta redonda se rasteriza como una cápsula (cobertura
// única por píxel); con punta cuadrada se estampa el bloque como antes.
void lineaGruesa(std::vector<uint32_t>& hoja, int ancho, int alto,
                 float x0, float y0, float x1, float y1,
                 float radio, bool redonda, uint32_t color,
                 Textura textura = Textura::LISA) {
    if (!redonda) {
        const int pasos = std::max(1, static_cast<int>(std::ceil(
            std::max(std::abs(x1 - x0), std::abs(y1 - y0)))));
        for (int i = 0; i <= pasos; ++i) {
            const float t = static_cast<float>(i) / static_cast<float>(pasos);
            const float x = x0 + (x1 - x0) * t;
            const float y = y0 + (y1 - y0) * t;
            const int px = static_cast<int>(std::lround(x));
            const int py = static_cast<int>(std::lround(y));
            const int r = static_cast<int>(std::lround(radio));
            for (int oy = -r; oy <= r; ++oy) {
                for (int ox = -r; ox <= r; ++ox) {
                    const int xx = px + ox;
                    const int yy = py + oy;
                    pintarMezcla(hoja, ancho, alto, xx, yy, color,
                                 aplicarTextura(textura, 255, xx, yy));
                }
            }
        }
        return;
    }
    const float puntos[4] = {x0, y0, x1, y1};
    polilineaGruesa(hoja, ancho, alto, puntos, 4, radio, true, color, textura);
}

// Desplaza la polilínea del gesto perpendicularmente para dibujar el trazo
// acompañante ("la doble"). La normal se suaviza por vértice (se usa la
// dirección de la cuerda que une los vecinos), así las dos líneas no tiemblan
// en los empalmes. Con `LadoDoble::MISMO` devuelve los mismos puntos: el
// acompañante se dibuja encima del principal.
//
// Replica `TrazoDoble.desplazar` de Kotlin (paridad píxel a píxel).
std::vector<float> desplazarTrazo(const std::vector<float>& puntos,
                                  float separacion, LadoDoble lado) {
    const std::size_t cantidad = puntos.size();
    std::vector<float> salida(cantidad, 0.0f);
    // Con menos de dos vértices no hay dirección: no hay nada que desplazar.
    if (cantidad < 4 || lado == LadoDoble::MISMO || separacion == 0.0f) {
        for (std::size_t i = 0; i < cantidad; ++i) salida[i] = puntos[i];
        return salida;
    }
    // La normal (-dy, dx) apunta a la derecha del sentido del trazo (en
    // coordenadas de pantalla, con la y hacia abajo); la izquierda es la
    // opuesta.
    const float signo = (lado == LadoDoble::IZQUIERDA) ? -1.0f : 1.0f;
    const int n = static_cast<int>(cantidad) / 2;
    for (int i = 0; i < n; ++i) {
        const int base = i * 2;
        float dx;
        float dy;
        if (i == 0) {
            dx = puntos[2] - puntos[0];
            dy = puntos[3] - puntos[1];
        } else if (i == n - 1) {
            dx = puntos[base] - puntos[base - 2];
            dy = puntos[base + 1] - puntos[base - 1];
        } else {
            dx = puntos[base + 2] - puntos[base - 2];
            dy = puntos[base + 3] - puntos[base - 1];
        }
        const double largo = std::sqrt(static_cast<double>(dx) * dx +
                                       static_cast<double>(dy) * dy);
        float nx;
        float ny;
        if (largo < 1e-6) {
            nx = 0.0f;
            ny = 0.0f;
        } else {
            nx = static_cast<float>(-dy / largo);
            ny = static_cast<float>(dx / largo);
        }
        const float ox = nx * separacion * signo;
        const float oy = ny * separacion * signo;
        salida[static_cast<std::size_t>(base)] = puntos[base] + ox;
        salida[static_cast<std::size_t>(base + 1)] = puntos[base + 1] + oy;
    }
    return salida;
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
    pasado_.push_back(comprimirHoja(hoja_));
    if (pasado_.size() > maxHistorial_) pasado_.erase(pasado_.begin());
    futuro_.clear();

    // Trazo asistido: se descarta el recorrido tembloroso y se dibuja una recta
    // entre el primer y el último punto. PINTAR no usa el recorrido.
    if (asistenciaRecta_ && herramienta_ != Tool::PINTAR && trazo_.size() >= 4) {
        const float x0 = trazo_[0];
        const float y0 = trazo_[1];
        const float x1 = trazo_[trazo_.size() - 2];
        const float y1 = trazo_[trazo_.size() - 1];
        trazo_.clear();
        trazo_.push_back(x0);
        trazo_.push_back(y0);
        trazo_.push_back(x1);
        trazo_.push_back(y1);
    }

    const bool borrar = herramienta_ == Tool::BORRADOR;
    const uint32_t color = borrar ? fondo_ : color_;
    // En modo DOBLE el grosor del gesto es el del trazo principal.
    const Tool herramientaTrazo = (herramienta_ == Tool::DOBLE)
        ? herramientaPrincipal_
        : herramienta_;
    const int grosor = grosorHerramienta(herramientaTrazo, grosor_);
    const float radio = static_cast<float>(grosor) * 0.5f;
    // El borrador borra uniforme (sin textura); el resto usa la elegida.
    const Textura textura = borrar ? Textura::LISA : textura_;

    if (herramienta_ == Tool::PINTAR) {
        // Balde: rellena la región donde se tocó con el color del lápiz.
        rellenar(hoja_, ancho_, alto_,
                 static_cast<int>(std::lround(trazo_[0])),
                 static_cast<int>(std::lround(trazo_[1])), color_);
    } else {
        // Trazo acompañante ("la doble"): su propia herramienta, su color de
        // sombra y el lado donde se dibuja. Con el pincel vectorial el doble
        // NO lo dibuja el motor (lo crea la app como objeto vivo).
        const float separacion = separacionDoble(grosor);
        const int grosorDoble = grosorHerramienta(
            herramientaDeDoble(herramientaDoble_), grosor_);
        // Dibuja una polilínea con la herramienta actual: en modo DOBLE se
        // dibujan las dos líneas (la acompañante debajo, para que con
        // LadoDoble::MISMO quede como sombra/contorno del principal).
        const auto dibujar = [&](const std::vector<float>& puntos) {
            if (herramienta_ == Tool::DOBLE) {
                if (herramientaDoble_ != ToolDoble::PLUMA) {
                    const std::vector<float> desplazado =
                        desplazarTrazo(puntos, separacion, ladoDoble_);
                    polilineaGruesa(hoja_, ancho_, alto_, desplazado.data(),
                                    static_cast<int>(desplazado.size()),
                                    static_cast<float>(grosorDoble) * 0.5f,
                                    puntaRedonda_, colorDoble_, textura);
                }
            }
            polilineaGruesa(hoja_, ancho_, alto_, puntos.data(),
                            static_cast<int>(puntos.size()), radio,
                            puntaRedonda_, color, textura);
        };
        dibujar(trazo_);
        // Simetría: el trazo se dibuja también en sus reflejos respecto del
        // centro de la hoja. El reflejo es una resta (sin trigonometría) para
        // que Kotlin dé el mismo resultado píxel a píxel.
        if (simetria_ != Simetria::NINGUNA) {
            const bool vertical = simetria_ == Simetria::VERTICAL ||
                                  simetria_ == Simetria::CUADRADA;
            const bool horizontal = simetria_ == Simetria::HORIZONTAL ||
                                    simetria_ == Simetria::CUADRADA;
            if (vertical) {
                dibujar(reflejarTrazo(trazo_, ancho_, alto_, true, false));
            }
            if (horizontal) {
                dibujar(reflejarTrazo(trazo_, ancho_, alto_, false, true));
            }
            if (vertical && horizontal) {
                dibujar(reflejarTrazo(trazo_, ancho_, alto_, true, true));
            }
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

void DibujoEngine::fijarLimiteHistorial(std::size_t maximo) {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    maxHistorial_ = maximo < 1 ? 1 : maximo;
    while (pasado_.size() > maxHistorial_) pasado_.erase(pasado_.begin());
}

bool DibujoEngine::deshacer() {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    if (pasado_.empty()) return false;
    futuro_.push_back(comprimirHoja(hoja_));
    restaurarHoja(pasado_.back(), hoja_);
    pasado_.pop_back();
    ++revision_;
    return true;
}

bool DibujoEngine::rehacer() {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    if (futuro_.empty()) return false;
    pasado_.push_back(comprimirHoja(hoja_));
    restaurarHoja(futuro_.back(), hoja_);
    futuro_.pop_back();
    ++revision_;
    return true;
}

void DibujoEngine::limpiarHoja() {
    std::lock_guard<std::mutex> bloqueo(mutex_);
    pasado_.push_back(comprimirHoja(hoja_));
    if (pasado_.size() > maxHistorial_) pasado_.erase(pasado_.begin());
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
    pasado_.push_back(comprimirHoja(hoja_));
    if (pasado_.size() > maxHistorial_) pasado_.erase(pasado_.begin());
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
    pasado_.push_back(comprimirHoja(hoja_));
    if (pasado_.size() > maxHistorial_) pasado_.erase(pasado_.begin());
    futuro_.clear();
    std::copy(datos, datos + n, hoja_.begin());
    ++revision_;
    return true;
}

bool DibujoEngine::transformar(bool voltearHorizontal, bool voltearVertical,
                               float desplazamientoX, float desplazamientoY, float escala) {
    if (escala <= 0.0f) return false;
    if (!voltearHorizontal && !voltearVertical &&
        desplazamientoX == 0.0f && desplazamientoY == 0.0f && escala == 1.0f) {
        return false;
    }
    std::lock_guard<std::mutex> bloqueo(mutex_);
    // Copia para muestrear y para el historial (se comprime al final, así el
    // historial no guarda una copia completa de la hoja por paso). El mapeo
    // inverso por píxel destino evita huecos al ampliar.
    std::vector<uint32_t> fuente = hoja_;
    const double cx = (ancho_ - 1) / 2.0;
    const double cy = (alto_ - 1) / 2.0;
    const double e = static_cast<double>(escala);
    const double tx = static_cast<double>(desplazamientoX);
    const double ty = static_cast<double>(desplazamientoY);
    const double fx = voltearHorizontal ? -1.0 : 1.0;
    const double fy = voltearVertical ? -1.0 : 1.0;
    for (int y = 0; y < alto_; ++y) {
        const double v = static_cast<double>(y) - cy;
        const int sy = static_cast<int>(std::floor(cy + fy * ((v - ty) / e) + 0.5));
        const std::size_t fila = static_cast<std::size_t>(y) * ancho_;
        for (int x = 0; x < ancho_; ++x) {
            const double u = static_cast<double>(x) - cx;
            const int sx = static_cast<int>(std::floor(cx + fx * ((u - tx) / e) + 0.5));
            hoja_[fila + x] =
                (sx >= 0 && sy >= 0 && sx < ancho_ && sy < alto_)
                    ? fuente[static_cast<std::size_t>(sy) * ancho_ + sx]
                    : fondo_;
        }
    }
    pasado_.push_back(comprimirHoja(fuente));
    if (pasado_.size() > maxHistorial_) pasado_.erase(pasado_.begin());
    futuro_.clear();
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
            const int ix = static_cast<int>(std::floor(static_cast<double>(hx)));
            const int iy = static_cast<int>(std::floor(static_cast<double>(hy)));
            if (ix < 0 || iy < 0 || ix >= ancho_ || iy >= alto_) {
                destino[y * vw + x] = kFuera;
                continue;
            }
            // Bilineal (centro del texel en el entero): al acercar no se ven
            // bloques duros. Misma aritmética que `MotorKotlin`.
            const double fx = static_cast<double>(hx) - ix;
            const double fy = static_cast<double>(hy) - iy;
            const int ix1 = (ix + 1 < ancho_) ? ix + 1 : ix;
            const int iy1 = (iy + 1 < alto_) ? iy + 1 : iy;
            const std::size_t fila0 = static_cast<std::size_t>(iy) * ancho_;
            const std::size_t fila1 = static_cast<std::size_t>(iy1) * ancho_;
            const uint32_t c00 = hoja_[fila0 + ix];
            const uint32_t c10 = hoja_[fila0 + ix1];
            const uint32_t c01 = hoja_[fila1 + ix];
            const uint32_t c11 = hoja_[fila1 + ix1];
            const int a = canalBilineal(static_cast<int>((c00 >> 24) & 0xFFu),
                                        static_cast<int>((c10 >> 24) & 0xFFu),
                                        static_cast<int>((c01 >> 24) & 0xFFu),
                                        static_cast<int>((c11 >> 24) & 0xFFu), fx, fy);
            const int r = canalBilineal(static_cast<int>((c00 >> 16) & 0xFFu),
                                        static_cast<int>((c10 >> 16) & 0xFFu),
                                        static_cast<int>((c01 >> 16) & 0xFFu),
                                        static_cast<int>((c11 >> 16) & 0xFFu), fx, fy);
            const int g = canalBilineal(static_cast<int>((c00 >> 8) & 0xFFu),
                                        static_cast<int>((c10 >> 8) & 0xFFu),
                                        static_cast<int>((c01 >> 8) & 0xFFu),
                                        static_cast<int>((c11 >> 8) & 0xFFu), fx, fy);
            const int b = canalBilineal(static_cast<int>(c00 & 0xFFu),
                                        static_cast<int>(c10 & 0xFFu),
                                        static_cast<int>(c01 & 0xFFu),
                                        static_cast<int>(c11 & 0xFFu), fx, fy);
            destino[y * vw + x] = (static_cast<uint32_t>(a) << 24) |
                                  (static_cast<uint32_t>(r) << 16) |
                                  (static_cast<uint32_t>(g) << 8) |
                                  static_cast<uint32_t>(b);
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
