# README_AI — Dibujo

Resumen condensado para IAs (LLMs, coding agents). La fuente completa está en
`README.md`, `AI_CONTEXT.md` y `AGENTS.md`. Responde siempre en español.

## Qué es

App de dibujo con motor doble (C++ nativo + Kotlin de respaldo, paridad
garantizada por tests). Dos front-ends: consola JVM (`:ui`) y Android Compose
(`:app`). El dominio vive en `:core`.

## Mapa rápido

| Ruta | Contenido |
|---|---|
| `core/src/main/kotlin/com/dibujo/core/` | Dominio: `Lienzo` (hoja ARGB ≤4096×4096, disco con antialiasing, `limpiarTrazos` con Zhang-Suen), `MotorDibujo` (fachada: trazado, cámara con rotación, expandir, copiarHoja, reemplazarHoja, limpiarTrazos), `Lapiz` (herramientas LAPIZ/PORTAMINA/CARBONCILLO/BORRADOR/PINTAR/DOBLE), `Colores` (58 colores, con neón/metálicos), `Cuantizador` (k-medias para la paleta del calco), `Modo`, `Historial`, `BackendRender`, `ComandoLinea` (parser). |
| `engine/src/main/kotlin/com/dibujo/engine/` | `Motores.crear()` → `MotorNativo` (JNI) o `MotorKotlin` (respaldo); `NativeEngine` declara los `external`. |
| `engine/src/main/cpp/` | C++17: `DibujoEngine` (núcleo, disco antialiased), `dibujo_engine.cpp` (JNI), `gpu/` (`RenderizadorGL`, `RenderizadorVulkan`, `GpuJni.cpp`, shaders; sólo Android), `CMakeLists.txt` (JDK en escritorio, NDK en Android). |
| `ui/` | Consola ANSI: `Main.kt`, `ControladorConsola` (`r`/`,` giran la hoja), `VistaHoja`, `Menu`. |
| `app/` | Android: `MainActivity`, `PantallaDibujo` (Compose M3, edge-to-edge + fuente fija), `SuperficieGpu` (SurfaceView + hilo de render GPU), `DibujoViewModel` (incluye `limpiarTrazos`, `limpiarTrazosConModelo` y `analizarCalco`), `LimpiadorTrazosIa` (modelo chico `.tflite` con LiteRT en `app/src/main/assets/modelos/`), `CapacidadesGraficas` (detección Vulkan 1.1 / OpenGL ES), `ExportadorGaleria` (guardar PNG en galería vía MediaStore, con diálogo de peso y compartir vía FileProvider), `IconosDibujo` (iconos propios de borrador y de la herramienta DOBLE), `ImagenCalco` (decodificación del calco), manifest, recursos e adaptive icon. |
| `app/src/debug/` | Solo variant debug: `PanelDebugActivity` (icono propio en el launcher), `MonitorDibujo` (RAM/hilos/crashes), `CapturaLogs`, `MonitorProvider` y LeakCanary 2.14. No existe en release. |
| `.github/workflows/` | `android-apk.yml` (assembleDebug manual y en PRs contra main). |

## Semántica de cámara (crítica, no re-derivar)

`renderizar(destino, vw, vh)` mapea pantalla→hoja como
**hoja = R(−rot)·(pantalla + pan) / zoom** (o sea
**pantalla = R(rot)·(hoja·zoom) − pan**; con `rot = 0` es `(pantalla + pan)/zoom`).
Derivar de ahí:
- pan con dedo que arrastra contenido: `motor.pan(-dx, -dy)`.
- zoom anclado al foco: ajustar `pan` después de `zoom()` con la fórmula
  `pan = R(rot)·(hojaFoco·zoomNuevo) − focoPantalla`.
- rotación anclada: `motor.rotar(delta)` y reanclar el pan igual; `centrarVista`
  resetea `rot = 0`. Degradados a `(-π, π]`.
Zoom de `0.05` a `64` (`ZOOM_MIN`/`ZOOM_MAX` = `kZoomMin`/`kZoomMax`). La app
Android usa exactamente esa conversión; si la cambiás, actualizar los dos
motores y el test de paridad juntos.

## Comandos

```bash
./gradlew build            # todo + tests + motor nativo de escritorio (cmake)
./gradlew :app:assembleDebug  # APK Android (motor C++ via NDK dentro del APK)
./gradlew :ui:run          # consola
```

Toolchain: JDK 17, Gradle 9.8.1 (wrapper), Kotlin 2.4.21, AGP 9.4.1,
Compose BOM 2026.09.00, LiteRT (`com.google.ai.edge.litert:litert`) 2.3.0,
compileSdk 37 / minSdk 26 / targetSdk 37, ABIs: arm64-v8a + x86_64.

## Backend gráfico

`BackendRender` (`:core`: `AUTOMATICO` / `OPENGL` / `VULKAN`) + detección en
`CapacidadesGraficas` (`:app`). El diálogo de configuración (engranaje en la
barra superior) permite cambiarlo; Vulkan se bloquea sin soporte 1.1 y la
preferencia se guarda en `SharedPreferences`. El render GPU real ya existe:
`engine/src/main/cpp/gpu/` (EGL+GLES3 y Vulkan) presenta la hoja como textura
y mueve la cámara al shader; `SuperficieGpu` (`:app`) la muestra en un
`SurfaceView` con hilo propio y, si falla, la app cae a `LienzoCpu`. La hoja
ocupa toda la ventana (barras encima) para que superficie GPU, gestos y motor
compartan origen y el trazo caiga donde se dibuja. Hay pantalla de carga
(`gpuCargando`) mientras el renderizador inicia o se cambia de backend, y la
primera subida de la hoja se hace con revisión 0 (si no, el lienzo queda negro
hasta el primer trazo). El rasterizado del trazo sigue en C++/Kotlin (paridad
`MotorTest` intacta).

## Reglas duras (resumen de AGENTS.md)

1. Español en todo (código, strings, docs, commits).
2. MVI estricto en ViewModel: `_estado.update { it.copy(...) }`, Nunca asignación directa.
3. Cero texto duro en UI Compose: `stringResource`.
4. Los cambios de rasterizado van SIEMPRE por pares C++/Kotlin + test de paridad.
5. No commitear sin pedirlo; no tocar `gradlew`/wrapper ni versions sin motivo.
