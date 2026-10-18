"""Benchmark: Buril contra los mejores modelos de limpieza de trazos.

Toma el set de validación del `.npz` (que ya trae *ground truth*: el esqueleto
limpio de 1 px) y corre **los mismos** dibujos por Buril y por modelos rivales
de la literatura/industria. Normaliza la salida de todos (umbral + esqueleto
Zhang-Suen) antes de medir, porque comparar el IoU de un esqueleto de 1 px
contra una línea gruesa sería tramposo.

Modelos rivales (5):

1. **Simo-Serra et al.** (`bobbens/sketch_simplification`, SIGGRAPH'16 / TOG'18),
   el modelo de referencia de limpieza de bocetos. Pesos convertidos a PyTorch
   (`Rayonapp/sketch_simplification`, `model_gan.pth`). Licencia del original:
   **CC-BY-NC-SA (no comercial)** — sirve como benchmark de investigación, no
   como argumento comercial.
2. **Anime2Sketch** (`Mukosame/Anime2Sketch`), extractor de línea (MIT).
3. **Informative Drawings** (`carolineec/informative-drawings`), generación de
   líneas no apareada (MIT).
4. **ControlNet Lineart** (`lllyasviel/Annotators`, vía `controlnet-aux`),
   extractor de lineart (Apache-2.0).
5. **PidiNet soft-edge** (`lllyasviel/Annotators`, vía `controlnet-aux`),
   detector de bordes suaves (Apache-2.0).

Baseline extra: **Zhang-Suen** puro (`skimage.morphology.skeletonize`) sobre el
repase binarizado.

Métricas por muestra (tras normalizar a 1 px): IoU y Dice esqueleto↔esqueleto,
IoU de máscara, precisión/recall, **clDice** (topología) y **PSNR**; además
métricas operativas (latencia ms/imagen y peso del modelo en MB).

Salidas: imprime los logs y el ranking por consola, guarda `resultados.json` y
una rejilla PNG en `.cache/benchmark/` (la rejilla muestra la salida
**normalizada** de cada modelo, es decir lo que el usuario ve tras el adelgazado
de la app, no la salida cruda). El cuaderno
`tools/benchmark_limpiar_trazos.ipynb` es la versión **autónoma** de Colab: lleva
esta lógica embebida (no clona el repo ni importa este archivo), muestra las
imágenes en línea y, opcionalmente, levanta una interfaz Gradio.

Uso:
    python3 tools/benchmark_limpiar_trazos.py todo --pares .cache/.../pares.npz --muestras 8
    python3 tools/benchmark_limpiar_trazos.py autoprueba   # sin red ni modelos

Dependencias (Colab las trae o las instala el cuaderno): numpy, pillow,
matplotlib, scikit-image, tensorflow, torch, torchvision, controlnet-aux,
huggingface_hub.
"""

import argparse
import gc
import json
import os
import subprocess
import time

import numpy as np

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Debe coincidir con `LimpiadorTrazosIa` (parche 64x64, paso 48 = solape 16).
LADO_PARQUE = 64
PASO_PARQUE = 48

# Umbral de "tinta" del repase de entrada (el fondo de la hoja es blanco puro).
UMBRAL_TINTA_ENTRADA = 250

LADO_DPI = 512

# Repos externos que se clonan una sola vez en `.cache/benchmark/`.
URL_ANIME2SKETCH = "https://github.com/Mukosame/Anime2Sketch"
PESOS_ANIME2SKETCH = "https://huggingface.co/public-data/Anime2Sketch/resolve/main/netG.pth"
REPO_SIMO_SERRA = "Rayonapp/sketch_simplification"  # pesos .t7 ya convertidos
ARCHIVO_SIMO_SERRA = "model_gan.pth"
REPO_INFORMATIVE = "carolineec/informativedrawings"  # Space con model.pth
ARCHIVO_INFORMATIVE = "model.pth"


# --------------------------------------------------------------------------
# Utilidades de imagen y métricas (numpy + scikit-image; sin modelos)
# --------------------------------------------------------------------------

def a_tinta(gray):
    """Devuelve la "tinta" (0..1) de una imagen gris, sin importar polaridad.

    La línea es lo que se aparta del fondo (la moda). Así funciona igual si el
    modelo pinta línea oscura sobre blanco o línea clara sobre negro.
    """
    g = np.asarray(gray, np.float32)
    if g.max() > 1.0:
        g = g / 255.0
    fondo = float(np.median(g))
    t = np.abs(g - fondo)
    mx = float(t.max())
    return (t / mx) if mx > 1e-6 else t


