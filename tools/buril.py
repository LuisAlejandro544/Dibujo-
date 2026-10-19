"""Entrena un modelo chico que "limpia" trazos repasados.

Entrada: parche 64x64x1 con la tinta gruesa/irregular (varios repases).
Salida:  parche 64x64x1 con la línea limpia. El objetivo de entrenamiento es una
**banda** de 3 px (no una línea dura de 1 px, casi imposible de acertar píxel a
píxel para una convolución): la app vuelve a adelgazar con Zhang-Suen en el
móvil, así que el esqueleto final sigue siendo de 1 px. La banda le da margen a
la red y saca el desbalance extremo del BCE.

**Contrato de polaridad (crítico).** Tanto la app (`LimpiadorTrazosIa`) como el
benchmark le pasan al modelo `tinta = 1, fondo = 0`. Los `.npz` de los planes A/B
y las imágenes reales guardan la tinta **oscura sobre fondo claro**, así que acá
se normalizan a `tinta = 1` antes de entrenar. Si no, el modelo aprende una
polaridad y en el móvil recibe la opuesta: sale ruido/manchas. La función
`_a_tinta` (abajo) hace esa normalización sin importar la polaridad de origen.

Datos:
  - Sintéticos (curvas Bezier gruesas + repases -> curva fina), sin licencias.
  - Reales opcionales: pares (entrada, objetivo) en disco, para cerrar el
    "domain gap" entre la Bezier sintética y el trazo escaneado/repasado real.
    El estándar de la industria es SketchBench (Yan et al., SIGGRAPH Asia 2020)
    y el set de Simo-Serra et al. (SIGGRAPH 2016); revisá la licencia de cada
    uno (la mayoría CC, algunos solo uso académico). Pasá `--reales <carpeta>`
    con subcarpetas `entrada/` y `objetivo/` (o `input/`/`output/`) y archivos
    con el mismo nombre base en ambas.

    Para un modelo que se pueda vender usá el **Plan B** (licencia comercial):
    `tools/preparar_dataset_limpiar_trazos_planb.py` arma pares a partir de
    Quick, Draw! (CC BY 4.0) y su `.npz` se pasa igual a `--pares`.

Además se aumenta el set de entrenamiento con transformaciones que no mueven la
etiqueta (volteos, giros de 90 grados, desplazamientos) y de forma/fotometría
solo sobre la entrada (difuminado, engrosado/adelgazado, contraste, ruido) para
simular más grosores de repase. `--aumentos N` agrega N copias aumentadas por
muestra (por defecto 3).

**Memoria (arreglo de OOM).** La aumentación se aplica **al vuelo** con
`tf.data` (una pasada extra por `--aumentos`, en `Dataset.map` con
`num_parallel_calls`), no materializando el set completo en RAM. Antes
`aumentar_conjunto` creaba `1 + aumentos` copias en float32 y las concatenaba:
con hojas de 512 px y muchos parches eso pasaba de 10 GB y el proceso moría con
`SIGKILL: 9` (el OOM killer del kernel), no por el tamaño del modelo.

La **pérdida** es BCE + Dice + **clDice** (Shit et al., CVPR'21): clDice es
diferenciable (esqueleto morfológico blando) y empuja a preservar la topología
de la línea, no sólo la superposición.

**Entrenamiento rápido y con pulido (por defecto).** El cuello de botella era el
número de pasos, no el peso del modelo, así que el schedule arranca rápido y
termina fino:

- **Fase rápida** (`--fase-rapida`, 75 % de las épocas): lote grande (`--lote`,
  64) y LR alto con **calentamiento** lineal (`--calentamiento`, 2 épocas) y
  luego **coseno** hasta `--alpha-fase-rapida`·`--lr`. Lote grande = menos pasos
  por época y GPU mejor aprovechada.
- **Fase de pulido** (25 % restante): lote chico (`--lote-final`, 32) con LR ya
  bajo (coseno hasta `--alpha-final`), que da más pasos y refina detalles sin
  mover el resultado brusco. El total de épocas es `--epocas` (60).

En GPU se usa **precisión mixta** float16 (`--precision auto`) y, si se puede,
**XLA** (`--xla auto`), que aceleran sin cambiar la calidad (el resultado se
exporta float16 igual). El `clDice` **no** se pide además como métrica: ya está
en la pérdida y recalcularlo duplicaba su costo.

**Puntos de control.** Se guarda `ultimo.weights.h5` en cada época
(`--checkpoints`) y `mejor.weights.h5` con el mejor `val_loss`; con `--reanudar`
se continúa desde donde quedó y **se exporta el mejor**, no el último. Esto es
lo que permite sobrevivir a un corte del entorno (Kaggle/Colab/Actions).

El modelo (mini U-Net 32/64/128, ~0,9 MB en float16 y ~466k parámetros) se
exporta a .tflite (float16) para correrlo en el móvil con LiteRT; es más ancho
que la v1 (24/32/48, ~100 KB) para tener más capacidad sin dejar de ser chico.

Uso (desde la raíz del repo, con `tensorflow-cpu` + `numpy` instalados):

    python3 tools/buril.py
    python3 tools/buril.py --pares .cache/limpiar_trazos/procesado/pares_esq_lado512.npz
    python3 tools/buril.py --reales ~/datos/sketchbench
    python3 tools/buril.py --epocas 30 --aumentos 2

`--pares` consume el cache de un plan de dataset (`tools/preparar_dataset_limpiar_trazos.py`
para SketchBench, `tools/preparar_dataset_limpiar_trazos_planb.py` para Quick, Draw!):
hojas completas emparejadas (boceto -> limpiado) de las que se recortan parches
64x64 con tinta, separando entrenamiento/validacion por boceto.

Al final exporta un `.tflite` float16 (apto para el delegado GPU) y evalua IoU y
PSNR sobre validacion con el modelo ya exportado, guardando un manifiesto `.json`
con las metricas y la atribucion del dataset.

Escribe `app/src/main/assets/modelos/limpiar_trazos.tflite` (y su `.json`). Esa
ruta esta en `.gitignore`: el modelo NO se versiona para no engordar el repo.

La via recomendada es **Kaggle** (`tools/buril_kaggle.ipynb`, GPU T4 x2 gratis y
*Save & Run All* en segundo plano, sin corte por inactividad): es un driver fino
que llama a este script y publica el `.tflite` en la release `modelo-buril`;
`android-apk.yml` la baja y la inyecta en los assets antes de armar el APK.
**Google Colab** (`tools/buril_colab.ipynb`) queda para **probar otros modelos** y
experimentos cortos; como respaldo, el workflow
`entrenar-limpiar-trazos-planb.yml` entrena en Actions (CPU) y publica en la
misma release. `--nombre` / `--version` quedan en el manifiesto (por defecto
`Buril v1.0.0-experimental.1a`).
"""

