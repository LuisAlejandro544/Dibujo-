#!/usr/bin/env python3
"""Paso A: preparar el dataset real (SketchBench) para limpiar trazos.

Baja el dataset real, empareja cada boceto repasado con su version limpiada y
guarda un `.npz` cacheado y determinista que consume el Paso B (entrenamiento,
`tools/entrenar_limpiar_trazos.py --pares ...`).

Esta fase esta pensada para NO exceder el corte de ~1 h de CI: `descargar` y
`preprocesar` son independientes y reanudables, y el resultado se cachea para
reusarse en corridas posteriores.

Fases:
    descargar     baja los .zip al cache (reanuda por rangos; verifica tamano y sha256)
    preprocesar   empareja rough/pixel + gt/pixel -> pares.npz + manifiesto.json
    todo          descargar + preprocesar
    estado        muestra que hay en el cache
    autoprueba    corre el emparejamiento sobre nombres de ejemplo (sin red)

Uso (desde la raiz del repo, con `numpy` + `pillow` instalados):

    python3 tools/preparar_dataset_limpiar_trazos.py descargar
    python3 tools/preparar_dataset_limpiar_trazos.py preprocesar --lado 512
    python3 tools/preparar_dataset_limpiar_trazos.py todo
    python3 tools/preparar_dataset_limpiar_trazos.py estado
"""

import argparse
import hashlib
import io
import json
import os
import ssl
import sys
import time
import urllib.request
import zipfile

import numpy as np

try:
    from PIL import Image
except ImportError:  # pragma: no cover - solo para dar un error claro
    Image = None

# Raiz del repo (este archivo vive en <raiz>/tools/).
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REGISTRO = os.path.join(RAIZ, "tools", "datasets_limpiar_trazos.json")
CACHE_DEF = os.path.join(RAIZ, ".cache", "limpiar_trazos")

# Lado del cache. El parche de entrenamiento sigue siendo 64x64; esto es la
# resolucion de las hojas completas cacheadas (de aca se recortan los parches).
LADO_DEF = 512
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


def sha256_archivo(ruta, trozo=1 << 20):
    h = hashlib.sha256()
    with open(ruta, "rb") as f:
        for bloque in iter(lambda: f.read(trozo), b""):
            h.update(bloque)
    return h.hexdigest()


def ruta_crudo(cache, fuente):
    return os.path.join(cache, "crudos", os.path.basename(fuente["url"]))


def ruta_pares(cache, lado, esqueleto):
    sufijo = "esq" if esqueleto else "linea"
    return os.path.join(cache, "procesado", f"pares_{sufijo}_lado{lado}.npz")


def ruta_manifiesto(cache, lado, esqueleto):
    sufijo = "esq" if esqueleto else "linea"
    return os.path.join(cache, "procesado", f"manifiesto_{sufijo}_lado{lado}.json")


