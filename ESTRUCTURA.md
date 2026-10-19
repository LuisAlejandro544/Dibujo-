# ESTRUCTURA — Mapa del proyecto Dibujo

Documento de estructura del repositorio: qué módulo hace qué, dónde vive cada
pieza y dónde tocar según el tipo de cambio. Para el detalle técnico fino ver
`AI_CONTEXT.md`; para el resumen ultra-rápido, `README_AI.md`.

## 1. Resumen

App de dibujo con **motor dual en paridad**: un núcleo en **C++17 / JNI**
(`MotorNativo`) y un respaldo en **Kotlin puro** (`MotorKotlin`). Dos
front-ends consumen el mismo dominio: una **consola JVM** (`:ui`) y una app
**Android con Jetpack Compose** (`:app`). Regla de oro: todo lo que rasteriza
se implementa en C++ **y** Kotlin, y pasa `MotorTest` (paridad píxel a píxel).

## 2. Módulos (Gradle)

`settings.gradle.kts` incluye cuatro módulos:

| Módulo | Rol | Depende de |
|---|---|---|
| `:core` | Dominio puro, sin Android/IO/JNI. Cero dependencias externas. | — |
| `:engine` | Motores `MotorKotlin` + `MotorNativo` (JNI) y la fachada `Motores.crear()`. Trae el C++ y las tareas `configureNative`/`buildNative` de escritorio. | `:core` |
| `:ui` | Front-end de consola JVM. | `:core`, `:engine` |
| `:app` | Front-end Android (Compose Material 3, AGP 9 con *built-in Kotlin*). | `:core`, `:engine` |

```
┌─────────────────────────┐  ┌──────────────────────────┐
│  :ui  (consola JVM)     │  │  :app (Android Compose)  │
│  ControladorConsola +   │  │  PantallaMenu/Dibujo +   │
│  VistaHoja + Menu       │  │  DibujoViewModel         │
└───────────┬─────────────┘  └───────────┬──────────────┘
            │  consumen                  │
            ▼                            ▼
        ┌──────────────────────────────────┐
        │  :engine  Motores.crear()        │
        │    → MotorNativo (JNI / C++)     │
        │    → MotorKotlin (respaldo)      │
        └───────────────┬──────────────────┘
                        ▼
        ┌──────────────────────────────────┐
        │  :core  Lienzo, Lapiz, Color,    │
        │  Historial, MotorDibujo, Modo,   │
        │  Cuantizador, BackendRender      │
        └──────────────────────────────────┘
```

## 3. `:core` — dominio puro

`core/src/main/kotlin/com/dibujo/core/`

- `MotorDibujo.kt` — **fachada** que consumen los front-ends: trazo
  (iniciar/continuar/soltar/cancelar), hoja (`ancho`/`alto`, `copiarHoja`,
  `reemplazarHoja`, `pixelHoja`), historial, cámara, render, `asistenciaRecta`,
  `transformarHoja`.
- `Lienzo.kt` — rasterizado puro (disco/línea/bloque con antialiasing,
  `pintarMezcla`, relleno balde, cierre de huecos, `transformar`).
- `Lapiz.kt` — `Herramienta` (LAPIZ, PORTAMINA, CARBONCILLO, BORRADOR, PINTAR,
  DOBLE) y `grosorEfectivo()`.
- `Color.kt` — `Color` y `Colores` (paleta fija). `Modo.kt` — EDICION/MOVER.
- `Historial.kt` — undo/redo por snapshots (límite **30**).
- `Cuantizador.kt` — k-medias determinista para la **paleta del calco**.
- `BackendRender.kt` — enum del backend gráfico (CPU/OpenGL/Vulkan).
- `ComandoLinea.kt` — parser de comandos (usado por la consola).
- `core/src/test/.../DibujoCoreTest.kt` — tests del dominio.

## 4. `:engine` — motor dual

`engine/src/main/kotlin/com/dibujo/engine/`

- `Motores.kt` — `crear()`: intenta el nativo y cae a `MotorKotlin`.
- `MotorKotlin.kt` — implementación de respaldo (paridad exacta con el C++).
- `MotorNativo.kt` — implementación JNI sobre `NativeEngine`.
- `NativeEngine.kt` — `external fun nativo*` (puente a JNI).

`engine/src/main/cpp/`

- `DibujoEngine.h` / `DibujoEngine.cpp` — núcleo C++17 (espejo del `Lienzo`).
- `dibujo_engine.cpp` — implementación de los símbolos JNI.
- `Types.h` — tipos comunes.
- `gpu/` — render de **presentación** (la hoja como textura, cámara en shader):
  `RenderizadorGL.{h,cpp}` (EGL+GLES3), `RenderizadorVulkan.{h,cpp}`,
  `RenderizadorGpu.{h,cpp}` (interfaz), `GpuJni.cpp`, `ParametrosPresentacion.h`,
  `Registro.h`.
