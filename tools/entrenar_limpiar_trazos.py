"""Entrena un modelo chico que "limpia" trazos repasados.

Entrada: parche 64x64x1 con la tinta gruesa/irregular (varios repases).
Salida:  parche 64x64x1 con el esqueleto limpio de 1 px.

Los datos son sintéticos (curvas Bezier gruesas + repases -> curva fina),
así que no hace falta ningún dataset externo ni licencias. El modelo se
exporta a .tflite (float16) para correrlo en el móvil con LiteRT.

Uso (desde la raíz del repo, con `tensorflow-cpu` + `numpy` instalados):

    python3 tools/entrenar_limpiar_trazos.py

Escribe `app/src/main/assets/modelos/limpiar_trazos.tflite`.
"""

import os
import numpy as np
import tensorflow as tf

# Raíz del repo (este archivo vive en <raíz>/tools/).
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LADO = 64
RNG = np.random.default_rng(1234)

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


def dataset(n):
    x = np.empty((n, LADO, LADO, 1), np.float32)
    y = np.empty((n, LADO, LADO, 1), np.float32)
    for i in range(n):
        x[i], y[i] = muestra(np.random.default_rng(1000 + i))
    return x, y


def modelo():
    inp = tf.keras.Input((LADO, LADO, 1))
    x = tf.keras.layers.Conv2D(16, 3, padding="same", activation="relu")(inp)
    x = tf.keras.layers.Conv2D(16, 3, padding="same", activation="relu")(x)
    s1 = x
    x = tf.keras.layers.Conv2D(24, 3, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(24, 3, padding="same", activation="relu")(x)
    s2 = x
    x = tf.keras.layers.Conv2D(32, 3, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2DTranspose(24, 2, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Concatenate()([x, s2])
    x = tf.keras.layers.Conv2D(24, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.Conv2DTranspose(16, 2, strides=2, padding="same", activation="relu")(x)
    x = tf.keras.layers.Concatenate()([x, s1])
    x = tf.keras.layers.Conv2D(16, 3, padding="same", activation="relu")(x)
    out = tf.keras.layers.Conv2D(1, 1, activation="sigmoid")(x)
    return tf.keras.Model(inp, out)


def dice(y_true, y_pred):
    y_true = tf.reshape(y_true, [-1])
    y_pred = tf.reshape(y_pred, [-1])
    inter = tf.reduce_sum(y_true * y_pred)
    return 1.0 - (2.0 * inter + 1.0) / (tf.reduce_sum(y_true) + tf.reduce_sum(y_pred) + 1.0)


def main():
    tf.random.set_seed(1)
    xtr, ytr = dataset(900)
    xva, yva = dataset(120)

    red = modelo()
    red.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.BinaryCrossentropy(),
        metrics=[dice],
    )
    red.fit(xtr, ytr, validation_data=(xva, yva), epochs=14, batch_size=24, verbose=2)

    conv = tf.lite.TFLiteConverter.from_keras_model(red)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    tflite = conv.convert()

    salida = os.path.join(
        RAIZ, "app", "src", "main", "assets", "modelos", "limpiar_trazos.tflite"
    )
    os.makedirs(os.path.dirname(salida), exist_ok=True)
    with open(salida, "wb") as f:
        f.write(tflite)
    print("modelo guardado:", salida, len(tflite), "bytes")

    # Chequeo rápido: solapamiento del esqueleto aprendido con el objetivo.
    interp = tf.lite.Interpreter(model_content=tflite)
    interp.allocate_tensors()
    det_in = interp.get_input_details()[0]
    det_out = interp.get_output_details()[0]
    for i in range(3):
        interp.set_tensor(det_in["index"], xva[i : i + 1])
        interp.invoke()
        pred = interp.get_tensor(det_out["index"])[0, ..., 0]
        binp = pred > 0.5
        gt = yva[i, ..., 0] > 0.5
        inter = np.logical_and(binp, gt).sum()
        union = np.logical_or(binp, gt).sum()
        print(f"muestra {i}: IoU={inter / max(union, 1):.3f}")


if __name__ == "__main__":
    main()