# --------------------------------------------------------------------------
# Fase 1: descargar
# --------------------------------------------------------------------------
def descargar_archivo(url, destino, bytes_esperados, sha_esperado, inseguro, forzar=False):
    """Baja [url] a [destino], reanudable y verificado.

    Si el archivo ya existe con el tamano/hash correctos no hace nada. Si hay
    un `.parcial` lo reanuda con una cabecera Range.
    """
    os.makedirs(os.path.dirname(destino), exist_ok=True)

    if os.path.exists(destino) and not forzar:
        tam = os.path.getsize(destino)
        if (bytes_esperados is None or tam == bytes_esperados) and \
           (sha_esperado is None or sha256_archivo(destino) == sha_esperado):
            print(f"[descargar] ya esta: {destino} ({tam} bytes)")
            return destino

    parcial = destino + ".parcial"
    inicio = os.path.getsize(parcial) if os.path.exists(parcial) else 0

    cabeceras = {"User-Agent": AGENTE}
    if inicio:
        cabeceras["Range"] = f"bytes={inicio}-"

    contexto = ssl._create_unverified_context() if inseguro else None
    req = urllib.request.Request(url, headers=cabeceras)
    print(f"[descargar] {url} -> {destino} (desde {inicio} bytes)")

    with urllib.request.urlopen(req, context=contexto, timeout=120) as r:
        reanudado = inicio > 0 and getattr(r, "status", 200) == 206
        if not reanudado:
            inicio = 0
        modo = "ab" if reanudado else "wb"
        total = bytes_esperados or 0
        descargado = inicio
        with open(parcial, modo) as f:
            while True:
                bloque = r.read(1 << 20)
                if not bloque:
                    break
                f.write(bloque)
                descargado += len(bloque)
                if total:
                    pct = 100.0 * descargado / total
                    sys.stdout.write(f"\r[descargar] {pct:5.1f}% ({descargado}/{total} bytes)")
                    sys.stdout.flush()
        sys.stdout.write("\n")

    tam = os.path.getsize(parcial)
    if bytes_esperados is not None and tam != bytes_esperados:
        raise SystemExit(f"tamano inesperado: {tam} != {bytes_esperados}")

    if sha_esperado is not None:
        print("[descargar] calculando sha256...")
        obtenido = sha256_archivo(parcial)
        if obtenido != sha_esperado:
            os.remove(parcial)
            raise SystemExit(f"sha256 no coincide:\n  esperado {sha_esperado}\n  obtenido {obtenido}")

    os.replace(parcial, destino)
    print(f"[descargar] listo: {destino} ({tam} bytes)")
    return destino


def cmd_descargar(args):
    registro = cargar_registro()
    fuentes = registro["fuentes"]
    if args.fuente:
        fuentes = [fuente_por_id(registro, args.fuente)]
    elif not args.todo:
        # Por defecto, solo las fuentes obligatorias (sin `opcional`).
        fuentes = [f for f in fuentes if not f.get("opcional")]

    for fuente in fuentes:
        inseguro = args.inseguro or fuente.get("inseguro", False)
        descargar_archivo(
            fuente["url"],
            ruta_crudo(args.cache, fuente),
            fuente.get("bytes"),
            fuente.get("sha256"),
            inseguro,
            forzar=args.forzar,
        )


# --------------------------------------------------------------------------
# Emparejamiento (puro: nombres -> pares). Se prueba en `autoprueba`.
# --------------------------------------------------------------------------
def _dividir_nombre(nombre_archivo):
    """`<base 4 tokens>_<resto>` de un PNG de SketchBench."""
    tallo = nombre_archivo[:-4] if nombre_archivo.endswith(".png") else nombre_archivo
    tokens = tallo.split("_")
    base = "_".join(tokens[:4])
    resto = tokens[4:]
    return base, resto


def parsear_boceto(nombre_archivo, resoluciones):
    base, resto = _dividir_nombre(nombre_archivo)
    res = resto[-1] if resto and resto[-1] in resoluciones else ""
    variante = "_".join(resto[:-1]) if res else "_".join(resto)
    return base, variante, res


def parsear_objetivo(nombre_archivo, resoluciones):
    base, resto = _dividir_nombre(nombre_archivo)
    res = resto[-1] if resto and resto[-1] in resoluciones else ""
    nucleo = resto[:-1] if res else resto
    if len(nucleo) >= 2 and nucleo[-2] == "norm":
        variante = "norm_" + nucleo[-1]
        artista = "_".join(nucleo[:-2])
    elif nucleo:
        variante = nucleo[-1]
        artista = "_".join(nucleo[:-1])
    else:
        variante = ""
        artista = ""
    return base, artista, variante, res


