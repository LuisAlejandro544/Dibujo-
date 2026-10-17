# AI_CONTEXT — Contexto técnico de Dibujo

Documento de contexto para asistentes de IA y personas nuevas. Complementa
`README.md` (qué es, cómo correr) y `AGENTS.md` (cómo trabajar). Todo en español.

## 1. Visión

**Dibujo** es una app de dibujo portátil: una hoja grande, lápices (color,
grosor, punta, herramienta) y una cámara con pan/zoom. El motor de dibujo
tiene **dos implementaciones equivalentes** que los tests mantienen en
paridad, para poder ser nativo (C++) donde exista y puro Kotlin donde no:

- `DibujoEngine` (C++17, `engine/src/main/cpp/`) — usada vía JNI (`MotorNativo`).
- `MotorKotlin` (respaldo puro Kotlin) — siempre disponible.

`Motores.crear(ancho, alto)` elige solo; la UI nunca pregunta cuál corre.

## 2. Arquitectura en capas

```
┌─────────────────────────┐  ┌──────────────────────────┐
│  :ui  (consola JVM)     │  │  :app (Android Compose)  │
│  ControladorConsola +   │  │  MainActivity +          │
│  VistaHoja + Menu       │  │  PantallaDibujo +        │
└───────────┬─────────────┘  │  DibujoViewModel         │
            │  consumen      └───────────┬──────────────┘
            ▼                            ▼
        ┌──────────────────────────────────┐
        │  com.dibujo.engine               │
        │  Motores.crear() →               │
        │    MotorNativo (JNI)             │
        │    MotorKotlin  (respaldo)       │
        └───────────────┬──────────────────┘
                        ▼
        ┌──────────────────────────────────┐
        │  com.dibujo.core (domino puro)   │
        │  Lienzo, Lapiz, Colores, Modo,   │
        │  Historial, MotorDibujo, Camara  │
        └──────────────────────────────────┘
```

- `:core` no depende de nada (JNI/Android/IO cero). Todo lo visual consume
  `MotorDibujo` (la fachada) más `Camara` para mapear coordenadas.
- El C++ no conoce el Kotlin y viceversa: comparten **semántica**, no código.

## 3. Semánticas que hay que respetar (por qué existen esos tests)

### Paridad C++ ↔ Kotlin
Cualquier cambio en el rasterizado (disco, bloque, línea gruesa, relleno,
grosor efectivo por herramienta, cámara, render) debe hacerse en las DOS
implementaciones y pasar `MotorTest` (paridad píxel a píxel). Las diferencias:
- `grosorEfectivo`: PORTAMINA `grosor/2`, CARBONCILLO `grosor+2`, BORRADOR `grosor+4`.
- BORRADOR pinta con el color de fondo de la hoja.
- FUERA de hoja en render = gris `0xFFECECEC`.

### Cámara
`renderizar()` define la transformación canonical:
**pantalla = hoja * zoom − pan**, o sea **hoja = (pantalla + pan) / zoom**.
De ahí salen (ver `README_AI.md`):
- pan natural: `motor.pan(-dx, -dy)` para que el contenido siga al dedo.
- zoom anclado: tras `motor.zoom(f)`, corregir `pan` con
  `pan = hojaFoco * zoomNuevo − focoPantalla` (así lo hace `DibujoViewModel.zoomear`).

### Hoja
`Lienzo` admite 1..4096 por lado, ARGB, fondo configurable (BLANCO por defecto).
En Android el ViewModel crea el motor con el tamaño físico de la pantalla
(px), acotado a `MAX_LADO = 4096`. El motor NO se recrea ante rotaciones:
vive en el `ViewModel` (la hoja sobrevive cambios de configuración).

## 4. Build

| Pieza | Valor |
|---|---|
| Gradle | 9.8.1 (wrapper, `gradlew`) |
| Kotlin | 2.4.21 (root `build.gradle.kts`; alias en `gradle/libs.versions.toml`) |
| AGP | 9.4.1 (con *built-in Kotlin*: NO aplicar `org.jetbrains.kotlin.android` en `:app`) |
| Compose | BOM 2026.09.00 + `kotlin.plugin.compose` 2.4.21 |
| SDK | compileSdk 37, minSdk 26, targetSdk 37, ABIs arm64-v8a + x86_64 |
| NDK en `:app` | `externalNativeBuild` → `engine/src/main/cpp/CMakeLists.txt` (versión CMake 3.31.5, `ndkVersion 27.3.13750724`) |
| Motor escritorio | tareas `configureNative`/`buildNative` de `:engine` (requiere cmake + g++; si no hay, tests y app usan el respaldo) |

Particularidades:
- `CMakeLists.txt` tiene rama `if(ANDROID)` porque en NDK `jni.h` viene del
  sysroot (no hace falta `JDK_HOME`).
- AGP 9 rechaza `kotlin-android`; en `:app` se usan `plugin.compose` + built-in Kotlin.
- `libs.versions.toml` es la fuente de versiones; en el root los plugins van
  con `apply false` para poder aplicarse por alias en los subproyectos.

## 5. UI Android: decisiones

- **ViewModel dueño del motor** (`DibujoViewModel`): estado `StateFlow`
  actualizado solo con `_estado.update { copy() }` (MVI). Eventos: iniciar/
  continuar/soltar/cancelar trazo, pan/zoom, lápiz (color/herramienta/grosor),
  deshacer, rehacer, limpiar, centrar.
- **Render**: `Canvas` de Compose pinta un `Bitmap` (buffer `IntArray`)
  rasterizado por `motor.renderizar(buffer, vw, vh)` + overlay del trazo en
  curso (Path con la misma transformación de cámara) para feedback inmediato.
- **Gestos**: `detectDragGestures` (EDICION) y `detectTransformGestures`
  con foco (MOVER), en `pointerInput(estado.modo)`.
- **Edge-to-edge** desde la Activity (`enableEdgeToEdge()`), `Scaffold` con insets.
- **Cero texto duro**: todo por `stringResource`; símbolos (↶ ↷ ◎ ✕ –) en `strings.xml`.
- Icono: adaptive icon XML (sin PNG) con `monochrome`.

## 6. Riesgos conocidos / no resueltos

- El trazo en curso se previsualiza con Path sin rasterizar por el motor: al
  soltar, el resultado exacto (disco/bloque/línea gruesa) puede diferir levemente del overlay.
- `MotorKotlin.renderizar` es O(vw*vh) por frame en CPU; en pantallas grandes
  conviene eventualmente mover el render a textura (ver ROADMAP).
- `build-debug-apk.yml` empaqueta a mano un APK placeholder que inicial la
  consola (`:ui`) en el dispositivo: con `:app` ya existe el APK real vía AGP;
  ese workflow queda como fallback histórico.
- No hay persistencia de la hoja (guardar PNG/deshacer entre sesiones): es la
  tarea #1 del ROADMAP.
- Release de `:app` no está firmado para distribución (solo debug).

## 7. Datos útiles para agentes

- Horario del usuario: America/Caracas; respondé en español nativo, fixeáte en `opencode.json`.
- Tests: JUnit Platform; paridad en `engine/src/test/kotlin/com/dibujo/engine/MotorTest.kt`.
- El workflow `android-apk.yml` corre solo manual (`workflow_dispatch`).
- Commits: esperar orden explícita del usuario (nunca commit proactivo).