def binarizar(tinta, umbral=None):
    """Binariza una imagen de tinta; por defecto con Otsu (con respaldo fijo)."""
    from skimage.filters import threshold_otsu

    t = np.asarray(tinta, np.float32)
    if umbral is None:
        try:
            umbral = float(threshold_otsu(t))
        except Exception:
            umbral = 0.5
    return t > max(umbral, 1e-3)


def esqueletizar(mask):
    """Esqueleto de 1 px (Zhang-Suen). Idempotente sobre algo ya de 1 px."""
    from skimage.morphology import skeletonize

    return skeletonize(np.asarray(mask, bool))


def _iou(a, b):
    a, b = np.asarray(a, bool), np.asarray(b, bool)
    union = float(np.logical_or(a, b).sum())
    return float(np.logical_and(a, b).sum()) / max(union, 1.0)


def _dice(a, b):
    a, b = np.asarray(a, bool), np.asarray(b, bool)
    s = float(a.sum()) + float(b.sum())
    return 2.0 * float(np.logical_and(a, b).sum()) / max(s, 1.0)


def _precision_recall(pred, gt):
    pred, gt = np.asarray(pred, bool), np.asarray(gt, bool)
    tp = float(np.logical_and(pred, gt).sum())
    return tp / max(float(pred.sum()), 1.0), tp / max(float(gt.sum()), 1.0)


def psnr(gt, pred):
    """PSNR entre la máscara GT (0/1) y la predicción normalizada."""
    gt = np.asarray(gt, np.float64)
    pred = np.asarray(pred, np.float64)
    mse = float(np.mean((gt - pred) ** 2))
    if mse <= 1e-12:
        return 99.0
    return float(10.0 * np.log10(1.0 / mse))


def cldice(gt, pred):
    """clDice (Shit et al., CVPR'21): preserva la topología del esqueleto.

    Con un GT de 1 px el IoU es durísimo y engaña; clDice premia que la
    predicción siga la conectividad correcta aunque el grosor no calce.
    """
    gt = np.asarray(gt, bool)
    pred = np.asarray(pred, bool)
    s_gt = esqueletizar(gt)
    s_pred = esqueletizar(pred)
    t_prec = float(np.logical_and(s_pred, gt).sum()) / max(float(s_pred.sum()), 1.0)
    t_sens = float(np.logical_and(s_gt, pred).sum()) / max(float(s_gt.sum()), 1.0)
    return 2.0 * t_prec * t_sens / max(t_prec + t_sens, 1e-6)


def evaluar_muestra(tinta_pred, gt):
    """Todas las métricas de una muestra (salida cruda vs. GT binario).

    La salida cruda de cada modelo puede tener la línea clara sobre negro o
    oscura sobre blanco; [a_tinta] la orienta a "tinta" antes de medir.
    """
    gt = np.asarray(gt, np.float32) > 0.5
    tinta = a_tinta(tinta_pred)
    pred_bin = binarizar(tinta)
    pred_esq = esqueletizar(pred_bin)
    gt_esq = esqueletizar(gt)
    precision, recall = _precision_recall(pred_bin, gt)
    return {
        "iou_esq": _iou(pred_esq, gt_esq),
        "dice_esq": _dice(pred_esq, gt_esq),
        "iou_bin": _iou(pred_bin, gt),
        "precision": precision,
        "recall": recall,
        "cldice": cldice(gt, pred_bin),
        "psnr": psnr(gt.astype(np.float32), tinta),
    }


METRICAS = ["cldice", "iou_esq", "dice_esq", "iou_bin", "precision", "recall", "psnr"]


def salida_normalizada(tinta_pred):
    """Salida como la ve el usuario: tinta orientada -> umbral Otsu -> esqueleto.

    La app (`LimpiadorTrazosIa`) adelgaza con Zhang-Suen antes de dibujar, así
    que esto es lo que llega a la hoja: mostrar la salida cruda (banda/manchas)
    en la rejilla daba conclusiones falsas. Devuelve 0/1 float.
    """
    return esqueletizar(binarizar(a_tinta(tinta_pred))).astype(np.float32)


