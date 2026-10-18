#!/usr/bin/env python3
"""Plan B: dataset con licencia comercial para limpiar trazos.

Objetivo: entrenar el limpiador de trazos con datos que **permitan monetizar** y
que **no obliguen** a liberar la app como open source. Por eso este plan deja de
lado SketchBench (tiene bocetos solo de uso academico) y usa **Quick, Draw!**
(Google, CC BY 4.0: uso comercial permitido, solo pide atribucion).

Quick, Draw! trae trazos vectoriales **humanos reales** (no curvas Bezier
sinteticas). El par (repasado -> limpio) se fabrica por simulacion, igual que el
"inverse dataset construction" de Simo-Serra: se engrosa y repasa el trazo limpio
con varias pasadas y jitter, y el objetivo es el trazo de 1 px. Asi se cierra la
brecha de dominio sin depender de datos no comerciales.

El `.npz` que sale usa el MISMO formato que el Plan A, asi que el entrenamiento
lo consume sin cambios:

    python3 tools/entrenar_limpiar_trazos.py --pares <npz>

Cada categoria de Quick, Draw! pesa ~40-100 MB, pero para el modelo alcanza la
cabecera: se baja solo un rango HTTP (el host de Google Cloud Storage soporta
`accept-ranges`) y se usan a lo sumo `--por-categoria` dibujos. Las fases
`descargar` y `preprocesar` son separadas y reanudables para no exceder el corte
de ~1 h de CI.

Dependencias: solo `numpy` (la rasterizacion es propia, sin Pillow ni CairoSVG).

Fases:
    descargar     baja la cabecera de cada categoria al cache (rango HTTP, reanudable)
    preprocesar   fabrica los pares (repasado -> limpio) y guarda pares.npz
    todo          descargar + preprocesar
    estado        muestra que hay en el cache
    autoprueba    extrae pares de un dibujo de ejemplo (sin red)

Uso (desde la raiz del repo):

    python3 tools/preparar_dataset_limpiar_trazos_planb.py descargar
    python3 tools/preparar_dataset_limpiar_trazos_planb.py preprocesar --lado 512
    python3 tools/preparar_dataset_limpiar_trazos_planb.py todo
    python3 tools/preparar_dataset_limpiar_trazos_planb.py estado
"""

import argparse
import json
import os
import time
import urllib.request

import numpy as np

# Raiz del repo (este archivo vive en <raiz>/tools/).
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REGISTRO = os.path.join(RAIZ, "tools", "datasets_limpiar_trazos_planb.json")
CACHE_DEF = os.path.join(RAIZ, ".cache", "limpiar_trazos_planb")

# Lado de las hojas cacheadas. Los parches de entrenamiento siguen siendo 64x64.
LADO_DEF = 512
MARGEN_DEF = 8
POR_CATEGORIA_DEF = 100
AGENTE = "Dibujo-DatasetBot/1.0"


# --------------------------------------------------------------------------
# Registro y utilidades
# --------------------------------------------------------------------------
def cargar_registro():
    with open(REGISTRO, "r", encoding="utf-8") as f:
        return json.load(f)


def fuente_por_id(registro, fuente_id):
    for fuente in registro["fuentes"]:
        if fuente["id"] == fuente_id:
            return fuente
    raise SystemExit(f"fuente desconocida: {fuente_id}")


def ruta_categoria(cache, categoria):
    return os.path.join(cache, "crudos", "quickdraw", f"{categoria}.ndjson")


def ruta_pares(cache, lado):
    return os.path.join(cache, "procesado", f"pares_esq_lado{lado}.npz")


def ruta_manifiesto(cache, lado):
    return os.path.join(cache, "procesado", f"manifiesto_esq_lado{lado}.json")


