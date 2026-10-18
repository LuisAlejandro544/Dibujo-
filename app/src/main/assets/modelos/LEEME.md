# Modelos de IA local

`limpiar_trazos.tflite` **no se versiona** (ver `.gitignore`) para no engordar el
repositorio. Se genera con `tools/entrenar_limpiar_trazos.py` sobre el dataset
comercial del Plan B (`tools/preparar_dataset_limpiar_trazos_planb.py`, Quick,
Draw! de Google, CC BY 4.0).

- Entrenamiento: workflow `.github/workflows/entrenar-limpiar-trazos-planb.yml`
  (cachea el `.tflite` y lo sube como artefacto, fuera de git).
- Empaquetado: `.github/workflows/android-apk.yml` restaura ese cache y copia el
  modelo a esta carpeta antes de `assembleDebug`.

Sin el archivo, `LimpiadorTrazosIa.disponible()` es `false` y la app cae al
adelgazado algorítmico de Zhang-Suen; el APK sigue funcionando.