import argparse
import json
import os
import numpy as np
import tensorflow as tf

# Raíz del repo (este archivo vive en <raíz>/tools/).
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Lado del parche. Debe coincidir con `LimpiadorTrazosIa.LADO` en la app.
LADO = 64

yy, xx = np.mgrid[0:LADO, 0:LADO].astype(np.float32)


def bezier(cp, n=180):
    cp = np.asarray(cp, dtype=np.float32)
    t = np.linspace(0.0, 1.0, n, dtype=np.float32)[:, None]
    u = 1.0 - t
    pts = (
        (u ** 3) * cp[0]
        + 3 * (u ** 2) * t * cp[1]
        + 3 * u * (t ** 2) * cp[2]
        + (t ** 3) * cp[3]
    )
    return pts


def estampar(mask, pts, radio, cobertura_min=0.0):
    r = float(radio)
    for (px, py) in pts:
        d = np.sqrt((xx - px) ** 2 + (yy - py) ** 2)
        c = np.clip(0.5 + r - d, 0.0, 1.0)
        np.maximum(mask, c, out=mask)
    return mask


def linea_1px(mask, pts):
    """Dibuja la polilínea [pts] como esqueleto de 1 px (vecino más próximo).

    Se usa para el objetivo sintético: igual que el Plan B (Bresenham de 1 px),
    la etiqueta es una línea dura de 1 px, no un trazo antialias de 2-3 px.
    """
    for (px, py) in pts:
        ix, iy = int(round(float(px))), int(round(float(py)))
        if 0 <= ix < LADO and 0 <= iy < LADO:
            mask[iy, ix] = 1.0
    return mask


def curva_base(rng, margen=10.0):
    return np.array(
        [
            [rng.uniform(margen, LADO - margen), rng.uniform(margen, LADO - margen)],
            [rng.uniform(0, LADO), rng.uniform(0, LADO)],
            [rng.uniform(0, LADO), rng.uniform(0, LADO)],
            [rng.uniform(margen, LADO - margen), rng.uniform(margen, LADO - margen)],
        ],
        dtype=np.float32,
    )


def muestra(rng):
    cp = curva_base(rng)
    pts_fina = bezier(cp)
    # Objetivo duro de 1 px (mismo contrato que el .npz del Plan B).
    objetivo = linea_1px(np.zeros((LADO, LADO), np.float32), pts_fina)

    entrada = np.zeros((LADO, LADO), np.float32)
    # Repase principal grueso.
    estampar(entrada, pts_fina, rng.uniform(1.6, 5.0))
    # Repases extra paralelos (el gesto de repasar la misma línea).
    for _ in range(rng.integers(0, 3)):
        # Desplaza los puntos de control perpendicular/aleatoriamente.
        cp2 = cp + rng.uniform(-3.0, 3.0, size=cp.shape).astype(np.float32)
        estampar(entrada, bezier(cp2), rng.uniform(1.2, 4.0))
    # Manchas sueltas (ruido de repase).
    for _ in range(rng.integers(0, 3)):
        idx = rng.integers(0, len(pts_fina))
        px, py = pts_fina[idx] + rng.uniform(-4, 4, size=2)
        estampar(entrada, np.array([[px, py]], np.float32), rng.uniform(1.0, 3.0))

    return entrada[..., None], objetivo[..., None]


def _vecinos(v):
    return [
        np.roll(np.roll(v, dy, axis=0), dx, axis=1)
        for dy in (-1, 0, 1)
        for dx in (-1, 0, 1)
    ]


def _difuminar(v):
    return sum(_vecinos(v)) / 9.0


def _engrosar(v):
    return np.maximum.reduce(_vecinos(v))


def _adelgazar(v):
    return np.minimum.reduce(_vecinos(v))


def _dilatar(v):
    """Dilatación 3x3 (máximo) por las dos dimensiones espaciales.

    A diferencia de `_engrosar` (que asume una sola muestra 2D), respeta el lote:
    para `(N, H, W, 1)` dilata H y W y deja N intacto; para `(H, W)` dilata todo.
    """
    ejes = (-3, -2) if (v.ndim >= 3 and v.shape[-1] == 1) else (-2, -1)
    salida = v
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            corrido = np.roll(np.roll(v, dy, axis=ejes[0]), dx, axis=ejes[1])
            salida = np.maximum(salida, corrido)
    return salida


def banda(mask, radio=1):
    """Ensancha el objetivo a una banda de [2*radio+1] px (la app adelgaza igual).

    Una etiqueta de 1 px es casi imposible de acertar píxel a píxel con una
    convolución y empuja a la red a devolver manchas; una banda limpia le da
    margen y `LimpiadorTrazosIa` vuelve a adelgazar con Zhang-Suen en el móvil,
    así que el esqueleto final sigue siendo de 1 px. `radio=0` deja el 1 px de
    siempre. Acepta `(H, W)` o lotes `(N, H, W, 1)`.
    """
    v = np.asarray(mask, np.float32)
    for _ in range(int(radio)):
        v = _dilatar(v)
    return v


