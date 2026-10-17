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
| `core/src/main/kotlin/com/dibujo/core/` | Dominio: `Lienzo` (hoja ARGB ≤4096×4096), `MotorDibujo` (fachada), `Lapiz`, `Colores`, `Modo`, `Historial`, `ComandoLinea` (parser). |
| `engine/src/main/kotlin/com/dibujo/engine/` | `Motores.crear()` → `MotorNativo` (JNI) o `MotorKotlin` (respaldo); `NativeEngine` declara los `external`. |
| `engine/src/main/cpp/` | C++17: `DibujoEngine` (núcleo), `dibujo_engine.cpp` (JNI), `CMakeLists.txt` (JDK en escritorio, NDK en Android). |
| `ui/` | Consola ANSI: `Main.kt`, `ControladorConsola`, `VistaHoja`, `Menu`. |
| `app/` | Android: `MainActivity`, `PantallaDibujo` (Compose M3), `DibujoViewModel` (dueño del motor), manifest, recursos e adaptive icon. |
| `.github/workflows/` | `android-apk.yml` (assembleDebug manual) y `build-debug-apk.yml` (APK empaquetado a mano + consola Linux). |

## Semántica de cámara (crítica, no re-derivar)

`renderizar(destino, vw, vh)` mapea pantalla→hoja como
**hoja = (pantalla + pan) / zoom**. Derivar de ahí:
- pan con dedo que arrastra contenido: `motor.pan(-dx, -dy)`.
- zoom anclado al foco: ajustar `pan` después de `zoom()` con la fórmula
  `pan = hojaFoco * zoomNuevo − focoPantalla`.
La app Android usa exactamente esa conversión; si la cambiás, actualizar los
dos motores y el test de paridad juntos.

## Comandos

```bash
./gradlew build            # todo + tests + motor nativo de escritorio (cmake)
./gradlew :app:assembleDebug  # APK Android (motor C++ via NDK dentro del APK)
./gradlew :ui:run          # consola
```

Toolchain: JDK 17, Gradle 9.8.1 (wrapper), Kotlin 2.4.21, AGP 9.4.1,
Compose BOM 2026.09.00, compileSdk 37 / minSdk 26 / targetSdk 37,
nDevice ABIs: arm64-v8a + x86_64.

## Reglas duras (resumen de AGENTS.md)

1. Español en todo (código, strings, docs, commits).
2. MVI estricto en ViewModel: `_estado.update { it.copy(...) }`, Nunca asignación directa.
3. Cero texto duro en UI Compose: `stringResource`.
4. Los cambios de rasterizado van SIEMPRE por pares C++/Kotlin + test de paridad.
5. No commitear sin pedirlo; no tocar `gradlew`/wrapper ni versions sin motivo.