# --------------------------------------------------------------------------
# Fase 1: descargar (solo la cabecera de cada categoria, por rango HTTP)
# --------------------------------------------------------------------------
def descargar_categoria(url, destino, max_bytes, forzar=False, timeout=120):
    """Baja `bytes=0..max_bytes-1` de [url] a [destino], reanudable.

    No baja la categoria completa (pesa ~40-100 MB): con `--por-categoria`
    dibujos alcanza con la cabecera. Si ya existe un archivo valido no hace
    nada; si hay un `.parcial` lo reanuda con `Range`.
    """
    os.makedirs(os.path.dirname(destino), exist_ok=True)

    if os.path.exists(destino) and not forzar:
        tam = os.path.getsize(destino)
        if tam > 0:
            print(f"[descargar] ya esta: {destino} ({tam} bytes)")
            return destino

    parcial = destino + ".parcial"
    inicio = os.path.getsize(parcial) if os.path.exists(parcial) else 0
    if inicio >= max_bytes:
        os.replace(parcial, destino)
        print(f"[descargar] ya estaba completo: {destino}")
        return destino

    cabeceras = {"User-Agent": AGENTE, "Range": f"bytes={inicio}-{max_bytes - 1}"}
    req = urllib.request.Request(url, headers=cabeceras)
    print(f"[descargar] {url} -> {destino} (desde {inicio} bytes, tope {max_bytes})")

    with urllib.request.urlopen(req, timeout=timeout) as r:
        reanudado = inicio > 0 and getattr(r, "status", 200) == 206
        if not reanudado:
            inicio = 0
        modo = "ab" if reanudado else "wb"
        descargado = inicio
        with open(parcial, modo) as f:
            while descargado < max_bytes:
                bloque = r.read(min(1 << 20, max_bytes - descargado))
                if not bloque:
                    break
                f.write(bloque)
                descargado += len(bloque)

    os.replace(parcial, destino)
    print(f"[descargar] listo: {destino} ({descargado} bytes)")
    return destino


def cmd_descargar(args):
    registro = cargar_registro()
    fuente = fuente_por_id(registro, args.fuente or registro["fuente_por_defecto"])
    if fuente.get("formato") != "ndjson":
        raise SystemExit(f"la fuente {fuente['id']} no es ndjson; este plan solo baja ndjson")
    plantilla = fuente["url_plantilla"]
    categorias = fuente["categorias"]
    if args.categorias:
        categorias = [c.strip() for c in args.categorias.split(",") if c.strip()]
    tope = int(fuente.get("max_bytes_por_categoria", 12_000_000))
    for categoria in categorias:
        descargar_categoria(
            plantilla.format(categoria=categoria),
            ruta_categoria(args.cache, categoria),
            tope,
            forzar=args.forzar,
        )