def aumentar(x, y, rng):
    """Aumenta un par (entrada, objetivo) sin mover la etiqueta.

    La geometría se aplica a ambos; la forma/fotometría solo a la entrada, que
    es lo que queremos que el modelo tolere (más grosores, más ruido).
    """
    # Geometría que preserva la etiqueta.
    if rng.random() < 0.5:
        x, y = x[:, ::-1], y[:, ::-1]
    if rng.random() < 0.5:
        x, y = x[::-1, :], y[::-1, :]
    giro = int(rng.integers(0, 4))
    if giro:
        x, y = np.rot90(x, giro), np.rot90(y, giro)
    dy, dx = int(rng.integers(-3, 4)), int(rng.integers(-3, 4))
    if dy or dx:
        x = np.roll(np.roll(x, dy, axis=0), dx, axis=1)
        y = np.roll(np.roll(y, dy, axis=0), dx, axis=1)

    # Forma/fotometría solo en la entrada.
    if rng.random() < 0.4:
        x = _difuminar(x)
    if rng.random() < 0.4:
        x = _engrosar(x)
    if rng.random() < 0.3:
        x = _adelgazar(x)
    if rng.random() < 0.5:
        gamma = rng.uniform(0.7, 1.4)
        x = np.clip(x ** gamma, 0.0, 1.0)
    if rng.random() < 0.5:
        x = np.clip(x + rng.normal(0.0, rng.uniform(0.0, 0.06), x.shape), 0.0, 1.0)

    return x.astype(np.float32), y.astype(np.float32)


def dataset(n, rng):
    x = np.empty((n, LADO, LADO, 1), np.float32)
    y = np.empty((n, LADO, LADO, 1), np.float32)
    for i in range(n):
        x[i], y[i] = muestra(np.random.default_rng(int(rng.integers(0, 2**31 - 1))))
    return x, y


def _leer_gris(ruta):
    """Lee una imagen como (LADO, LADO, 1) float32 en [0, 1]."""
    datos = tf.io.read_file(ruta)
    img = tf.io.decode_image(datos, channels=1, expand_animations=False)
    img = tf.image.resize(img, [LADO, LADO], method="area")
    return (tf.cast(img, tf.float32).numpy() / 255.0).reshape(LADO, LADO, 1)


def _a_tinta(gray):
    """Normaliza una imagen gris a tinta (1 = tinta) sin importar la polaridad.

    El fondo se toma como el valor mediano y la tinta como la desviación de ese
    fondo. Así da igual si el trazo es oscuro sobre blanco (planes A/B, la app,
    la mayoría de los escaneos) o claro sobre negro (algunos sets reales).
    """
    g = np.asarray(gray, np.float32)[..., 0]
    fondo = float(np.median(g))
    t = np.abs(g - fondo)
    mx = float(t.max())
    return (t / mx) if mx > 1e-6 else t


def cargar_reales(carpeta):
    """Carga pares reales (entrada, objetivo) de [carpeta].

    Espera subcarpetas `entrada`/`objetivo` (o `input`/`output`). El objetivo se
    binariza a 0/1 (esqueleto limpio); la entrada conserva grises.
    Devuelve (x, y) o `None` si la carpeta no tiene el formato esperado.
    """
    def _sub(*nombres):
        for n in nombres:
            p = os.path.join(carpeta, n)
            if os.path.isdir(p):
                return p
        return None

    dir_in = _sub("entrada", "input")
    dir_out = _sub("objetivo", "output")
    if dir_in is None or dir_out is None:
        print(f"[reales] {carpeta} no tiene subcarpetas entrada/objetivo; se ignora.")
        return None

    objetivos = {}
    for nombre in os.listdir(dir_out):
        base, _ = os.path.splitext(nombre)
        objetivos[base] = os.path.join(dir_out, nombre)

    xs, ys = [], []
    for nombre in sorted(os.listdir(dir_in)):
        base, _ = os.path.splitext(nombre)
        if base not in objetivos:
            continue
        # Entrada y objetivo al contrato del modelo: tinta = 1, fondo = 0.
        xs.append(_a_tinta(_leer_gris(os.path.join(dir_in, nombre)))[..., None])
        ys.append((_a_tinta(_leer_gris(objetivos[base])) >= 0.5).astype(np.float32)[..., None])
    if not xs:
        print(f"[reales] {carpeta}: no se encontraron pares con el mismo nombre.")
        return None
    print(f"[reales] {len(xs)} pares cargados de {carpeta}")
    return np.stack(xs), np.stack(ys)


def _aumentar_uno(x, y, semilla):
    """Aumenta un par con una semilla propia (seguro para `tf.data` paralelo)."""
    rng = np.random.default_rng(int(semilla))
    xa, ya = aumentar(x, y, rng)
    return xa.astype(np.float32), ya.astype(np.float32)


def _map_aumento(x, y):
    """Aplica `aumentar` a un par dentro de un `Dataset.map`."""
    semilla = tf.random.uniform([], 0, 2 ** 31 - 1, dtype=tf.int64)
    xa, ya = tf.numpy_function(
        _aumentar_uno, [x, y, semilla], [tf.float32, tf.float32]
    )
    xa.set_shape([LADO, LADO, 1])
    ya.set_shape([LADO, LADO, 1])
    return xa, ya


