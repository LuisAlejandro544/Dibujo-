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
        │  Historial, MotorDibujo, Camara, │
        │  BackendRender                   │
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
- `grosorEfectivo`: PORTAMINA `grosor/2`, CARBONCILLO `grosor+2`, BORRADOR `grosor+4`
  (la fórmula vive en `Lapiz.grosorEfectivo()`; el C++ la replica).
- **Antialiasing**: `disco()` calcula la cobertura de cada píxel como
  `0.5 + radio − distancia` (distancia en `Double` y `sqrt` correctamente
  redondeado en ambos lados) y mezcla con el fondo con `pintarMezcla` /
  `pintarMezcla()` (aritmética entera idéntica). Por eso los trazos tienen
  borde suave y `Lienzo.pintarMezcla` no debe "optimizarse" en un solo lado.
- BORRADOR pinta con el color de fondo de la hoja; la vista previa de Compose
  lo imita (fondo + grosor efectivo) para que el borrado se vea al instante.
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

### Historial
Undo/redo por snapshots completos de la hoja. Límite de pasos: **30**
(`Historial` en Kotlin y `kMaxHistorial` en C++ tienen que coincidir). El
estado Compose expone `puedeDeshacer`/`puedeRehacer` (leídos del motor
después de cada operación) y habilita/deshabilita los botones. Importante:
el `Canvas` lee el `State` dentro del `draw` (`estadoState.value`), no sólo
durante la composición; si no, undo/redo cambian el motor pero no repintan
(era el bug "se unde y no hace nada").

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
- **Edge-to-edge** desde la Activity (`enableEdgeToEdge()`), `Scaffold` con
  `TopAppBar` (título + motor + deshacer/rehacer/centrar/limpiar/configuración)
  y barra inferior con herramientas/chips de modo/grosor y la paleta.
  Particularidades que costaron bugs reales:
  - Las barras del sistema usan `SystemBarStyle.light(...)`: el tema es claro
    y, con el teléfono en modo oscuro, `auto()` dejaba hora/batería blancas
    sobre fondo claro (invisibles).
  - La barra inferior lleva `windowInsetsPadding(WindowInsets.navigationBars)`;
    sin eso la paleta quedaba debajo de la barra de navegación y no se tocaba.
  - `TemaDibujo` fija `fontScale = 1` en `LocalDensity`: el tamaño de letra
    no depende de la configuración de Android del usuario.
- **Material Icons**: `androidx.compose.material:material-icons-extended`
  (BOM, iconos Rounded; deshacer/rehacer vía `Icons.AutoMirrored`). Sin
  símbolos de texto: cada icono lleva `contentDescription` desde strings.xml.
- **Cero texto duro**: todo por `stringResource`.
- Icono de app: adaptive icon XML (sin PNG) con `monochrome`.

### Backend gráfico (OpenGL ES / Vulkan)
`BackendRender` (`:core`) es el enum elegible: `AUTOMATICO`, `OPENGL`,
`VULKAN`. `CapacidadesGraficas` (`:app`) detecta el soporte real (Vulkan 1.1
vía `FEATURE_VULKAN_HARDWARE_VERSION`; OpenGL ES vía
`ActivityManager.deviceConfigurationInfo.glEsVersion`). El diálogo de
configuración (icono de engranaje en la barra superior) permite elegir el
backend; **Vulkan queda bloqueado si el equipo no lo soporta** y el menú
sigue existiendo. La elección se persiste en `SharedPreferences`
(`dibujo_config/backend_render`) y `resolver()` cae a OpenGL cuando
corresponde. Hoy el rasterizado sigue en C++/Kotlin; el render GPU real es
la tarea #3 del ROADMAP (la selección ya está cableada en el estado).

## 6. Riesgos conocidos / no resueltos

- El trazo en curso se previsualiza con Path sin rasterizar por el motor: al
  soltar, el resultado exacto (disco/bloque/línea gruesa) puede diferir levemente del overlay.
- `MotorKotlin.renderizar` es O(vw*vh) por frame en CPU; en pantallas grandes
  conviene eventualmente mover el render a textura (ver ROADMAP). La selección
  de backend (OpenGL ES / Vulkan) ya existe en la UI, pero el render GPU que
  la usaría todavía no está implementado: hoy el rasterizado corre igual en
  C++/Kotlin y la preferencia queda persistida.
- No hay persistencia de la hoja (guardar PNG/deshacer entre sesiones): es la
  tarea #1 del ROADMAP.
- Release de `:app` no está firmado para distribución (solo debug).
- El historial es por snapshots: 30 pasos (compartidos C++/Kotlin) para
  acotar la RAM del dispositivo (ver "Historial").

## 7. Datos útiles para agentes

- Horario del usuario: America/Caracas; respondé en español nativo, fixeáte en `opencode.json`.
- Tests: JUnit Platform; paridad en `engine/src/test/kotlin/com/dibujo/engine/MotorTest.kt` (incluye paridad de undo/redo con `historialParidad`). El antialiasing entra en esa paridad: si tocás `disco`/`pintarMezcla` en un lado, corré `:engine:test`.
- El workflow `android-apk.yml` corre manual (`workflow_dispatch`) y en cada PR contra main; el workflow falso `build-debug-apk.yml` (APK placeholder aapt2/d8) fue eliminado.
- Commits: esperar orden explícita del usuario (nunca commit proactivo).