# --------------------------------------------------------------------------
# Fabricacion de pares: geometria, rasterizado y repase simulado
# --------------------------------------------------------------------------
def _leer_dibujos(ruta, maximo):
    """Lee hasta [maximo] dibujos ndjson de [ruta].

    Descarta lineas vacias o cortadas (la ultima linea del rango puede quedar a
    medias). Es determinista: siempre toma las primeras del archivo.
    """
    dibujos = []
    with open(ruta, "r", encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea:
                continue
            try:
                d = json.loads(linea)
            except json.JSONDecodeError:
                continue
            if not isinstance(d, dict) or not d.get("drawing"):
                continue
            dibujos.append(d)
            if maximo and len(dibujos) >= maximo:
                break
    return dibujos


def _a_lienzo(trazos, lado, margen):
    """Escala y centra los trazos (coords 0..255) dentro de la hoja [lado]."""
    xs_all = np.concatenate([xs for xs, _ in trazos])
    ys_all = np.concatenate([ys for _, ys in trazos])
    minx, maxx = float(xs_all.min()), float(xs_all.max())
    miny, maxy = float(ys_all.min()), float(ys_all.max())
    ancho = max(maxx - minx, 1e-3)
    alto = max(maxy - miny, 1e-3)
    escala = (lado - 2.0 * margen) / max(ancho, alto)
    ox = (lado - ancho * escala) / 2.0 - minx * escala
    oy = (lado - alto * escala) / 2.0 - miny * escala
    return [(xs * escala + ox, ys * escala + oy) for xs, ys in trazos]


def _linea(m, x0, y0, x1, y1):
    """Dibuja un segmento de 1 px (Bresenham) en la mascara binaria [m]."""
    lado = m.shape[0]
    x0, y0 = int(round(x0)), int(round(y0))
    x1, y1 = int(round(x1)), int(round(y1))
    dx = abs(x1 - x0)
    dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        if 0 <= x0 < lado and 0 <= y0 < lado:
            m[y0, x0] = 1
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def _objetivo(trazos, lado):
    """Objetivo: el trazo limpio de 1 px (polilinea de Bresenham)."""
    m = np.zeros((lado, lado), np.uint8)
    for xs, ys in trazos:
        if len(xs) == 1:
            x, y = int(round(xs[0])), int(round(ys[0]))
            if 0 <= x < lado and 0 <= y < lado:
                m[y, x] = 1
            continue
        for i in range(len(xs) - 1):
            _linea(m, xs[i], ys[i], xs[i + 1], ys[i + 1])
    return m


def _kernel(radio):
    """Disco con cobertura antialias: 0.5 + r - distancia (igual que el motor)."""
    r = round(radio * 2.0) / 2.0
    h = int(np.ceil(r)) + 1
    yy, xx = np.mgrid[-h:h + 1, -h:h + 1]
    d = np.sqrt(xx * xx + yy * yy)
    return np.clip(0.5 + r - d, 0.0, 1.0).astype(np.float32)


class Kernels:
    """Cache de discos por radio (evita recalcular el kernel en cada punto)."""

    def __init__(self):
        self._c = {}

    def obtener(self, radio):
        clave = round(radio * 2.0) / 2.0
        k = self._c.get(clave)
        if k is None:
            k = _kernel(clave)
            self._c[clave] = k
        return k


def _sello(m, cx, cy, k):
    """Estampa el disco [k] con maximo en [m] alrededor de (cx, cy)."""
    h = k.shape[0] // 2
    x0 = int(round(cx)) - h
    y0 = int(round(cy)) - h
    mx0 = max(0, x0)
    my0 = max(0, y0)
    mx1 = min(m.shape[1], x0 + k.shape[1])
    my1 = min(m.shape[0], y0 + k.shape[0])
    if mx0 >= mx1 or my0 >= my1:
        return
    np.maximum(
        m[my0:my1, mx0:mx1],
        k[my0 - y0:my1 - y0, mx0 - x0:mx1 - x0],
        out=m[my0:my1, mx0:mx1],
    )


def _densificar(xs, ys, paso=0.8):
    """Repunta la polilinea a ~[paso] px: sin esto, trazos con vertices separados
    quedarian punteados al estampar (QuickDraw ya viene denso, pero no confiemos)."""
    if len(xs) <= 1:
        return xs, ys
    dx = np.diff(xs)
    dy = np.diff(ys)
    dist = np.hypot(dx, dy)
    n = np.maximum(1, np.ceil(dist / paso).astype(int))
    outx = [xs[:1]]
    outy = [ys[:1]]
    for i in range(len(xs) - 1):
        t = np.arange(1, n[i] + 1) / n[i]
        outx.append(xs[i] + dx[i] * t)
        outy.append(ys[i] + dy[i] * t)
    return np.concatenate(outx), np.concatenate(outy)


def _repasado(trazos, lado, rng, kernels):
    """Entrada: simula el trazo repasado a mano (varias pasadas, grueso, ruido)."""
    densos = [_densificar(xs, ys) for xs, ys in trazos]
    m = np.zeros((lado, lado), np.float32)
    for _ in range(int(rng.integers(1, 4))):
        radio = float(rng.uniform(1.5, 4.5))
        k = kernels.obtener(radio)
        jx = float(rng.normal(0.0, rng.uniform(0.0, 2.5)))
        jy = float(rng.normal(0.0, rng.uniform(0.0, 2.5)))
        for xs, ys in densos:
            px = xs + jx + rng.normal(0.0, 0.5, len(xs))
            py = ys + jy + rng.normal(0.0, 0.5, len(ys))
            for a, b in zip(px, py):
                _sello(m, a, b, k)
    # Manchas sueltas (ruido de repase).
    xs_all = np.concatenate([xs for xs, _ in trazos])
    ys_all = np.concatenate([ys for _, ys in trazos])
    for _ in range(int(rng.integers(0, 4))):
        i = int(rng.integers(0, len(xs_all)))
        cx = xs_all[i] + rng.uniform(-6.0, 6.0)
        cy = ys_all[i] + rng.uniform(-6.0, 6.0)
        _sello(m, cx, cy, kernels.obtener(float(rng.uniform(1.0, 3.0))))
    return np.clip(m, 0.0, 1.0)


def _trazos_de(dibujo):
    """Extrae [(xs, ys), ...] float de un registro de Quick, Draw!."""
    trazos = []
    for trazo in dibujo.get("drawing") or []:
        if not trazo or len(trazo) < 2:
            continue
        xs = np.asarray(trazo[0], dtype=np.float64)
        ys = np.asarray(trazo[1], dtype=np.float64)
        if xs.size == 0 or ys.size == 0:
            continue
        trazos.append((xs, ys))
    return trazos


def _generar_pares(dibujos, categoria, lado, margen, min_tinta, rng, kernels):
    equis, yes, nombres, bases, categorias = [], [], [], [], []
    for dibujo in dibujos:
        trazos = _trazos_de(dibujo)
        if not trazos:
            continue
        trazos = _a_lienzo(trazos, lado, margen)
        y = _objetivo(trazos, lado)
        if int(y.sum()) < min_tinta:
            continue
        x = _repasado(trazos, lado, rng, kernels)
        equis.append(((1.0 - x) * 255.0).astype(np.uint8))
        yes.append(y)
        llave = str(dibujo.get("key_id", f"{categoria}-{len(bases)}"))
        nombres.append(f"{categoria}|{llave}")
        bases.append(f"{categoria}:{llave}")
        categorias.append(categoria)
    return equis, yes, nombres, bases, categorias


def _dividir_por_base(bases, semilla, fraccion_validacion=0.15):
    """Split entrenamiento/validacion agrupado por [base] (sin fuga)."""
    unicas = sorted(set(bases))
    rng = np.random.default_rng(semilla)
    orden = rng.permutation(len(unicas))
    corte = max(1, int(round(len(unicas) * fraccion_validacion))) if len(unicas) > 1 else 0
    validacion = {unicas[i] for i in orden[:corte]}
    return np.array(
        ["validacion" if b in validacion else "entrenamiento" for b in bases]
    )


# --------------------------------------------------------------------------
# Fase 2: preprocesar
# --------------------------------------------------------------------------
def preprocesar(fuente, cache, lado, margen, por_categoria, min_tinta, semilla, forzar,
                categorias=None):
    salida = ruta_pares(cache, lado)
    manifiesto_ruta = ruta_manifiesto(cache, lado)
    if os.path.exists(salida) and not forzar:
        print(f"[preprocesar] ya esta: {salida} (usa --forzar para rehacerlo)")
        return salida

    categorias = categorias or fuente["categorias"]
    rng = np.random.default_rng(semilla)
    kernels = Kernels()
    equis, yes, nombres, bases, categs = [], [], [], [], []
    t0 = time.time()
    for categoria in categorias:
        ruta = ruta_categoria(cache, categoria)
        if not os.path.exists(ruta):
            raise SystemExit(f"falta {ruta}; corre primero `descargar`")
        dibujos = _leer_dibujos(ruta, por_categoria)
        ex, ye, no, ba, ca = _generar_pares(
            dibujos, categoria, lado, margen, min_tinta, rng, kernels
        )
        equis += ex
        yes += ye
        nombres += no
        bases += ba
        categs += ca
        print(f"[preprocesar] {categoria:10s} {len(ex):4d} pares "
              f"({time.time() - t0:.0f} s)")

    if not equis:
        raise SystemExit("no se genero ningun par; revisa las descargas de ndjson")

    x = np.stack(equis)
    y = np.stack(yes)
    split = _dividir_por_base(bases, semilla)
    os.makedirs(os.path.dirname(salida), exist_ok=True)
    np.savez_compressed(
        salida,
        x=x,
        y=y,
        nombres=np.array(nombres),
        bases=np.array(bases),
        categorias=np.array(categs),
        split=split,
    )

    manifiesto = {
        "generado": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "plan": "B (licencia comercial)",
        "fuente": fuente["id"],
        "licencia": fuente.get("licencia"),
        "licencia_url": fuente.get("licencia_url"),
        "comercial": fuente.get("comercial", True),
        "obliga_opensource": fuente.get("obliga_opensource", False),
        "atribucion": fuente.get("atribucion"),
        "categorias": list(categorias),
        "lado": lado,
        "margen": margen,
        "por_categoria": por_categoria,
        "semilla": semilla,
        "total_pares": int(len(x)),
        "pares_entrenamiento": int((split == "entrenamiento").sum()),
        "pares_validacion": int((split == "validacion").sum()),
        "por_categoria": {c: int(sum(1 for v in categs if v == c))
                          for c in sorted(set(categs))},
        "formato_npz": {
            "x": "entrada en grises uint8 [0,255] (tinta oscura)",
            "y": "objetivo binario uint8 {0,1} (1=tinta, esqueleto de 1 px)",
            "nombres": "categoria|key_id",
            "split": "entrenamiento|validacion (agrupado por key_id, sin fuga)",
        },
    }
    with open(manifiesto_ruta, "w", encoding="utf-8") as f:
        json.dump(manifiesto, f, ensure_ascii=False, indent=2)

    peso = os.path.getsize(salida)
    print(f"[preprocesar] guardado: {salida} ({peso / 1e6:.1f} MB)")
    print(f"[preprocesar] manifiesto: {manifiesto_ruta}")
    return salida


def cmd_preprocesar(args):
    registro = cargar_registro()
    fuente = fuente_por_id(registro, args.fuente or registro["fuente_por_defecto"])
    categorias = None
    if args.categorias:
        categorias = [c.strip() for c in args.categorias.split(",") if c.strip()]
    preprocesar(fuente, args.cache, args.lado, args.margen, args.por_categoria,
                args.min_tinta, args.semilla, args.forzar, categorias)


# --------------------------------------------------------------------------
# estado / autoprueba
# --------------------------------------------------------------------------
def cmd_estado(args):
    print(f"cache: {args.cache}")
    crudos = os.path.join(args.cache, "crudos", "quickdraw")
    if os.path.isdir(crudos):
        for nombre in sorted(os.listdir(crudos)):
            ruta = os.path.join(crudos, nombre)
            print(f"  crudo     {nombre:24s} {os.path.getsize(ruta):>12d} bytes")
    procesado = os.path.join(args.cache, "procesado")
    if os.path.isdir(procesado):
        for nombre in sorted(os.listdir(procesado)):
            ruta = os.path.join(procesado, nombre)
            print(f"  procesado {nombre:24s} {os.path.getsize(ruta):>12d} bytes")
    if not os.path.isdir(crudos) and not os.path.isdir(procesado):
        print("  (vacio)")


def cmd_autoprueba(args):
    """Prueba el rasterizado y el repase sobre un dibujo de ejemplo (sin red)."""
    dibujo = {
        "key_id": "0001",
        "word": "prueba",
        "drawing": [
            [[10, 60, 110, 160, 210], [120, 60, 40, 60, 120]],
            [[10, 210], [120, 120]],
            [[40, 180], [180, 180]],
        ],
    }
    equis, yes, nombres, bases, categs = _generar_pares(
        [dibujo], "prueba", LADO_DEF, MARGEN_DEF, 4,
        np.random.default_rng(1), Kernels(),
    )
    if len(equis) != 1:
        print("FALLO autoprueba: no se genero el par de ejemplo")
        raise SystemExit(1)
    x, y = equis[0], yes[0]
    tinta_objetivo = int(y.sum())
    tinta_entrada = int((x < 200).sum())
    if tinta_objetivo < 4:
        print("FALLO autoprueba: el objetivo no tiene tinta")
        raise SystemExit(1)
    if tinta_entrada <= tinta_objetivo:
        print(f"FALLO autoprueba: el repase ({tinta_entrada}) deberia ser mas "
              f"grueso que el objetivo ({tinta_objetivo})")
        raise SystemExit(1)
    if x.dtype != np.uint8 or y.dtype != np.uint8 or y.max() != 1:
        print("FALLO autoprueba: tipos del npz")
        raise SystemExit(1)
    # El objetivo de Bresenham es conexo: sin filas/columnas vacias dentro del bbox.
    ys, xs = np.where(y > 0)
    if not (ys.min() <= ys.max() and xs.min() <= xs.max()):
        print("FALLO autoprueba: bbox invalido")
        raise SystemExit(1)
    print(f"OK autoprueba del Plan B (objetivo {tinta_objetivo} px, "
          f"repase {tinta_entrada} px)")


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def argumentos():
    p = argparse.ArgumentParser(
        description="Plan B: dataset comercial (Quick, Draw!) para limpiar trazos."
    )
    p.add_argument("--cache", default=CACHE_DEF, help="carpeta de cache")
    sub = p.add_subparsers(dest="fase", required=True)

    d = sub.add_parser("descargar", help="baja la cabecera de cada categoria")
    d.add_argument("--fuente", default=None, help="id del registro")
    d.add_argument("--categorias", default=None, help="lista separada por comas (override)")
    d.add_argument("--forzar", action="store_true", help="rehacer aunque ya exista")
    d.set_defaults(func=cmd_descargar)

    pr = sub.add_parser("preprocesar", help="fabrica los pares y guarda pares.npz")
    pr.add_argument("--fuente", default=None, help="id del registro")
    pr.add_argument("--categorias", default=None, help="lista separada por comas (override)")
    pr.add_argument("--lado", type=int, default=LADO_DEF, help="lado de la hoja en px")
    pr.add_argument("--margen", type=int, default=MARGEN_DEF, help="margen en px")
    pr.add_argument("--por-categoria", dest="por_categoria", type=int,
                    default=POR_CATEGORIA_DEF, help="dibujos maximos por categoria")
    pr.add_argument("--min-tinta", dest="min_tinta", type=int, default=12,
                    help="pixeles minimos de tinta del objetivo")
    pr.add_argument("--semilla", type=int, default=1, help="semilla del split y del repase")
    pr.add_argument("--forzar", action="store_true", help="rehacer aunque ya exista")
    pr.set_defaults(func=cmd_preprocesar)

    t = sub.add_parser("todo", help="descargar + preprocesar")
    t.add_argument("--fuente", default=None)
    t.add_argument("--categorias", default=None)
    t.add_argument("--lado", type=int, default=LADO_DEF)
    t.add_argument("--margen", type=int, default=MARGEN_DEF)
    t.add_argument("--por-categoria", dest="por_categoria", type=int,
                   default=POR_CATEGORIA_DEF)
    t.add_argument("--min-tinta", dest="min_tinta", type=int, default=12)
    t.add_argument("--semilla", type=int, default=1)
    t.add_argument("--forzar", action="store_true")

    e = sub.add_parser("estado", help="muestra el cache")
    e.set_defaults(func=cmd_estado)

    a = sub.add_parser("autoprueba", help="prueba el pipeline sin red")
    a.set_defaults(func=cmd_autoprueba)
    return p


def main():
    args = argumentos().parse_args()
    if args.fase == "todo":
        cmd_descargar(args)
        cmd_preprocesar(args)
    else:
        args.func(args)


if __name__ == "__main__":
    main()