def flujo(x, y, lote, aumentos=0, entrenando=False, semilla=1):
    """Arma un `tf.data.Dataset` que aumenta al vuelo, sin copiar el set en RAM.

    Antes `aumentar_conjunto` materializaba `1 + aumentos` copias del set
    completo en float32 y las concatenaba: con hojas de 512 px y muchos parches
    eso pasaba de 10 GB y el proceso moría por OOM del kernel (`SIGKILL: 9`), no
    por el tamaño del modelo. Acá cada muestra se aumenta en `map` (con semilla
    por llamada), así la memoria es del orden del lote y no del set entero. La
    pasada extra por `--aumentos` mantiene la misma cantidad de ejemplos por
    época que antes.
    """
    ds = tf.data.Dataset.from_tensor_slices((x, y))
    if entrenando and aumentos > 0:
        ds = ds.repeat(int(aumentos) + 1)
        ds = ds.shuffle(min(int(x.shape[0]), 8192), seed=semilla,
                        reshuffle_each_iteration=True)
        ds = ds.map(_map_aumento, num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(lote).prefetch(tf.data.AUTOTUNE)


def cargar_pares_cache(ruta, lado=LADO, paso=32, max_por_imagen=16, min_tinta=4, semilla=1):
    """Extrae parches [lado]x[lado] del cache del Paso A (`--pares`).

    El cache guarda hojas completas (p. ej. 512x512) con `x` en grises
    [0,255] y `y` binaria {0,1}, mas un `split` agrupado por base (sin fuga).
    Aca se recortan parches con algo de tinta y se devuelven ya en el formato
    del entrenamiento (float32, canal 1).
    """
    datos = np.load(ruta)
    equis = datos["x"]
    ye = datos["y"]
    if "split" in datos:
        split = datos["split"]
    else:
        split = np.array(["entrenamiento"] * len(equis))
    rng = np.random.default_rng(semilla)

    acumulado = {"entrenamiento": ([], []), "validacion": ([], [])}
    alto, ancho = equis.shape[1], equis.shape[2]
    for i in range(len(equis)):
        candidatos = []
        for fy in range(0, alto - lado + 1, paso):
            for fx in range(0, ancho - lado + 1, paso):
                if int(ye[i, fy:fy + lado, fx:fx + lado].sum()) >= min_tinta:
                    candidatos.append((fy, fx))
        if not candidatos:
            continue
        rng.shuffle(candidatos)
        destino = str(split[i])
        for fy, fx in candidatos[:max_por_imagen]:
            xs, ys = acumulado[destino]
            # El cache guarda la tinta oscura (0) sobre fondo claro (255); el
            # modelo espera tinta = 1, fondo = 0 (igual que la app), así que se
            # invierte. Sin esto la app recibe la polaridad opuesta y sale ruido.
            xs.append(1.0 - equis[i, fy:fy + lado, fx:fx + lado, None].astype(np.float32) / 255.0)
            ys.append(ye[i, fy:fy + lado, fx:fx + lado, None].astype(np.float32))

    def _apila(par):
        xs, ys = par
        if not xs:
            return (np.empty((0, lado, lado, 1), np.float32),
                    np.empty((0, lado, lado, 1), np.float32))
        return np.stack(xs, 0), np.stack(ys, 0)

    xtr, ytr = _apila(acumulado["entrenamiento"])
    xva, yva = _apila(acumulado["validacion"])
    print(f"[pares] {len(xtr)} parches de entrenamiento y {len(xva)} de validacion de {ruta}")
    return xtr, ytr, xva, yva


def autoprueba():
    """Verifica el contrato de datos del pipeline (sin red ni modelo).

    Regresión concreta: el cache guarda la tinta oscura y la app le pasa al
    modelo `tinta = 1`; si el loader no invierte, el móvil recibe la polaridad
    opuesta y el resultado sale ruidoso. Se corre en cada entrenamiento.
    """
    import tempfile

    # 1. `_a_tinta` es invariante a la polaridad (oscuro sobre claro == claro
    #    sobre oscuro).
    g = np.linspace(0.0, 1.0, LADO * LADO, np.float32).reshape(LADO, LADO, 1)
    assert np.allclose(_a_tinta(g), _a_tinta(1.0 - g), atol=1e-6), "polaridad de _a_tinta"

    # 2. El objetivo sintético es una línea dura 0/1 (no un trazo antialias).
    objetivo = linea_1px(np.zeros((LADO, LADO), np.float32),
                         bezier(curva_base(np.random.default_rng(0))))
    assert set(np.unique(objetivo)).issubset({0.0, 1.0}), "objetivo sintético no binario"

    # 2b. La banda engorda el objetivo (3 px) pero sigue siendo binaria.
    banda3 = banda(objetivo, 1)
    assert set(np.unique(banda3)).issubset({0.0, 1.0}), "banda no binaria"
    assert banda3.sum() > objetivo.sum(), "la banda no engordó el objetivo"
    assert banda(objetivo, 0).sum() == objetivo.sum(), "radio 0 no debe cambiar el objetivo"

    # 3. El loader del cache invierte a tinta=1/fondo=0 (contrato de la app).
    x = np.full((1, LADO, LADO), 255, np.uint8)
    y = np.zeros((1, LADO, LADO), np.uint8)
    x[0, 10:30, 10] = 0      # tinta oscura en el cache
    y[0, 10:30, 10] = 1      # objetivo tinta=1
    with tempfile.TemporaryDirectory() as carpeta:
        ruta = os.path.join(carpeta, "pares.npz")
        np.savez(ruta, x=x, y=y, split=np.array(["entrenamiento"]))
        xtr, ytr, _, _ = cargar_pares_cache(ruta, lado=LADO, paso=1)
    assert xtr.shape[0] == 1, xtr.shape
    assert xtr[..., 0].max() == 1.0 and xtr[..., 0].min() == 0.0, "rango de la entrada"
    assert xtr[0, 20, 10, 0] == 1.0, "la tinta (oscura) no se invirtió a 1"
    assert xtr[0, 0, 0, 0] == 0.0, "el fondo deberia ser 0"
    assert ytr[0, 20, 10, 0] == 1.0, "el objetivo debe seguir siendo tinta=1"
    print("autoprueba OK: contrato de datos (entrada tinta=1/fondo=0, objetivo tinta=1)")


def modelo():
    """Mini U-Net de 3 niveles (32/64/128), ~466k parámetros (~0,9 MB float16).

    Es más ancha y profunda que la v1 (24/32/48, ~100 KB) para tener más
    capacidad, pero sigue siendo un modelo chico y rápido para el móvil. Entrada
    y salida siguen siendo `[1, 64, 64, 1]`, así que `LimpiadorTrazosIa` no
    cambia.
    """
    inp = tf.keras.Input((LADO, LADO, 1))
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(inp)
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    s1 = x
    x = tf.keras.layers.Conv2D(64, 3, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(64, 3, padding="same", activation="relu")(x)
    s2 = x
    x = tf.keras.layers.Conv2D(128, 3, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(128, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2DTranspose(64, 2, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Concatenate()([x, s2])
    x = tf.keras.layers.Conv2D(64, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(64, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2DTranspose(32, 2, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Concatenate()([x, s1])
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    # Salida en float32 aunque se entrene con precisión mixta (float16): la
    # sigmoide y la pérdida (BCE con log) necesitan rango float32 para no
    # subdesbordar ni perder precisión. Con `float32` no cambia nada.
    out = tf.keras.layers.Conv2D(1, 1, activation="sigmoid", dtype="float32")(x)
    return tf.keras.Model(inp, out)


def modelo_float32(entrenado):
    """Copia un modelo a float32 para poder exportarlo a `.tflite`.

    TFLite no convierte bien un modelo entrenado con política `mixed_float16`
    (los `Conv2D` en float16 quedan como ops TF y pedirían Flex). Como igual
    exportamos float16 con `TFLiteConverter` (cuantización), se clona el modelo
    bajo política float32 y se copian los pesos; el resultado es equivalente.
    """
    anterior = tf.keras.mixed_precision.global_policy()
    tf.keras.mixed_precision.set_global_policy("float32")
    try:
        copia = modelo()
    finally:
        tf.keras.mixed_precision.set_global_policy(anterior)
    copia.set_weights([np.asarray(w, np.float32) for w in entrenado.get_weights()])
    return copia


def dice(y_true, y_pred):
    y_true = tf.reshape(y_true, [-1])
    y_pred = tf.reshape(y_pred, [-1])
    inter = tf.reduce_sum(y_true * y_pred)
    return 1.0 - (2.0 * inter + 1.0) / (tf.reduce_sum(y_true) + tf.reduce_sum(y_pred) + 1.0)


def _erosion_soft(imagen):
    """Erosión morfológica blanda y diferenciable (max-pooling sobre el negativo)."""
    p1 = -tf.nn.max_pool2d(-imagen, (3, 1), (1, 1), "SAME")
    p2 = -tf.nn.max_pool2d(-imagen, (1, 3), (1, 1), "SAME")
    return tf.minimum(p1, p2)


def _dilatacion_soft(imagen):
    return tf.nn.max_pool2d(imagen, (3, 3), (1, 1), "SAME")


def _esqueletizar_soft(imagen, iteraciones=10):
    """Esqueleto morfológico blando (clDice, Shit et al., CVPR'21).

    Es diferenciable (erosión/dilatación con max-pooling), así que la pérdida
    puede "ver" la topología del esqueleto y no sólo la superposición de píxeles.
    """
    imagen1 = _dilatacion_soft(_erosion_soft(imagen))
    esqueleto = tf.nn.relu(imagen - imagen1)
    for _ in range(int(iteraciones)):
        imagen = _erosion_soft(imagen)
        imagen1 = _dilatacion_soft(_erosion_soft(imagen))
        delta = tf.nn.relu(imagen - imagen1)
        esqueleto = esqueleto + tf.nn.relu(delta - esqueleto * delta)
    return esqueleto


def cldice_loss(y_true, y_pred):
    """clDice (Shit et al., CVPR'21): preserva la topología del esqueleto.

    Con un objetivo fino el IoU castiga fuerte el grosor; clDice premia que la
    predicción siga la conectividad correcta. `1 - clDice` es la pérdida.
    """
    s_gt = _esqueletizar_soft(y_true)
    s_pred = _esqueletizar_soft(y_pred)
    t_prec = tf.reduce_sum(s_pred * y_true) / (tf.reduce_sum(s_pred) + 1.0)
    t_sens = tf.reduce_sum(s_gt * y_pred) / (tf.reduce_sum(s_gt) + 1.0)
    return 1.0 - (2.0 * t_prec * t_sens + 1.0) / (t_prec + t_sens + 1.0)


def cldice_metrica(y_true, y_pred):
    """clDice como métrica (1 = perfecto)."""
    return 1.0 - cldice_loss(y_true, y_pred)


def perdida(y_true, y_pred):
    """BCE + Dice + 0.5*clDice (soft) para la línea limpia.

    El BCE solo se hunde en el desbalance (la línea es ~2-15 % de los píxeles);
    el término Dice es sensible a la superposición y el clDice empuja a
    preservar la topología (conectividad) de la línea.
    """
    y_pred = tf.clip_by_value(y_pred, 1e-6, 1.0 - 1e-6)
    bce = -tf.reduce_mean(
        y_true * tf.math.log(y_pred) + (1.0 - y_true) * tf.math.log(1.0 - y_pred)
    )
    return bce + dice(y_true, y_pred) + 0.5 * cldice_loss(y_true, y_pred)


def psnr(y_true, y_pred):
    """PSNR de la mascara limpia (y binaria) contra la probabilidad predicha."""
    yt = np.asarray(y_true, dtype=np.float64)
    yp = np.asarray(y_pred, dtype=np.float64)
    mse = float(np.mean((yt - yp) ** 2))
    if mse <= 1e-12:
        return 99.0
    return float(10.0 * np.log10(1.0 / mse))


def evaluar(tflite, x, y, max_muestras=48):
    """IoU y PSNR promedio del modelo exportado sobre [x]/[y] (banda objetivo).

    Se usa el propio `.tflite` (no Keras) para medir exactamente lo que corre en
    el movil, incluida la cuantizacion float16. El objetivo [y] es la banda de
    entrenamiento (ver `banda`); el benchmark mide clDice contra el esqueleto de
    1 px del `.npz`.
    """
    if x.shape[0] == 0:
        return {"iou": 0.0, "psnr": 0.0, "muestras": 0}
    interp = tf.lite.Interpreter(model_content=tflite)
    interp.allocate_tensors()
    det_in = interp.get_input_details()[0]
    det_out = interp.get_output_details()[0]
    n = min(max_muestras, x.shape[0])
    ious, psnrs = [], []
    for i in range(n):
        interp.set_tensor(det_in["index"], x[i : i + 1].astype(np.float32))
        interp.invoke()
        pred = interp.get_tensor(det_out["index"])[0, ..., 0]
        binp = pred > 0.5
        gt = y[i, ..., 0] > 0.5
        inter = np.logical_and(binp, gt).sum()
        union = np.logical_or(binp, gt).sum()
        ious.append(float(inter) / max(float(union), 1.0))
        psnrs.append(psnr(gt.astype(np.float32), pred))
    return {
        "iou": float(np.mean(ious)),
        "psnr": float(np.mean(psnrs)),
        "muestras": n,
    }


class CalentamientoCoseno(tf.keras.optimizers.schedules.LearningRateSchedule):
    """Calentamiento lineal y después decaimiento coseno.

    El calentamiento evita que un lote grande y un LR alto desestabilicen las
    primeras épocas; el coseno baja el LR de forma suave para que el modelo
    converja fino al final de la fase rápida.
    """

    def __init__(self, lr_max, lr_fin, pasos_calentamiento, pasos_decay):
        super().__init__()
        self.lr_max = float(lr_max)
        self.lr_fin = float(lr_fin)
        self.pasos_calentamiento = max(0, int(pasos_calentamiento))
        self.pasos_decay = max(1, int(pasos_decay))

    def __call__(self, paso):
        paso = tf.cast(paso, tf.float32)
        p_cal = float(self.pasos_calentamiento)
        # Coseno desde lr_max hasta lr_fin a lo largo de la fase.
        progreso = tf.clip_by_value(
            (paso - p_cal) / tf.cast(self.pasos_decay, tf.float32), 0.0, 1.0
        )
        lr_cos = self.lr_fin + 0.5 * (self.lr_max - self.lr_fin) * (
            1.0 + tf.cos(np.pi * progreso)
        )
        if self.pasos_calentamiento <= 0:
            return lr_cos
        lr_cal = self.lr_max * (paso + 1.0) / p_cal
        return tf.where(paso < p_cal, lr_cal, lr_cos)

    def get_config(self):
        return {
            "lr_max": self.lr_max,
            "lr_fin": self.lr_fin,
            "pasos_calentamiento": self.pasos_calentamiento,
            "pasos_decay": self.pasos_decay,
        }


class EstadoEntrenamiento(tf.keras.callbacks.Callback):
    """Escribe cuántas épocas van completadas (para `--reanudar`).

    Se lleva un contador propio en vez de leer el número de época de Keras:
    así el valor es el total absoluto aunque el `fit` arranque con
    `initial_epoch` (fase de pulido o reanudación).
    """

    def __init__(self, carpeta, epocas_previas=0):
        super().__init__()
        self.carpeta = carpeta
        self.epocas = int(epocas_previas)
        os.makedirs(carpeta, exist_ok=True)

    def on_epoch_end(self, epoch, logs=None):
        self.epocas += 1
        with open(os.path.join(self.carpeta, "estado.json"), "w", encoding="utf-8") as f:
            json.dump({"epoca_completadas": self.epocas}, f)


def configurar_precision(modo):
    """Resuelve `auto` y fija la política de precisión mixta si corresponde."""
    if modo == "auto":
        modo = "mixed_float16" if tf.config.list_physical_devices("GPU") else "float32"
    if modo == "mixed_float16":
        tf.keras.mixed_precision.set_global_policy("mixed_float16")
    print(f"precision: {modo}")
    return modo


def resolver_xla(modo):
    """Resuelve `auto` (XLA sólo si hay GPU) a un booleano de `jit_compile`."""
    if modo == "auto":
        modo = "si" if tf.config.list_physical_devices("GPU") else "no"
    activo = modo == "si"
    print(f"XLA (jit_compile): {'si' if activo else 'no'}")
    return activo


def leer_epocas_hechas(carpeta):
    """Devuelve las épocas ya completadas de un punto de control (0 si no hay).

    Acepta un directorio (usa `estado.json` + `ultimo.weights.h5`) o el archivo
    `estado.json` directamente. No valida los pesos; sólo cuántas épocas van.
    """
    if not carpeta:
        return 0
    estado = carpeta if carpeta.endswith(".json") else os.path.join(carpeta, "estado.json")
    if os.path.exists(estado):
        try:
            with open(estado, encoding="utf-8") as f:
                return int(json.load(f).get("epoca_completadas", 0))
        except (ValueError, OSError):
            return 0
    return 0


def pesos_reanudar(carpeta):
    """Ruta del `ultimo.weights.h5` de una carpeta de puntos de control (o None)."""
    if not carpeta:
        return None
    candidato = os.path.join(carpeta, "ultimo.weights.h5")
    return candidato if os.path.exists(candidato) else None


def argumentos():
    p = argparse.ArgumentParser(description="Entrena el limpiador de trazos.")
    p.add_argument("--muestras", type=int, default=1400, help="pares sintéticos de entrenamiento")
    p.add_argument("--validacion", type=int, default=200, help="pares sintéticos de validación")
    p.add_argument("--reales", type=str, default=None, help="carpeta con pares reales entrada/objetivo")
    p.add_argument("--pares", type=str, default=None,
                   help="cache .npz de un plan de dataset (tools/preparar_dataset_limpiar_trazos*.py)")
    p.add_argument("--parches-por-imagen", type=int, default=16,
                   help="parches 64x64 maximos por hoja del cache --pares")
    p.add_argument("--aumentos", type=int, default=3, help="copias aumentadas por muestra de entrenamiento")
    p.add_argument("--epocas", type=int, default=60)
    p.add_argument("--lote", type=int, default=64,
                   help="lote de la fase rápida (lote grande = menos pasos y GPU mejor usada)")
    p.add_argument("--lote-final", type=int, default=32,
                   help="lote de la fase de pulido (0 = igual que --lote)")
    p.add_argument("--fase-rapida", type=float, default=0.75,
                   help="fracción de épocas con lote grande y LR alto (resto = pulido)")
    p.add_argument("--calentamiento", type=int, default=2,
                   help="épocas de calentamiento lineal del LR en la fase rápida")
    p.add_argument("--alpha-fase-rapida", type=float, default=0.05,
                   help="LR al terminar la fase rápida, como fracción de --lr")
    p.add_argument("--alpha-final", type=float, default=0.1,
                   help="LR al terminar el pulido, como fracción del LR de arranque de esa fase")
    p.add_argument("--lr", type=float, default=1e-3,
                   help="LR máximo de la fase rápida (calentamiento + coseno)")
    p.add_argument("--banda", type=int, default=1,
                   help="radio de la banda objetivo en px (0 = línea de 1 px; 1 = 3 px)")
    p.add_argument("--precision", choices=["auto", "float32", "mixed_float16"],
                   default="auto",
                   help="precisión de cómputo (auto = float16 mixto en GPU, float32 en CPU)")
    p.add_argument("--xla", choices=["auto", "si", "no"], default="auto",
                   help="compilar el paso de entrenamiento con XLA (auto = sí en GPU)")
    p.add_argument("--checkpoints", type=str,
                   default=os.path.join(RAIZ, ".cache", "modelos", "checkpoints"),
                   help="carpeta de puntos de control (ultimo/mejor.weights.h5)")
    p.add_argument("--reanudar", type=str, default=None,
                   help="carpeta de checkpoints (o estado.json) para continuar entrenando")
    p.add_argument("--semilla", type=int, default=1)
    p.add_argument("--nombre", type=str, default="Buril",
                   help="nombre del modelo (va al manifiesto y a la release)")
    p.add_argument("--version", type=str, default="v1.0.0-experimental.1a",
                   help="version del modelo (va al manifiesto y a la release)")
    p.add_argument("--salida", type=str, default=os.path.join(
        RAIZ, "app", "src", "main", "assets", "modelos", "limpiar_trazos.tflite"))
    return p.parse_args()


def main():
    args = argumentos()
    autoprueba()
    tf.random.set_seed(args.semilla)
    rng = np.random.default_rng(args.semilla)

    xtr, ytr = dataset(args.muestras, rng)
    xva, yva = dataset(args.validacion, rng)

    if args.reales:
        reales = cargar_reales(args.reales)
        if reales is not None:
            xr, yr = reales
            corte = max(1, int(len(xr) * 0.8)) if len(xr) > 1 else len(xr)
            xtr = np.concatenate([xtr, xr[:corte]], 0)
            ytr = np.concatenate([ytr, yr[:corte]], 0)
            if corte < len(xr):
                xva = np.concatenate([xva, xr[corte:]], 0)
                yva = np.concatenate([yva, yr[corte:]], 0)

    if args.pares:
        xp_tr, yp_tr, xp_va, yp_va = cargar_pares_cache(
            args.pares, LADO,
            max_por_imagen=args.parches_por_imagen,
            semilla=args.semilla,
        )
        xtr = np.concatenate([xtr, xp_tr], 0)
        ytr = np.concatenate([ytr, yp_tr], 0)
        xva = np.concatenate([xva, xp_va], 0)
        yva = np.concatenate([yva, yp_va], 0)

    # Objetivo de entrenamiento = banda limpia (no línea de 1 px): la app
    # adelgaza con Zhang-Suen, así que el esqueleto final sigue siendo de 1 px.
    ytr = banda(ytr, args.banda)
    yva = banda(yva, args.banda)

    # Aumentación al vuelo con tf.data: no se materializa el set ×(1+aumentos)
    # en RAM (eso causaba el OOM / SIGKILL). Ver `flujo`.
    print(f"entrenamiento: {xtr.shape[0]} pares x{args.aumentos + 1} (aumento al "
          f"vuelo) | validación: {xva.shape[0]} pares | objetivo: banda de "
          f"{2 * args.banda + 1} px")

    # Precision/XLA antes de construir y compilar la red (la politica global de
    # precision mixta tiene que estar fijada desde el inicio).
    precision = configurar_precision(args.precision)
    jit = resolver_xla(args.xla)

    # Schedule: fase rápida (lote grande + LR alto) y fase de pulido (lote chico
    # + LR bajo). Ver el docstring del módulo.
    epocas = max(1, int(args.epocas))
    epocas_rapida = int(round(epocas * min(max(args.fase_rapida, 0.0), 1.0)))
    epocas_rapida = min(max(epocas_rapida, 0), epocas)
    lote_final = args.lote_final if args.lote_final and args.lote_final > 0 else args.lote
    total = xtr.shape[0] * (args.aumentos + 1)

    os.makedirs(args.checkpoints, exist_ok=True)
    pesos = pesos_reanudar(args.reanudar)
    hechas = leer_epocas_hechas(args.reanudar) if pesos else 0
    if args.reanudar and pesos:
        print(f"reanudando desde {pesos} ({hechas} épocas completadas)")
    elif args.reanudar:
        print(f"[reanudar] no hay punto de control en {args.reanudar}; se entrena de cero")

    red = modelo()
    parametros = int(red.count_params())
    print(f"modelo: {parametros:,} parametros (~{parametros * 2 / 1e6:.2f} MB en float16)")
    if pesos:
        red.load_weights(pesos)

    def _callbacks(previas, tiene_val):
        cbs = [
            tf.keras.callbacks.ModelCheckpoint(
                os.path.join(args.checkpoints, "ultimo.weights.h5"),
                save_weights_only=True, save_freq="epoch", verbose=0),
            EstadoEntrenamiento(args.checkpoints, previas),
            tf.keras.callbacks.TerminateOnNaN(),
        ]
        if tiene_val:
            cbs.insert(1, tf.keras.callbacks.ModelCheckpoint(
                os.path.join(args.checkpoints, "mejor.weights.h5"),
                monitor="val_loss", save_best_only=True, save_weights_only=True,
                verbose=0))
        return cbs

    def _ajustar(plan_lr, lote):
        tren = flujo(xtr, ytr, lote, aumentos=args.aumentos,
                     entrenando=True, semilla=args.semilla)
        val = flujo(xva, yva, lote) if xva.shape[0] else None
        red.compile(
            optimizer=tf.keras.optimizers.Adam(plan_lr),
            loss=perdida,
            metrics=[dice],
            jit_compile=jit,
        )
        return tren, val

    # --- Fase rápida: lote grande, LR con calentamiento y coseno (menos pasos). ---
    if hechas < epocas_rapida:
        cal = min(args.calentamiento, max(0, epocas_rapida - hechas - 1))
        pasos_epoca = max(1, int(np.ceil(total / args.lote)))
        plan = CalentamientoCoseno(
            args.lr, args.lr * args.alpha_fase_rapida,
            cal * pasos_epoca, epocas_rapida * pasos_epoca)
        tren, val = _ajustar(plan, args.lote)
        print(f"fase rapida: lote {args.lote}, {epocas_rapida} epocas "
              f"({pasos_epoca} pasos/epoca), LR {args.lr} -> "
              f"{args.lr * args.alpha_fase_rapida} (calentamiento {cal})")
        red.fit(tren, validation_data=val, epochs=epocas_rapida,
                initial_epoch=hechas, callbacks=_callbacks(hechas, val is not None),
                verbose=2)
        hechas = epocas_rapida

    # --- Pulido: lote chico, LR bajo y más pasos para refinar el detalle. ---
    if hechas < epocas:
        inicio = max(hechas, epocas_rapida)
        lr0 = args.lr * args.alpha_fase_rapida
        pasos_epoca = max(1, int(np.ceil(total / lote_final)))
        plan = tf.keras.optimizers.schedules.CosineDecay(
            initial_learning_rate=lr0,
            decay_steps=max(1, pasos_epoca * (epocas - inicio)),
            alpha=args.alpha_final,
        )
        tren, val = _ajustar(plan, lote_final)
        print(f"pulido: lote {lote_final}, {epocas - inicio} epocas "
              f"({pasos_epoca} pasos/epoca), LR {lr0} -> "
              f"{lr0 * args.alpha_final}")
        red.fit(tren, validation_data=val, epochs=epocas,
                initial_epoch=inicio, callbacks=_callbacks(inicio, val is not None),
                verbose=2)

    # Exportamos el MEJOR val_loss (si existe), no el último: así un sobreajuste
    # de las últimas épocas no degrada el modelo que va al móvil.
    mejor = os.path.join(args.checkpoints, "mejor.weights.h5")
    if os.path.exists(mejor):
        red.load_weights(mejor)
        print("exportando el mejor punto de control (menor val_loss)")

    # Se exporta un clon en float32 (aunque se haya entrenado con precisión
    # mixta): el conversor no acepta el modelo fp16 tal cual y terminaría
    # pidiendo Flex ops. La cuantización a float16 la hace el propio conversor.
    conv = tf.lite.TFLiteConverter.from_keras_model(modelo_float32(red))
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    tflite = conv.convert()

    os.makedirs(os.path.dirname(args.salida), exist_ok=True)
    with open(args.salida, "wb") as f:
        f.write(tflite)
    print("modelo guardado:", args.salida, len(tflite), "bytes")

    # Evaluacion final sobre validacion con el modelo exportado (float16), que es
    # lo que de verdad corre en el telefono.
    metricas = evaluar(tflite, xva, yva)
    print(f"validacion: IoU={metricas['iou']:.3f} PSNR={metricas['psnr']:.2f} dB "
          f"({metricas['muestras']} muestras)")

    # Manifiesto con metricas + licencia/atribucion del dataset (Plan B).
    manifiesto = {
        "modelo": f"{args.nombre} {args.version}",
        "descripcion": "limpiador de trazos (entrada 64x64x1 -> línea limpia 64x64x1)",
        "formato": "tflite float16",
        "bytes": len(tflite),
        "parametros": parametros,
        "epocas": epocas,
        "aumentos": args.aumentos,
        "lr": args.lr,
        "lote": args.lote,
        "lote_final": lote_final,
        "fase_rapida": args.fase_rapida,
        "calentamiento": args.calentamiento,
        "precision": precision,
        "xla": jit,
        "banda_objetivo_px": 2 * args.banda + 1,
        "perdida": "BCE + Dice + 0.5*clDice",
        "pares": args.pares,
        "reales": args.reales,
        "validacion": metricas,
        "licencia_datos": "CC BY 4.0 - Quick, Draw! (Google, Inc.)",
        "atribucion": "The Quick, Draw! dataset by Google, Inc. (CC BY 4.0).",
    }
    ruta_manifiesto = os.path.splitext(args.salida)[0] + ".json"
    with open(ruta_manifiesto, "w", encoding="utf-8") as f:
        json.dump(manifiesto, f, ensure_ascii=False, indent=2)
    print("metricas guardadas:", ruta_manifiesto)


if __name__ == "__main__":
    main()
