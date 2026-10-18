"""Entrena un modelo chico que "limpia" trazos repasados.

Entrada: parche 64x64x1 con la tinta gruesa/irregular (varios repases).
Salida:  parche 64x64x1 con el esqueleto limpio de 1 px.

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
simular más grosores de repase.

El modelo se exporta a .tflite (float16) para correrlo en el móvil con LiteRT.

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

La via recomendada es Google Colab: `tools/buril_colab.ipynb`
es un driver fino que llama a este script y publica el `.tflite` en la release
`modelo-buril`; `android-apk.yml` la baja y la inyecta en los assets antes de
armar el APK. Como respaldo, el workflow
`entrenar-limpiar-trazos-planb.yml` entrena en Actions y publica en la misma
release. `--nombre` / `--version` quedan en el manifiesto (Buril 1, Buril 2...).
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
    objetivo = np.zeros((LADO, LADO), np.float32)
    pts_fina = bezier(cp)
    estampar(objetivo, pts_fina, rng.uniform(0.6, 1.2))

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
        xs.append(_leer_gris(os.path.join(dir_in, nombre)))
        ys.append((_leer_gris(objetivos[base]) >= 0.5).astype(np.float32))
    if not xs:
        print(f"[reales] {carpeta}: no se encontraron pares con el mismo nombre.")
        return None
    print(f"[reales] {len(xs)} pares cargados de {carpeta}")
    return np.stack(xs), np.stack(ys)


def aumentar_conjunto(x, y, rng, copias):
    """Agrega [copias] versiones aumentadas de cada muestra al conjunto."""
    if copias <= 0:
        return x, y
    xa, ya = [x], [y]
    for _ in range(copias):
        ax = np.empty_like(x)
        ay = np.empty_like(y)
        for i in range(x.shape[0]):
            ax[i], ay[i] = aumentar(x[i], y[i], rng)
        xa.append(ax)
        ya.append(ay)
    return np.concatenate(xa, 0), np.concatenate(ya, 0)


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
            xs.append(equis[i, fy:fy + lado, fx:fx + lado, None].astype(np.float32) / 255.0)
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


def modelo():
    # Mini U-Net de 2 niveles. Un poco más ancha que la v1 (16/24/32) pero
    # sigue siendo chica para el móvil (float16, unos cientos de KB).
    inp = tf.keras.Input((LADO, LADO, 1))
    x = tf.keras.layers.Conv2D(24, 3, padding="same", activation="relu")(inp)
    x = tf.keras.layers.Conv2D(24, 3, padding="same", activation="relu")(x)
    s1 = x
    x = tf.keras.layers.Conv2D(32, 3, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    s2 = x
    x = tf.keras.layers.Conv2D(48, 3, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(48, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2DTranspose(32, 2, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Concatenate()([x, s2])
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2DTranspose(24, 2, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Concatenate()([x, s1])
    x = tf.keras.layers.Conv2D(24, 3, padding="same", activation="relu")(x)
    out = tf.keras.layers.Conv2D(1, 1, activation="sigmoid")(x)
    return tf.keras.Model(inp, out)


def dice(y_true, y_pred):
    y_true = tf.reshape(y_true, [-1])
    y_pred = tf.reshape(y_pred, [-1])
    inter = tf.reduce_sum(y_true * y_pred)
    return 1.0 - (2.0 * inter + 1.0) / (tf.reduce_sum(y_true) + tf.reduce_sum(y_pred) + 1.0)


def psnr(y_true, y_pred):
    """PSNR de la mascara limpia (y binaria) contra la probabilidad predicha."""
    yt = np.asarray(y_true, dtype=np.float64)
    yp = np.asarray(y_pred, dtype=np.float64)
    mse = float(np.mean((yt - yp) ** 2))
    if mse <= 1e-12:
        return 99.0
    return float(10.0 * np.log10(1.0 / mse))


def evaluar(tflite, x, y, max_muestras=48):
    """IoU y PSNR promedio del modelo exportado sobre [x]/[y] (esqueleto vs ideal).

    Se usa el propio `.tflite` (no Keras) para medir exactamente lo que corre en
    el movil, incluida la cuantizacion float16.
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


def argumentos():
    p = argparse.ArgumentParser(description="Entrena el limpiador de trazos.")
    p.add_argument("--muestras", type=int, default=1400, help="pares sintéticos de entrenamiento")
    p.add_argument("--validacion", type=int, default=200, help="pares sintéticos de validación")
    p.add_argument("--reales", type=str, default=None, help="carpeta con pares reales entrada/objetivo")
    p.add_argument("--pares", type=str, default=None,
                   help="cache .npz de un plan de dataset (tools/preparar_dataset_limpiar_trazos*.py)")
    p.add_argument("--parches-por-imagen", type=int, default=16,
                   help="parches 64x64 maximos por hoja del cache --pares")
    p.add_argument("--aumentos", type=int, default=1, help="copias aumentadas por muestra de entrenamiento")
    p.add_argument("--epocas", type=int, default=20)
    p.add_argument("--lote", type=int, default=32)
    p.add_argument("--semilla", type=int, default=1)
    p.add_argument("--nombre", type=str, default="Buril",
                   help="nombre del modelo (va al manifiesto y a la release)")
    p.add_argument("--version", type=str, default="1",
                   help="version del modelo (Buril 1, Buril 2, ...)")
    p.add_argument("--salida", type=str, default=os.path.join(
        RAIZ, "app", "src", "main", "assets", "modelos", "limpiar_trazos.tflite"))
    return p.parse_args()


def main():
    args = argumentos()
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

    xtr, ytr = aumentar_conjunto(xtr, ytr, rng, args.aumentos)

    orden = rng.permutation(xtr.shape[0])
    xtr, ytr = xtr[orden], ytr[orden]
    print(f"entrenamiento: {xtr.shape[0]} pares | validación: {xva.shape[0]} pares")

    red = modelo()
    red.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.BinaryCrossentropy(),
        metrics=[dice],
    )
    red.fit(
        xtr, ytr, validation_data=(xva, yva),
        epochs=args.epocas, batch_size=args.lote, verbose=2,
    )

    conv = tf.lite.TFLiteConverter.from_keras_model(red)
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
        "descripcion": "limpiador de trazos (entrada 64x64x1 -> esqueleto 64x64x1)",
        "formato": "tflite float16",
        "bytes": len(tflite),
        "epocas": args.epocas,
        "aumentos": args.aumentos,
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