- `CMakeLists.txt` — construcción (rama `if(ANDROID)` para el NDK).
- `engine/src/test/.../MotorTest.kt` — **paridad C++ ↔ Kotlin** píxel a píxel.

### Paridad obligatoria
Si tocás rasterizado, cámara, historial o herramientas: editá **los dos lados**
(`Lienzo.kt` ↔ `DibujoEngine.cpp`) y ampliá `MotorTest`. Redondeo unificado
`floor(v + 0.5)` con aritmética `double`.

## 5. `:ui` — consola JVM

`ui/src/main/kotlin/com/dibujo/ui/`

- `Main.kt`, `Menu.kt`, `ControladorConsola.kt`, `VistaHoja.kt` (vista ANSI con
  colores), `ui/src/test/.../ControladorConsolaTest.kt`.

## 6. `:app` — Android (Compose)

`app/src/main/kotlin/com/dibujo/android/`

- `MainActivity`/navegación: `Navegacion.kt`, `PantallaMenu.kt`,
  `PantallaCargar.kt`, `PantallaOpciones.kt`.
- `PantallaDibujo.kt` — pantalla del lienzo: barras superior/inferior, gestos
  (`CapaGestos`/`CapaCalco`), `DialogoTransformar` (voltear/escalar + ajuste
  libre por gestos), `DialogoColor`, etc.
- `RuedaHsv.kt` — rueda de color HSV (`DialogoColor` + `RuedaHsv` privada).
- `DibujoViewModel.kt` — **dueño del motor** y del estado (MVI estricto con
  `_estado.update { copy() }`): lápiz, paleta, recientes, cuentagotas,
  asistencia, transformar, historial, cámara, calco.
- `SuperficieGpu.kt` — `SurfaceView` + hilo de render del backend gráfico.
- `CapacidadesGraficas.kt` — detección de OpenGL ES / Vulkan.
- `AlmacenDibujos.kt` — persistencia (PNG en `filesDir/dibujos`, sin permisos).
- `ImagenCalco.kt` — calco (Photo Picker, recorte al contenido).
- `ExportadorGaleria.kt` — guardar/compartir PNG (`FileProvider`).
- `LimpiadorTrazosIa.kt` — modelo IA local (Buril) vía LiteRT.
- `IconosDibujo.kt` — iconos propios.
- `app/src/debug/kotlin/.../debug/` — panel y monitor de depuración (solo debug).
- `app/src/main/res/values/strings.xml` — **todos** los textos (cero texto duro).
- `app/src/main/assets/modelos/` — pesos `.tflite` (no se versionan; los inyecta
  CI desde la release `modelo-buril`).

## 7. Datos, herramientas y CI

- `tools/` — scripts de IA/datasets: `buril.py`, `buril_colab.ipynb`,
  `benchmark_limpiar_trazos.{py,ipynb}`, `preparar_dataset_*`, JSON de datasets.
- `.github/workflows/` — `android-apk.yml` (APK debug), `opencode.yml`,
  `entrenar-*` y `preparar-dataset-*`.
- `gradle/libs.versions.toml` — **fuente única de versiones**; al agregar una
  dependencia, verificá Maven y documentala en `AI_CONTEXT.md`.
- `AI_CONTEXT.md`, `README_AI.md`, `ROADMAP.md`, `AGENTS.md`, este archivo.

## 8. Build y verificación

```bash
./gradlew build               # todo + tests (incluye motor nativo de escritorio)
./gradlew test                # tests de todos los módulos
./gradlew :engine:test        # paridad C++ ↔ Kotlin (MotorTest)
./gradlew :app:assembleDebug  # APK Android debug
./gradlew :ui:run             # app de consola
```

Restricciones de build: **AGP 9 con built-in Kotlin** (no aplicar
`org.jetbrains.kotlin.android` en `:app`), `minSdk 26`, ABIs arm64-v8a +
x86_64, NDK `27.3.13750724` / CMake 3.31.5.

## 9. Dónde tocar según el cambio

| Quiero… | Archivos |
|---|---|
| Cambiar rasterizado | `core/.../Lienzo.kt` **+** `engine/src/main/cpp/DibujoEngine.cpp` **+** `MotorTest.kt` |
| Nueva herramienta de trazo | `core/.../Lapiz.kt`, ambos motores, `PantallaDibujo.kt`, `strings.xml` |
| Nueva operación del motor | `core/.../MotorDibujo.kt`, `MotorKotlin`, `MotorNativo`+`NativeEngine`, C++ (`DibujoEngine.*`, `dibujo_engine.cpp`), `MotorTest` |
| Estado/acciones de UI | `app/.../DibujoViewModel.kt` (MVI: `_estado.update { copy() }`) |
| UI / diálogos | `app/.../PantallaDibujo.kt` + `strings.xml` (cero texto duro) |
| Render GPU (presentación) | `engine/src/main/cpp/gpu/*`, `app/.../SuperficieGpu.kt` |
| IA local (Buril) | `app/.../LimpiadorTrazosIa.kt`, `tools/buril.py`, assets `modelos/` |