def promediar(lista):
    """Promedia una lista de dictámenes de métricas (uno por muestra)."""
    if not lista:
        return {m: 0.0 for m in METRICAS}
    return {m: float(np.mean([d[m] for d in lista])) for m in METRICAS}


# --------------------------------------------------------------------------
# Dataset
# --------------------------------------------------------------------------

def cargar_muestras(npz, n, semilla=1):
    """Toma [n] pares (entrada gris uint8, GT binario) del split validación.

    El `.npz` guarda `x` (repase) y `y` (esqueleto limpio) con `split` agrupado
    por base para no tener fuga entre entrenamiento y validación.
    """
    datos = np.load(npz)
    equis, ye = datos["x"], datos["y"]
    if "split" in datos:
        split = np.asarray(datos["split"]).astype(str)
    else:
        split = np.array(["entrenamiento"] * len(equis))
    indices = np.where(split == "validacion")[0]
    if len(indices) == 0:
        indices = np.arange(len(equis))
    rng = np.random.default_rng(semilla)
    rng.shuffle(indices)
    indices = indices[:n]
    return equis[indices], ye[indices]


# --------------------------------------------------------------------------
# Modelos rivales y Buril
# --------------------------------------------------------------------------

def _clonar(url, destino):
    if not os.path.isdir(os.path.join(destino, ".git")):
        os.makedirs(os.path.dirname(destino), exist_ok=True)
        subprocess.run(["git", "clone", "--depth", "1", url, destino], check=True)
    return destino


def _torch_load(ruta):
    import torch

    try:
        return torch.load(ruta, map_location="cpu", weights_only=True)
    except Exception:
        return torch.load(ruta, map_location="cpu", weights_only=False)


def _hf_descarga(repo, archivo, tipo="model"):
    from huggingface_hub import hf_hub_download

    return hf_hub_download(repo_id=repo, filename=archivo, repo_type=tipo)


def _pil_rgb(x_u8):
    from PIL import Image

    return Image.fromarray(np.stack([x_u8] * 3, axis=-1), mode="RGB")


def _dispositivo():
    import torch

    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


# --- Baseline algorítmico: Zhang-Suen puro sobre el repase ------------------

def cargar_zhangsuen():
    return {"bytes": 0}


def predecir_zhangsuen(_obj, x_u8):
    tinta = a_tinta(x_u8)
    return esqueletizar(binarizar(tinta)).astype(np.float32)


# --- Buril (el modelo propio, por parches como en el móvil) -----------------

def cargar_buril(ruta):
    import tensorflow as tf

    with open(ruta, "rb") as f:
        interp = tf.lite.Interpreter(model_content=f.read())
    interp.allocate_tensors()
    return {"interp": interp, "bytes": os.path.getsize(ruta)}


def predecir_buril(obj, x_u8):
    interp = obj["interp"]
    alto, ancho = x_u8.shape
    det_in = interp.get_input_details()[0]
    det_out = interp.get_output_details()[0]
    acumulado = np.zeros((alto, ancho), np.float32)
    for ty in range(0, alto, PASO_PARQUE):
        for tx in range(0, ancho, PASO_PARQUE):
            parche = np.zeros((LADO_PARQUE, LADO_PARQUE), np.float32)
            sub = x_u8[ty:ty + LADO_PARQUE, tx:tx + LADO_PARQUE]
            parche[:sub.shape[0], :sub.shape[1]] = (sub < UMBRAL_TINTA_ENTRADA)
            interp.set_tensor(det_in["index"], parche[None, :, :, None])
            interp.invoke()
            salida = interp.get_tensor(det_out["index"])[0, :, :, 0]
            y2 = min(ty + LADO_PARQUE, alto)
            x2 = min(tx + LADO_PARQUE, ancho)
            acumulado[ty:y2, tx:x2] = np.maximum(
                acumulado[ty:y2, tx:x2], salida[:y2 - ty, :x2 - tx])
    return acumulado


# --- 1. Simo-Serra et al. (pesos .t7 convertidos a PyTorch) -----------------
# Arquitectura adaptada del código Lua original (`bobbens/sketch_simplification`)
# por el proyecto VideoComposer/ModelScope (Apache-2.0). El modelo original es
# CC-BY-NC-SA: sólo benchmark de investigación, NO comercial.

