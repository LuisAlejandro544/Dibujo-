# Modelos de IA local

`limpiar_trazos.tflite` (el modelo **Buril**) **no se versiona** (ver
`.gitignore`) para no engordar el repositorio. Se genera con
`tools/buril.py` sobre el dataset comercial del Plan B
(`tools/preparar_dataset_limpiar_trazos_planb.py`, Quick, Draw! de Google,
CC BY 4.0).

- Entrenamiento (recomendado): cuaderno de Google Colab
  `tools/buril_colab.ipynb` (driver fino sobre los scripts).
  Como respaldo, el workflow `.github/workflows/entrenar-limpiar-trazos-planb.yml`.
- Buzón: los dos publican `limpiar_trazos.tflite` + `limpiar_trazos.json` en la
  **release `modelo-buril`** (fuera de git, con historial).
- Empaquetado: `.github/workflows/android-apk.yml` baja el modelo de esa release
  y lo copia a esta carpeta antes de `assembleDebug`.

Sin el archivo, `LimpiadorTrazosIa.disponible()` es `false` y la app cae al
adelgazado algorítmico de Zhang-Suen; el APK sigue funcionando.