def emparejar(nombres, estructura, resoluciones_deseadas=None):
    """Devuelve los pares (boceto, objetivo) a partir de los nombres del zip.

    Pairing de SketchBench: para cada objetivo `..._norm_cleaned[_res].png`
    busca el boceto del mismo `<base>` y `<res>`, prefiriendo la variante
    `norm_full` (escaneo en grises), luego `norm_rough`, `cb` y por ultimo el
    archivo tal cual.

    `resoluciones_deseadas` filtra el resultado ("full" es la resolucion
    nativa). Por defecto no filtra; el emparejamiento siempre parsea con el
    conjunto completo de resoluciones para no confundir sufijos.
    """
    resoluciones = set(estructura.get("resoluciones", ["500", "1000", "2000", ""]))
    prioridad = estructura.get("variantes", ["norm_full", "norm_rough", "cb", ""])
    pref_objetivo = estructura.get("variante_objetivo", "norm_cleaned")
    dir_bocetos = "Benchmark_Testset/rough/pixel/"
    dir_objetivos = "Benchmark_Testset/gt/pixel/"

    indice = {}
    for nombre in nombres:
        if not (nombre.startswith(dir_bocetos) and nombre.endswith(".png")):
            continue
        base, variante, res = parsear_boceto(os.path.basename(nombre), resoluciones)
        indice.setdefault((base, res), {})[variante] = nombre

    pares = []
    for nombre in nombres:
        if not (nombre.startswith(dir_objetivos) and nombre.endswith(".png")):
            continue
        base, artista, variante, res = parsear_objetivo(os.path.basename(nombre), resoluciones)
        if variante != pref_objetivo:
            continue
        etiqueta_res = res or "full"
        if resoluciones_deseadas is not None and etiqueta_res not in resoluciones_deseadas:
            continue
        candidatos = indice.get((base, res))
        if not candidatos:
            continue
        boceto = next((candidatos[v] for v in prioridad if v in candidatos), None)
        if boceto is None:
            continue
        pares.append({
            "boceto": boceto,
            "objetivo": nombre,
            "base": base,
            "artista": artista,
            "resolucion": etiqueta_res,
        })
    pares.sort(key=lambda p: (p["base"], p["resolucion"], p["artista"]))
    return pares


# --------------------------------------------------------------------------
# Fase 2: preprocesar
# --------------------------------------------------------------------------
def _a_gris(im):
    """Convierte a L componiendo la transparencia sobre blanco."""
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        fondo = Image.new("RGBA", im.size, (255, 255, 255, 255))
        im = Image.alpha_composite(fondo, im)
    return im.convert("L")


def _tamano_caja(w, h, lado):
    escala = lado / float(max(w, h))
    return max(1, int(round(w * escala))), max(1, int(round(h * escala)))