def _clase_simo_serra():
    """Devuelve la clase PyTorch del modelo (importa torch recién acá)."""
    import torch.nn as nn

    class SimoSerraSketch(nn.Module):
        """Reimplementación en PyTorch del limpiador de bocetos de Simo-Serra."""

        def __init__(self):
            super().__init__()
            self.mean = 0.9664114577640158
            self.std = 0.0858381272736797
            self.layers = nn.Sequential(
                nn.Conv2d(1, 48, 5, 2, 2), nn.ReLU(inplace=True),
                nn.Conv2d(48, 128, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(128, 128, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(128, 128, 3, 2, 1), nn.ReLU(inplace=True),
                nn.Conv2d(128, 256, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(256, 256, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(256, 256, 3, 2, 1), nn.ReLU(inplace=True),
                nn.Conv2d(256, 512, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(512, 1024, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(1024, 1024, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(1024, 1024, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(1024, 1024, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(1024, 512, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(512, 256, 3, 1, 1), nn.ReLU(inplace=True),
                nn.ConvTranspose2d(256, 256, 4, 2, 1), nn.ReLU(inplace=True),
                nn.Conv2d(256, 256, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(256, 128, 3, 1, 1), nn.ReLU(inplace=True),
                nn.ConvTranspose2d(128, 128, 4, 2, 1), nn.ReLU(inplace=True),
                nn.Conv2d(128, 128, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(128, 48, 3, 1, 1), nn.ReLU(inplace=True),
                nn.ConvTranspose2d(48, 48, 4, 2, 1), nn.ReLU(inplace=True),
                nn.Conv2d(48, 24, 3, 1, 1), nn.ReLU(inplace=True),
                nn.Conv2d(24, 1, 3, 1, 1), nn.Sigmoid())

        def forward(self, x):
            # x: [B, 1, H, W] en [0, 1], tinta oscura sobre blanco.
            return self.layers((x - self.mean) / self.std)

    return SimoSerraSketch


def _adaptar_pesos_simo_serra(sd):
    """Normaliza las claves de los pesos a la `layers.N` del modelo.

    El `.pth` convertido viene agrupado por bloques (`layer1.0`, `layer1.2`, …);
    las convoluciones, en orden, coinciden con las `layers.0/2/4/…` del
    `nn.Sequential`. Si ya vinieran planas, se devuelven tal cual.
    """
    if any(str(k).startswith("layers.") for k in sd):
        return sd
    bases = sorted({str(k).rsplit(".", 1)[0] for k in sd},
                   key=lambda b: tuple(int(p) for p in b.replace("layer", "").split(".")))
    indices = list(range(0, 45, 2))  # 23 convoluciones en el Sequential
    if len(bases) != len(indices):
        raise ValueError(f"pesos inesperados: {len(bases)} bloques, se esperaban {len(indices)}")
    nuevo = {}
    for idx, base in zip(indices, bases):
        nuevo[f"layers.{idx}.weight"] = sd[f"{base}.weight"]
        if f"{base}.bias" in sd:
            nuevo[f"layers.{idx}.bias"] = sd[f"{base}.bias"]
    return nuevo


def cargar_simo_serra():
    ruta = _hf_descarga(REPO_SIMO_SERRA, ARCHIVO_SIMO_SERRA)
    modelo = _clase_simo_serra()()
    modelo.load_state_dict(_adaptar_pesos_simo_serra(_torch_load(ruta)))
    modelo.eval()
    return {"modelo": modelo.to(_dispositivo()), "bytes": os.path.getsize(ruta)}


def predecir_simo_serra(obj, x_u8):
    import torch

    g = x_u8.astype(np.float32) / 255.0
    alto, ancho = g.shape
    ph, pw = (-alto) % 8, (-ancho) % 8
    if ph or pw:
        g = np.pad(g, ((0, ph), (0, pw)), mode="edge")
    with torch.no_grad():
        t = torch.from_numpy(g)[None, None].to(_dispositivo())
        out = obj["modelo"](t)[0, 0].cpu().numpy()
    return out[:alto, :ancho]


# --- 2. Anime2Sketch --------------------------------------------------------

def cargar_anime2sketch():
    import functools
    import importlib.util
    import torch.nn as nn

    dir_repo = _clonar(URL_ANIME2SKETCH, os.path.join(RAIZ, ".cache/benchmark/Anime2Sketch"))
    # Carga `model.py` del repo con un nombre único (evita chocar con otro `model`).
    spec = importlib.util.spec_from_file_location(
        "anime2sketch_model", os.path.join(dir_repo, "model.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)

    pesos = _hf_descarga("public-data/Anime2Sketch", "netG.pth")
    norm = functools.partial(nn.InstanceNorm2d, affine=False, track_running_stats=False)
    modelo = mod.UnetGenerator(3, 1, 8, 64, norm_layer=norm, use_dropout=False)
    estado = _torch_load(pesos)
    estado = {k.replace("module.", ""): v for k, v in estado.items()}
    modelo.load_state_dict(estado)
    modelo.eval()
    return {"modelo": modelo.to(_dispositivo()), "bytes": os.path.getsize(pesos)}


def predecir_anime2sketch(obj, x_u8):
    import torch
    from torchvision.transforms import (Compose, InterpolationMode, Normalize,
                                        Resize, ToTensor)

    tf = Compose([
        Resize((LADO_DPI, LADO_DPI), InterpolationMode.BICUBIC),
        ToTensor(),
        Normalize((0.5, 0.5, 0.5), (0.5, 0.5, 0.5)),
    ])
    img = tf(_pil_rgb(x_u8))[None].to(_dispositivo())
    with torch.no_grad():
        out = obj["modelo"](img)[0, 0].cpu().numpy()
    return np.clip((out + 1.0) / 2.0, 0.0, 1.0)


# --- 3. Informative Drawings ------------------------------------------------
# Arquitectura `Generator` del Space `carolineec/informativedrawings`
# (MIT). Reproducida acá para cargar los pesos `model.pth` directamente.

def _clase_informative():
    """Devuelve la clase PyTorch del generador (importa torch recién acá)."""
    import torch.nn as nn

    class InformativeGenerator(nn.Module):
        """Generador ResNet de Informative Drawings (salida con sigmoide)."""

        def __init__(self, input_nc=3, output_nc=1, n_residual_blocks=3):
            super().__init__()
            norm_layer = nn.InstanceNorm2d

            class ResidualBlock(nn.Module):
                def __init__(self, canales):
                    super().__init__()
                    self.conv_block = nn.Sequential(
                        nn.ReflectionPad2d(1),
                        nn.Conv2d(canales, canales, 3),
                        norm_layer(canales),
                        nn.ReLU(inplace=True),
                        nn.ReflectionPad2d(1),
                        nn.Conv2d(canales, canales, 3),
                        norm_layer(canales),
                    )

                def forward(self, x):
                    return x + self.conv_block(x)

            self.model0 = nn.Sequential(
                nn.ReflectionPad2d(3), nn.Conv2d(input_nc, 64, 7),
                norm_layer(64), nn.ReLU(inplace=True))
            model1, entrada, salida = [], 64, 128
            for _ in range(2):
                model1 += [nn.Conv2d(entrada, salida, 3, stride=2, padding=1),
                           norm_layer(salida), nn.ReLU(inplace=True)]
                entrada, salida = salida, salida * 2
            self.model1 = nn.Sequential(*model1)
            self.model2 = nn.Sequential(
                *[ResidualBlock(entrada) for _ in range(n_residual_blocks)])
            model3 = []
            salida = entrada // 2
            for _ in range(2):
                model3 += [nn.ConvTranspose2d(entrada, salida, 3, stride=2, padding=1,
                                              output_padding=1),
                           norm_layer(salida), nn.ReLU(inplace=True)]
                entrada, salida = salida, salida // 2
            self.model3 = nn.Sequential(*model3)
            self.model4 = nn.Sequential(
                nn.ReflectionPad2d(3), nn.Conv2d(64, output_nc, 7), nn.Sigmoid())

        def forward(self, x):
            return self.model4(self.model3(self.model2(self.model1(self.model0(x)))))

    return InformativeGenerator


def cargar_informative():
    pesos = _hf_descarga(REPO_INFORMATIVE, ARCHIVO_INFORMATIVE, tipo="space")
    modelo = _clase_informative()()
    modelo.load_state_dict(_torch_load(pesos))
    modelo.eval()
    return {"modelo": modelo.to(_dispositivo()), "bytes": os.path.getsize(pesos)}


def predecir_informative(obj, x_u8):
    import torch
    from torchvision.transforms import Compose, InterpolationMode, Resize, ToTensor

    tf = Compose([Resize((LADO_DPI, LADO_DPI), InterpolationMode.BICUBIC), ToTensor()])
    img = tf(_pil_rgb(x_u8))[None].to(_dispositivo())
    with torch.no_grad():
        out = obj["modelo"](img)[0, 0].cpu().numpy()
    return out


# --- 4 y 5. ControlNet Annotators (Lineart y PidiNet) -----------------------

def cargar_controlnet(nombre):
    from controlnet_aux import LineartDetector, PidiNetDetector

    if nombre == "lineart":
        det = LineartDetector.from_pretrained("lllyasviel/Annotators")
    elif nombre == "pidinet":
        det = PidiNetDetector.from_pretrained("lllyasviel/Annotators")
    else:
        raise ValueError(nombre)
    try:
        det = det.to(_dispositivo())
    except Exception:
        pass
    return {"det": det, "modelo": nombre, "bytes": 0}


def predecir_lineart(obj, x_u8):
    salida = obj["det"](_pil_rgb(x_u8), coarse=True,
                        detect_resolution=LADO_DPI, image_resolution=LADO_DPI)
    return np.asarray(salida.convert("L"), np.float32) / 255.0


def predecir_pidinet(obj, x_u8):
    salida = obj["det"](_pil_rgb(x_u8), safe=True,
                        detect_resolution=LADO_DPI, image_resolution=LADO_DPI)
    return np.asarray(salida.convert("L"), np.float32) / 255.0


# --- Registro ---------------------------------------------------------------

def registro(buril):
    """Devuelve la lista ordenada de modelos a comparar.

    `buril` es la ruta al `.tflite`; si no existe, Buril se saltea con aviso.
    """
    modelos = []
    modelos.append({
        "id": "zhangsuen", "nombre": "Zhang-Suen", "licencia": "Algoritmo (scikit-image, BSD-3)",
        "cargar": cargar_zhangsuen, "predecir": predecir_zhangsuen,
        "exportable": True,
    })
    if buril and os.path.exists(buril):
        modelos.append({
            "id": "buril", "nombre": "Buril (nuestro)", "licencia": "Propio (datos Quick, Draw! CC BY 4.0)",
            "cargar": lambda: cargar_buril(buril), "predecir": predecir_buril,
            "exportable": True,
        })
    modelos.append({
        "id": "simoserra", "nombre": "Simo-Serra (gan)", "licencia": "CC-BY-NC-SA (NO comercial)",
        "cargar": cargar_simo_serra, "predecir": predecir_simo_serra, "exportable": False,
    })
    modelos.append({
        "id": "anime2sketch", "nombre": "Anime2Sketch", "licencia": "MIT (código)",
        "cargar": cargar_anime2sketch, "predecir": predecir_anime2sketch, "exportable": False,
    })
    modelos.append({
        "id": "informative", "nombre": "Informative Drawings", "licencia": "MIT (código)",
        "cargar": cargar_informative, "predecir": predecir_informative, "exportable": False,
    })
    modelos.append({
        "id": "lineart", "nombre": "ControlNet Lineart", "licencia": "Apache-2.0",
        "cargar": lambda: cargar_controlnet("lineart"), "predecir": predecir_lineart,
        "exportable": False,
    })
    modelos.append({
        "id": "pidinet", "nombre": "PidiNet (soft edge)", "licencia": "Apache-2.0",
        "cargar": lambda: cargar_controlnet("pidinet"), "predecir": predecir_pidinet,
        "exportable": False,
    })
    return modelos


# --------------------------------------------------------------------------
# Ejecución del benchmark (en memoria; la usan el CLI y el cuaderno)
# --------------------------------------------------------------------------

def ejecutar(npz, buril, n=8, semilla=1, ids=None, log=print):
    """Corre todos los modelos sobre [n] muestras comunes y devuelve el informe.

    Devuelve un dict con `muestras` (entradas/GT), `predicciones` por modelo y
    `resultados` (métricas + metadatos). No escribe nada en disco.
    """
    equis, yes = cargar_muestras(npz, n, semilla)
    log(f"[datos] {len(equis)} muestras de validación de {npz}")

    predicciones = {}
    normalizadas = {}
    informe = {"npz": npz, "muestras": len(equis), "lado": int(equis.shape[1]), "modelos": {}}

    for m in registro(buril):
        if ids and m["id"] not in ids:
            continue
        nombre = m["nombre"]
        try:
            t0 = time.time()
            obj = m["cargar"]()
            t_carga = time.time() - t0
        except Exception as e:  # noqa: BLE001
            log(f"[{nombre}] NO DISPONIBLE: {type(e).__name__}: {e}")
            continue

        por_muestra, salidas, tiempos = [], [], []
        try:
            for i in range(len(equis)):
                t0 = time.time()
                tinta = np.asarray(m["predecir"](obj, equis[i]), np.float32)
                tiempos.append((time.time() - t0) * 1000.0)
                salidas.append(tinta)
                por_muestra.append(evaluar_muestra(tinta, yes[i]))
                log(f"[{nombre}] {i + 1}/{len(equis)}  "
                    f"IoU_esq={por_muestra[-1]['iou_esq']:.3f} "
                    f"clDice={por_muestra[-1]['cldice']:.3f} "
                    f"{tiempos[-1]:.0f} ms")
        except Exception as e:  # noqa: BLE001
            log(f"[{nombre}] FALLÓ durante la inferencia: {type(e).__name__}: {e}")
            continue

        predicciones[nombre] = salidas
        normalizadas[nombre] = [salida_normalizada(s) for s in salidas]
        informe["modelos"][nombre] = {
            "id": m["id"],
            "licencia": m["licencia"],
            "exportable_movil": m["exportable"],
            "peso_mb": obj.get("bytes", 0) / 1e6,
            "carga_s": t_carga,
            "latencia_ms": float(np.mean(tiempos)),
            "resumen": promediar(por_muestra),
            "por_muestra": por_muestra,
        }
        log(f"[{nombre}] listo: {informe['modelos'][nombre]['resumen']}")

        # Libera el modelo antes de cargar el siguiente (evita OOM con 5 pesos).
        del obj
        gc.collect()
        try:
            import torch
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
        except ImportError:
            pass

    informe["orden"] = [k for k in predicciones]
    return {"entradas": equis, "gt": yes, "predicciones": predicciones,
            "normalizadas": normalizadas, "informe": informe}


# --------------------------------------------------------------------------
# Presentación
# --------------------------------------------------------------------------

def imprimir_ranking(informe):
    """Imprime el ranking por clDice (con el resto de métricas al lado)."""
    print("\n" + "=" * 92)
    print(f"{'modelo':<24}{'clDice':>8}{'IoU_esq':>9}{'Dice_esq':>9}"
          f"{'IoU_bin':>9}{'prec':>7}{'recall':>8}{'PSNR':>7}{'ms':>7}{'MB':>8}")
    print("-" * 92)
    filas = sorted(informe["modelos"].items(),
                   key=lambda kv: kv[1]["resumen"]["cldice"], reverse=True)
    for nombre, d in filas:
        r = d["resumen"]
        print(f"{nombre:<24}{r['cldice']:>8.3f}{r['iou_esq']:>9.3f}{r['dice_esq']:>9.3f}"
              f"{r['iou_bin']:>9.3f}{r['precision']:>7.3f}{r['recall']:>8.3f}"
              f"{r['psnr']:>7.2f}{d['latencia_ms']:>7.0f}{d['peso_mb']:>8.1f}")
    print("=" * 92)


def dibujar_rejilla(entradas, gt, predicciones, ruta, max_muestras=4):
    """Rejilla PNG (entrada | cada modelo | GT) para inspección visual.

    [predicciones] debe venir **normalizada** (`salida_normalizada`), es decir lo
    que el usuario termina viendo tras el adelgazado de la app, no la salida
    cruda (banda/manchas) de cada modelo.
    """
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    nombres = list(predicciones.keys())
    n = min(max_muestras, len(entradas))
    columnas = 2 + len(nombres)
    fig, hachas = plt.subplots(n, columnas, figsize=(2.0 * columnas, 2.0 * n))
    if n == 1:
        hachas = hachas[None, :]
    for fila in range(n):
        paneles = [entradas[fila]] + [predicciones[k][fila] for k in nombres] + [gt[fila]]
        titulos = ["entrada"] + [f"{k} (norm)" for k in nombres] + ["GT (1 px)"]
        for col in range(columnas):
            ax = hachas[fila, col]
            ax.imshow(np.asarray(paneles[col]), cmap="gray")
            ax.set_xticks([])
            ax.set_yticks([])
            if fila == 0:
                ax.set_title(titulos[col], fontsize=8)
    fig.tight_layout()
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    fig.savefig(ruta, dpi=130)
    plt.close(fig)
    return ruta


def guardar(informe, rejilla, salida):
    os.makedirs(salida, exist_ok=True)
    ruta_json = os.path.join(salida, "resultados.json")
    with open(ruta_json, "w", encoding="utf-8") as f:
        json.dump(informe, f, ensure_ascii=False, indent=2)
    print(f"[salida] métricas: {ruta_json}")
    if rejilla:
        print(f"[salida] rejilla:  {rejilla}")
    return ruta_json


# --------------------------------------------------------------------------
# Autoprueba (sin red ni modelos)
# --------------------------------------------------------------------------

def autoprueba():
    """Chequeos de la lógica pura (métricas, polaridad, esqueleto)."""
    from skimage.morphology import skeletonize

    linea = np.zeros((64, 64), np.float32)
    for i in range(8, 56):
        linea[i, i] = 1.0
    linea = skeletonize(linea > 0.5).astype(np.float32)
    assert 40 <= linea.sum() <= 50, f"esqueleto diagonal inesperado: {linea.sum()}"
    assert esqueletizar(linea > 0.5).sum() == linea.sum(), "esqueleto no idempotente"

    grueso = np.zeros((64, 64), np.float32)
    grueso[10:54, 10:14] = 1.0
    assert esqueletizar(grueso > 0.5).sum() < grueso.sum(), "no adelgazó la banda"

    # La salida que se muestra en la rejilla es la normalizada (0/1 adelgazada).
    normal = salida_normalizada(grueso)
    assert set(np.unique(normal)).issubset({0.0, 1.0}), "normalizada no binaria"
    assert normal.sum() < grueso.sum(), "la normalización no adelgazó"

    # La misma línea, oscura sobre blanco y clara sobre negro, da la misma tinta.
    oscura = np.ones((64, 64), np.float32)
    oscura[linea > 0.5] = 0.0
    clara = np.zeros((64, 64), np.float32)
    clara[linea > 0.5] = 1.0
    assert np.allclose(a_tinta(oscura), a_tinta(clara), atol=1e-5), "polaridad"

    # Métricas: predicción perfecta = 1; ruido total = ~0.
    perfecto = evaluar_muestra(linea, linea)
    assert perfecto["iou_esq"] > 0.99 and perfecto["cldice"] > 0.99, perfecto
    vacio = evaluar_muestra(np.zeros((64, 64), np.float32), linea)
    assert vacio["cldice"] < 0.5, vacio

    print("autoprueba OK: esqueleto, polaridad y métricas funcionan.")


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def argumentos():
    p = argparse.ArgumentParser(description="Benchmark de limpieza de trazos (Buril vs. rivales).")
    p.add_argument("accion", choices=["todo", "autoprueba"], help="qué correr")
    p.add_argument("--pares", type=str, default=None, help=".npz con x, y y split")
    p.add_argument("--buril", type=str,
                   default=os.path.join(RAIZ, ".cache/modelos/limpiar_trazos.tflite"),
                   help="ruta al .tflite de Buril")
    p.add_argument("--muestras", type=int, default=8, help="muestras del split validación")
    p.add_argument("--semilla", type=int, default=1)
    p.add_argument("--modelos", type=str, default=None,
                   help="ids separados por coma (por defecto, todos)")
    p.add_argument("--salida", type=str, default=os.path.join(RAIZ, ".cache/benchmark"),
                   help="carpeta de resultados")
    return p.parse_args()


def main():
    args = argumentos()
    if args.accion == "autoprueba":
        autoprueba()
        return
    if not args.pares:
        raise SystemExit("Falta --pares <npz>.")
    ids = [s.strip() for s in args.modelos.split(",")] if args.modelos else None
    corrida = ejecutar(args.pares, args.buril, args.muestras, args.semilla, ids)
    informe = corrida["informe"]
    imprimir_ranking(informe)
    guardar(informe, None, args.salida)
    if corrida["predicciones"]:
        try:
            rejilla = dibujar_rejilla(corrida["entradas"], corrida["gt"],
                                      corrida["normalizadas"],
                                      os.path.join(args.salida, "rejilla.png"))
        except Exception as e:  # noqa: BLE001
            print(f"[aviso] rejilla no generada: {type(e).__name__}: {e}")
        else:
            print(f"[salida] rejilla:  {rejilla}")


if __name__ == "__main__":
    main()
