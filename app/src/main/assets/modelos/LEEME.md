# Modelos de IA local

`limpiar_trazos.tflite` (el modelo **Buril**) **no se versiona** (ver
`.gitignore`) para no engordar el repositorio. Se genera con
`tools/buril.py` sobre el dataset comercial del Plan B
(`tools/preparar_dataset_limpiar_trazos_planb.py`, Quick, Draw! de Google,
CC BY 4.0).

- Entrenamiento (recomendado): cuaderno de **Kaggle**
  `tools/buril_kaggle.ipynb` (driver fino sobre los scripts; *Save & Run All* en
  segundo plano, GPU T4 x2, sin corte por inactividad). El de Colab
  `tools/buril_colab.ipynb` queda para pruebas de otros modelos. La celda 1 pide
  la versión del `.npz` y la del modelo (por defecto `v1.0.0-experimental.1a`).
  Como respaldo, el workflow `.github/workflows/entrenar-limpiar-trazos-planb.yml`
  (CPU) y `entrenar-kaggle.yml` (lanza el kernel por la API).
- Arquitectura y entrenamiento: mini U-Net 32/64/128 (~466k parámetros, ~0,9 MB
  en float16); objetivo = **banda de 3 px** (`--banda`); pérdida **BCE + Dice +
  clDice**. Schedule en dos fases: **rápida** (lote 64, LR alto con calentamiento
  y coseno, 75 % de las 60 épocas) y **pulido** (lote 32, LR bajo). En GPU usa
  precisión mixta + XLA. El objetivo es una banda porque la app vuelve a
  adelgazar con Zhang-Suen.
- Puntos de control: `--checkpoints` guarda `ultimo.weights.h5`/`mejor.weights.h5`
  y `--reanudar` continúa; se reanudan desde la **release `checkpoints-buril`**.
- Buzón del modelo: Kaggle/Colab/Actions publican `limpiar_trazos.tflite` +
  `limpiar_trazos.json` en la **release `modelo-buril`** (fuera de git, con
  historial).
- Buzón del dataset: el `.npz` se cachea en la **release `dataset-buril`**
  (asset `pares_esq_lado{LADO}_pc{POR_CATEGORIA}_{VERSION}.npz`), así no se
  vuelve a bajar/preprocesar en cada sesión.
- Empaquetado: `.github/workflows/android-apk.yml` baja el modelo de esa release
  y lo copia a esta carpeta antes de `assembleDebug`.

Sin el archivo, `LimpiadorTrazosIa.disponible()` es `false` y la app cae al
adelgazado algorítmico de Zhang-Suen; el APK sigue funcionando.