def _encajar(im, lado, caja):
    nw, nh = caja
    im = im.resize((nw, nh), Image.LANCZOS)
    lienzo = Image.new("L", (lado, lado), 255)
    lienzo.paste(im, ((lado - nw) // 2, (lado - nh) // 2))
    return np.asarray(lienzo, dtype=np.uint8)


def otsu(arr):
    """Umbral de Otsu (entero 0..255) para una imagen en grises."""
    hist = np.bincount(arr.ravel(), minlength=256).astype(np.float64)
    total = arr.size
    suma_total = float(np.dot(np.arange(256), hist))
    suma_fondo = 0.0
    peso_fondo = 0.0
    mejor = -1.0
    umbral = 128
    for t in range(256):
        peso_fondo += hist[t]
        if peso_fondo == 0:
            continue
        peso_frente = total - peso_fondo
        if peso_frente == 0:
            break
        suma_fondo += t * hist[t]
        media_fondo = suma_fondo / peso_fondo
        media_frente = (suma_total - suma_fondo) / peso_frente
        varianza = peso_fondo * peso_frente * (media_fondo - media_frente) ** 2
        if varianza > mejor:
            mejor = varianza
            umbral = t
    return umbral


def adelgazar_zhang_suen(binaria):
    """Adelgaza una mascara booleana (True=tinta) a 1 px (Zhang-Suen)."""
    p = binaria.astype(np.uint8)
    desplazamientos = [(-1, 0), (-1, 1), (0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1)]
    cambiado = True
    while cambiado:
        cambiado = False
        for paso in range(2):
            seq = [np.roll(np.roll(p, dy, 0), dx, 1) for dy, dx in desplazamientos]
            p2, p3, p4, p5, p6, p7, p8, p9 = seq
            b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
            a = np.zeros_like(p)
            ciclo = seq + [p2]
            for k in range(8):
                a += ((ciclo[k] == 0) & (ciclo[k + 1] == 1)).astype(np.uint8)
            cond = (p == 1) & (b >= 2) & (b <= 6) & (a == 1)
            if paso == 0:
                cond &= (p2 * p4 * p6 == 0) & (p4 * p6 * p8 == 0)
            else:
                cond &= (p2 * p4 * p8 == 0) & (p2 * p6 * p8 == 0)
            if cond.any():
                p[cond] = 0
                cambiado = True
    return p.astype(bool)


def _dividir_por_base(bases, semilla, fraccion_validacion=0.15):
    unicas = sorted(set(bases))
    rng = np.random.default_rng(semilla)
    orden = rng.permutation(len(unicas))
    corte = max(1, int(round(len(unicas) * fraccion_validacion))) if len(unicas) > 1 else 0
    validacion = {unicas[i] for i in orden[:corte]}
    return np.array(
        ["validacion" if b in validacion else "entrenamiento" for b in bases]
    )


def preprocesar(fuente, cache, lado, esqueleto, umbral, semilla, forzar,
                resoluciones_deseadas=None):
    zip_ruta = ruta_crudo(cache, fuente)
    if not os.path.exists(zip_ruta):
        raise SystemExit(f"falta el zip crudo {zip_ruta}; corre primero `descargar`")

    salida = ruta_pares(cache, lado, esqueleto)
    manifiesto_ruta = ruta_manifiesto(cache, lado, esqueleto)
    if os.path.exists(salida) and not forzar:
        print(f"[preprocesar] ya esta: {salida} (usa --forzar para rehacerlo)")
        return salida

    estructura = fuente.get("estructura", {})
    estructura.setdefault("variante_objetivo", "norm_cleaned")
    with zipfile.ZipFile(zip_ruta) as zf:
        pares = emparejar(zf.namelist(), estructura, resoluciones_deseadas)
        if not pares:
            raise SystemExit("no se encontro ningun par; revisa la estructura del zip")
        print(f"[preprocesar] {len(pares)} pares; lado={lado}; esqueleto={esqueleto}; "
              f"umbral={umbral}")

        equis = np.empty((len(pares), lado, lado), np.uint8)
        ye = np.empty((len(pares), lado, lado), np.uint8)
        nombres = []
        bases = []
        resoluciones = []
        umbrales = []
        t0 = time.time()
        for i, par in enumerate(pares):
            with zf.open(par["boceto"]) as fh:
                im_boceto = _a_gris(Image.open(io.BytesIO(fh.read())))
            with zf.open(par["objetivo"]) as fh:
                im_objetivo = _a_gris(Image.open(io.BytesIO(fh.read())))

            caja = _tamano_caja(*im_boceto.size, lado)
            equis[i] = _encajar(im_boceto, lado, caja)
            objetivo = _encajar(im_objetivo, lado, caja)
            corte = otsu(objetivo) if umbral == "auto" else int(umbral)
            binaria = objetivo < corte
            if esqueleto:
                binaria = adelgazar_zhang_suen(binaria)
            ye[i] = binaria.astype(np.uint8)

            nombres.append(f"{par['base']}|{par['artista']}|{par['resolucion']}")
            bases.append(par["base"])
            resoluciones.append(par["resolucion"])
            umbrales.append(corte)
            if (i + 1) % 100 == 0 or i + 1 == len(pares):
                ritmo = (i + 1) / max(time.time() - t0, 1e-6)
                print(f"\r[preprocesar] {i + 1}/{len(pares)} ({ritmo:.1f} pares/s)", end="")
        print()

    split = _dividir_por_base(bases, semilla)
    os.makedirs(os.path.dirname(salida), exist_ok=True)
    np.savez_compressed(
        salida,
        x=equis,
        y=ye,
        nombres=np.array(nombres),
        bases=np.array(bases),
        resoluciones=np.array(resoluciones),
        split=split,
    )

    manifiesto = {
        "generado": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "fuente": fuente["id"],
        "url": fuente["url"],
        "sha256": fuente.get("sha256"),
        "licencia": fuente.get("licencia"),
        "lado": lado,
        "esqueleto": esqueleto,
        "umbral": umbral,
        "umbral_medio": float(np.mean(umbrales)),
        "resoluciones": sorted(set(resoluciones)),
        "semilla": semilla,
        "total_pares": len(pares),
        "pares_entrenamiento": int((split == "entrenamiento").sum()),
        "pares_validacion": int((split == "validacion").sum()),
        "bases_unicas": len(set(bases)),
        "por_resolucion": {r: int(sum(1 for x in resoluciones if x == r))
                           for r in sorted(set(resoluciones))},
        "formato_npz": {
            "x": "entrada en grises uint8 [0,255] (tinta oscura)",
            "y": "objetivo binario uint8 {0,1} (1=tinta)",
            "nombres": "base|artista|resolucion",
            "split": "entrenamiento|validacion (agrupado por base, sin fuga)",
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
    deseadas = None
    if args.resoluciones and args.resoluciones.strip().lower() != "todas":
        deseadas = {r.strip() for r in args.resoluciones.split(",") if r.strip()}
    preprocesar(fuente, args.cache, args.lado, args.esqueleto, args.umbral,
                args.semilla, args.forzar, deseadas)


# --------------------------------------------------------------------------
# estado / autoprueba
# --------------------------------------------------------------------------
def cmd_estado(args):
    print(f"cache: {args.cache}")
    crudos = os.path.join(args.cache, "crudos")
    if os.path.isdir(crudos):
        for nombre in sorted(os.listdir(crudos)):
            ruta = os.path.join(crudos, nombre)
            print(f"  crudo    {nombre:40s} {os.path.getsize(ruta):>12d} bytes")
    procesado = os.path.join(args.cache, "procesado")
    if os.path.isdir(procesado):
        for nombre in sorted(os.listdir(procesado)):
            ruta = os.path.join(procesado, nombre)
            print(f"  procesado {nombre:40s} {os.path.getsize(ruta):>12d} bytes")
    if not os.path.isdir(crudos) and not os.path.isdir(procesado):
        print("  (vacio)")


def cmd_autoprueba(args):
    estructura = {
        "resoluciones": ["500", "1000", "2000", ""],
        "variantes": ["norm_full", "norm_rough", "cb", ""],
        "variante_objetivo": "norm_cleaned",
    }
    nombres = [
        "Benchmark_Testset/rough/pixel/Art_freeform_AG_02.png",
        "Benchmark_Testset/rough/pixel/Art_freeform_AG_02_norm_full_500.png",
        "Benchmark_Testset/rough/pixel/Art_freeform_AG_02_norm_rough_500.png",
        "Benchmark_Testset/rough/pixel/Art_freeform_AG_02_norm_full_1000.png",
        "Benchmark_Testset/gt/pixel/Art_freeform_AG_02_Branislav Mirkovic_norm_cleaned_500.png",
        "Benchmark_Testset/gt/pixel/Art_freeform_AG_02_Diego Barrionuevo_norm_cleaned_500.png",
        "Benchmark_Testset/gt/pixel/Art_freeform_AG_02_Branislav Mirkovic_norm_cleaned_1000.png",
        "Benchmark_Testset/gt/pixel/Art_freeform_AG_02_Branislav Mirkovic_norm_full_500.png",
        "Benchmark_Testset/gt/pixel/Art_freeform_AG_02_Branislav Mirkovic_norm_cleaned.png",
        "Benchmark_Testset/rough/pixel/Ind_product_AS_11_cb_500.png",
        "Benchmark_Testset/gt/pixel/Ind_product_AS_11_Liliya Larsen_norm_cleaned_500.png",
        "Benchmark_Testset/rough/pixel/Ignorado_sin_base_valida.png",
    ]
    pares = emparejar(nombres, estructura)
    claves = sorted((p["resolucion"], p["boceto"].split("/")[-1]) for p in pares)
    esperado = sorted([
        ("500", "Art_freeform_AG_02_norm_full_500.png"),
        ("500", "Art_freeform_AG_02_norm_full_500.png"),
        ("500", "Ind_product_AS_11_cb_500.png"),
        ("1000", "Art_freeform_AG_02_norm_full_1000.png"),
        ("full", "Art_freeform_AG_02.png"),
    ])
    if claves != esperado:
        print("FALLO autoprueba de emparejamiento")
        print("  obtenido:", claves)
        print("  esperado:", esperado)
        raise SystemExit(1)

    # Comprueba que `norm_cleaned` no cae en `norm_full`.
    assert all("_norm_cleaned" in p["objetivo"] for p in pares)
    print(f"OK autoprueba de emparejamiento ({len(pares)} pares como se esperaba)")


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def argumentos():
    p = argparse.ArgumentParser(description="Paso A: dataset real para limpiar trazos.")
    p.add_argument("--cache", default=CACHE_DEF, help="carpeta de cache")
    sub = p.add_subparsers(dest="fase", required=True)

    d = sub.add_parser("descargar", help="baja los .zip al cache")
    d.add_argument("--fuente", default=None, help="id del registro (si se omite, las obligatorias)")
    d.add_argument("--todo", action="store_true", help="incluye tambien las fuentes opcionales")
    d.add_argument("--inseguro", action="store_true", help="no verificar TLS (override)")
    d.add_argument("--forzar", action="store_true", help="rehacer aunque ya exista")
    d.set_defaults(func=cmd_descargar)

    pr = sub.add_parser("preprocesar", help="empareja y guarda pares.npz")
    pr.add_argument("--fuente", default=None, help="id del registro")
    pr.add_argument("--lado", type=int, default=LADO_DEF, help="lado del cache en px")
    pr.add_argument("--esqueleto", dest="esqueleto", action="store_true",
                    help="adelgaza el objetivo a 1 px (contrato del modelo de la app)")
    pr.add_argument("--sin-esqueleto", dest="esqueleto", action="store_false",
                    help="guarda la linea limpia tal cual (binaria)")
    pr.set_defaults(esqueleto=True)
    pr.add_argument("--umbral", default="auto", help="'auto' (Otsu) o un entero 0..255")
    pr.add_argument("--resoluciones", default="full",
                    help="'full' (nativa, por defecto), 'todas' o lista 'full,1000,500'")
    pr.add_argument("--semilla", type=int, default=1, help="semilla del split por base")
    pr.add_argument("--forzar", action="store_true", help="rehacer aunque ya exista")
    pr.set_defaults(func=cmd_preprocesar)

    t = sub.add_parser("todo", help="descargar + preprocesar")
    t.add_argument("--fuente", default=None)
    t.add_argument("--lado", type=int, default=LADO_DEF)
    t.add_argument("--esqueleto", dest="esqueleto", action="store_true")
    t.add_argument("--sin-esqueleto", dest="esqueleto", action="store_false")
    t.set_defaults(esqueleto=True)
    t.add_argument("--umbral", default="auto")
    t.add_argument("--resoluciones", default="full")
    t.add_argument("--semilla", type=int, default=1)
    t.add_argument("--inseguro", action="store_true")
    t.add_argument("--forzar", action="store_true")

    e = sub.add_parser("estado", help="muestra el cache")
    e.set_defaults(func=cmd_estado)

    a = sub.add_parser("autoprueba", help="prueba el emparejamiento sin red")
    a.set_defaults(func=cmd_autoprueba)
    return p, sub


def main():
    if Image is None:
        raise SystemExit("falta Pillow: pip install pillow")
    parser, sub = argumentos()
    args = parser.parse_args()

    if args.fase == "todo":
        args.todo = False
        cmd_descargar(args)
        cmd_preprocesar(args)
    else:
        args.func(args)


if __name__ == "__main__":
    main()
