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

La hoja se puede **expandir** en cualquier momento (`MotorDibujo.expandir`):
agrega píxeles nuevos (fondo) por cada lado sin mover el contenido, acotado a
`Lienzo.MAX_LADO`. Los dos motores limpian el historial al expandir (los
snapshots viejos tienen otras dimensiones). En Android el render GPU se recrea
al cambiar el tamaño (la textura se asigna con las dimensiones del motor), así
que `hojaAncho`/`hojaAlto` forman parte de la `key` de `SuperficieGpu`.

### Herramienta PINTAR (balde)
`Herramienta.PINTAR` (ordinal 4, mismo orden que `Tool::PINTAR` en C++) no
traza: rellena por inundación (4 vecinos, igualdad exacta de color) desde el
punto tocado con el color del lápiz. `Lienzo.rellenar` y la función `rellenar`
del C++ deben mantener la paridad del conjunto rellenado. No usa grosor ni
punta. En la app, un toque sin arrastre dispara `DibujoViewModel.tocarHoja`
(punto con el lápiz, relleno con PINTAR).

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
| LeakCanary | 2.14 (solo `debugImplementation` en `:app`) |
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
- **Barra inferior sin scroll incómodo** (issue 18): se pasó de chips de texto
  en una fila deslizable a tres filas fijas → modo + acciones (calco/expandir)
  + grosor de herramientas (iconos compactos `IconoToggle`, sin scroll) +
  paleta deslizable. La paleta creció a 29 colores (`Colores.PALETA`); los
  primeros 12 conservan su índice (la consola y sus tests los eligen por
  número), los nuevos van al final.
- **Imagen de calco** (issue 18): `CapaCalco` es una capa Compose translúcida
  sobre la hoja (GPU o CPU) y debajo del trazo; la imagen se ajusta dentro de
  la hoja y sigue la cámara. Se elige con el Photo Picker (sin permisos), se
  muestra/oculta al instante (`calcoVisible`) y tiene opacidad ajustable. El
  `calcoUri` vive en el `ViewModel` (sobrevive rotaciones) y el `Bitmap` se
  decodifica reducido (`MAX_CALCO = 2048`) al componer.
- **Expandir hoja** (issue 18): diálogo con sliders de ancho/alto a añadir
  (hasta 4096 por lado); `DibujoViewModel.expandirHoja` llama a
  `MotorDibujo.expandir(0, 0, ancho, alto)`.
- **Exportar a la galería** (issue 22): botón de guardar en la barra superior.
  `MotorDibujo.copiarHoja(destino)` vuelca la hoja COMPLETA (ancho*alto ARGB,
  sin cámara) y en Android se arma un `Bitmap` ARGB_8888; el copiado y la
  compresión corren fuera del hilo principal. `ExportadorGaleria` (`:app`)
  guarda con `MediaStore` (`RELATIVE_PATH=Pictures/Dibujo`, `IS_PENDING`) en
  API 29+, sin permisos; en API 26–28 escribe en `Pictures/Dibujo` y pide
  `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`). `copiarHoja` NO es
  rasterizado: copia el mismo buffer en C++ (`DibujoEngine::copiarHoja`, ya
  existía para el render GPU) y en Kotlin, cubierto por el test de paridad.
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
corresponde.

**Render GPU real (en `engine/src/main/cpp/gpu/`, sólo Android):**
- `RenderizadorGpu` es la interfaz + fábrica `crear(Backend, ANativeWindow*,
  DibujoEngine*)`; devuelve `nullptr` si el backend no arranca.
- `RenderizadorGL` monta EGL + contexto OpenGL ES 3.x y presenta con
  `glBindVertexArray` (por eso se enlaza `libGLESv3`, no `libGLESv2`, que sólo
  expone VAOs con sufijo `OES`).
- `RenderizadorVulkan` monta instancia/dispositivo/swapchain/render pass y
  presenta; recrea la cadena ante `VK_ERROR_OUT_OF_DATE_KHR`.
- Los shaders presentan la hoja como textura y resuelven la cámara en la GPU:
  `hoja = (pantalla + pan) / zoom` con `texelFetch`; el color se lee del
  `0xAARRGGBB` en bytes B,G,R,A y se reordena a RGBA. Parámetros en un bloque
  std430 de 48 bytes (`ParametrosPresentacion.h`). El rasterizado del trazo
  sigue en CPU (paridad `MotorTest` intacta); la GPU sólo presenta.
- `GpuJni.cpp` expone `nativoGpuCrear/Render/Redimensionar/Nombre/Destruir` y
  guarda el `ANativeWindow` (`ANativeWindow_fromSurface`).
- Vulkan GLSL se compila a SPIR-V en configure (glslc del NDK) e incrusta en
  `shaders_vk_generado.h`; el CMake lo hace sólo bajo `if(ANDROID)`.

**Lado Kotlin:** `SuperficieGpu` (`:app`) es un `SurfaceView` + hilo de render
(`HiloRenderGpu`, `ReentrantLock`/`Condition`) que crea el renderizador y pide
cuadros on-demand. El hilo presenta un primer cuadro **antes** de avisar
`gpuListo`: así la pantalla de carga se retira con la hoja ya pintada y no se
ve un destello negro. `DibujoEngine` expone `revision()`/`copiarHoja()`/
`leerCamara()` con mutex para que el hilo GPU lea un snapshot coherente.
`PantallaDibujo` separa `AreaDibujo` en: hoja (`SuperficieGpu` si hay GPU, si
no `LienzoCpu`) + `CapaGestos` encima (gestos y vista previa del trazo). El
`Scaffold` y el `windowBackground` son transparentes para que se vea la
superficie GPU, que queda en una capa detrás de la ventana. Si el render GPU
falla en runtime, `gpuFracaso()` cae a `LienzoCpu` (CPU).

**Alineación de coordenadas (crítico).** El contenido del `Scaffold` NO aplica
`margenes`: la hoja ocupa **toda la ventana** (las barras se pintan encima, con
la barra superior/inferior después del contenido, así siguen dibujándose y
recibiendo toque). De este modo la superficie GPU, la capa de gestos y el motor
comparten el mismo origen y tamaño (pantalla completa), y el trazo cae justo
donde se dibuja. Aplicar `margenes` al área de dibujo desincronizaba el
`SurfaceView` (capa aparte) de la capa Compose y el trazo aparecía desplazado.

**Pantalla de carga.** `DibujoEstado.gpuCargando` se enciende al crear el motor
con GPU disponible y al cambiar de backend, y se apaga en `gpuListo()` o
`gpuFracaso()`. Mientras `motorNombre` está vacío o `gpuCargando` es true,
`AreaDibujo` muestra `PantallaCarga` (indicador + texto) sobre la superficie,
evitando el cuadro en negro durante la inicialización de OpenGL/Vulkan.

**Primera subida de la hoja.** La revisión 0 es válida (hoja recién creada) y
la textura/imagen debe subirse igualmente: `subirHoja` en `RenderizadorGL` y
`RenderizadorVulkan` no descartan `rev == 0`. Si se descarta, el lienzo queda
negro hasta el primer cambio de contenido (el "negro hasta tocar la pantalla",
sobre todo en Vulkan).

### Herramientas de depuración (source set `debug`, solo en el variant debug)

Todo vive en `app/src/debug/` (Kotlin + `AndroidManifest.xml` + recursos) y
**no existe en release**: el manifest de `debug` se fusiona solo en ese variant.
Es clave para trabajar desde el móvil, sin adb ni PC.

- **`MonitorDibujo`** (`object`): recolecta continuamente, en un hilo daemon
  `dibujo-monitor` (muestra cada 2 s):
  - **Memoria**: heap Java (`Runtime`), heap nativo (`Debug.getNativeHeap*`) y
    PSS por categoría (`Debug.MemoryInfo`: Java/dalvik, nativo, gráficos vía
    `summary.graphics`, y otros) más memoria privada.
  - **Hilos**: enumeración barata con `Thread.enumerate`; detecta
    apariciones/terminaciones respecto a la muestra anterior (`EventoHilo`).
  - **Acciones**: ciclo de vida de las Activities de la app principal vía
    `Application.registerActivityLifecycleCallbacks` (se salta el propio panel).
  - **Crashes**: `Thread.setDefaultUncaughtExceptionHandler` encadenado al
    anterior; registra el `FATAL` y guarda el volcado en `filesDir/crashes/`.
  - Los buffers son ring buffers acotados (`MAX_MUESTRAS`, `MAX_LOGS`, ...).
- **`CapturaLogs`**: lee el logcat propio (`logcat -v threadtime`) en un hilo
  daemon y conserva W/E/F; de V/D/I solo tags de interés (dibujo, EGL, Vulkan,
  OpenGL, GPU, LeakCanary). Descarta el ruido de firmware de proveedor
  (`TAGS_RUIDO`: Transsion/TECNO — `TranClassInfo`, `TranChoreographerImpl`,
  `AppResolutionTuner`, `FBI`, `QT`), que no es de la app y llenaba el reporte.
  Si el dispositivo restringe `logcat`, el monitor sigue con su buffer propio y
  el capturador de crashes.
- **`MonitorProvider`**: `ContentProvider` sin datos en `src/debug`; su único
  rol es arrancar `MonitorDibujo` al crear el proceso (se instala antes de
  `Application.onCreate`), así "escucha" siempre, aunque no se abra el panel.
- **`PanelDebugActivity`** (Compose, reusa `TemaDibujo`): Activity propia con
  **icono propio** (`ic_launcher_debug`) y su `intent-filter` LAUNCHER. Pestañas
  Resumen / Memoria / Hilos / Registro / Entorno; se refresca cada segundo.
  Botones: abrir LeakCanary, copiar reporte al portapapeles y compartirlo
  (`construirReporte`). Todo texto por `stringResource` en `strings_debug.xml`.
  **Tareas separadas** (issue 22): `MainActivity` y `PanelDebugActivity` llevan
  `launchMode="singleTask"` y `taskAffinity` distinta
  (`com.dibujo.android` vs `com.dibujo.android.debug`). Sin eso, al abrir un
  icono el launcher reanudaba la última Activity de la tarea y "Dibujo" abría
  el panel (y al revés).
- **LeakCanary 2.14**: `debugImplementation`, auto-instalado por su
  `ContentProvider`. Su icono de launcher se **desactiva** (`bools_debug.xml`,
  `leak_canary_add_launcher_icon=false`) para dejar un único icono de depuración
  (el panel); se entra a su pantalla desde el botón "Ver fugas". El permiso
  `POST_NOTIFICATIONS` se declara en el manifest de debug (Android 13+).


## 6. Riesgos conocidos / no resueltos

- El trazo en curso se previsualiza con Path sin rasterizar por el motor: al
  soltar, el resultado exacto (disco/bloque/línea gruesa) puede diferir levemente del overlay.
- `MotorKotlin.renderizar` es O(vw*vh) por frame en CPU; con el render GPU ya
  implementado, la app presenta la hoja como textura en GPU y sólo cae a CPU
  si el backend falla. Sin dispositivo Android en el runner, la corrección en
  runtime del render GPU (sobre todo Vulkan) sólo se valida compilando/enlazando.
- Se puede exportar el dibujo a la galería (PNG), pero no hay persistencia del
  estado editable de la hoja (reabrir el proyecto/deshacer entre sesiones): es
  la tarea #1 del ROADMAP.
- Release de `:app` no está firmado para distribución (solo debug).
- El historial es por snapshots: 30 pasos (compartidos C++/Kotlin) para
  acotar la RAM del dispositivo (ver "Historial").

## 7. Datos útiles para agentes

- Horario del usuario: America/Caracas; respondé en español nativo, fixeáte en `opencode.json`.
- Tests: JUnit Platform; paridad en `engine/src/test/kotlin/com/dibujo/engine/MotorTest.kt` (incluye paridad de undo/redo con `historialParidad`). El antialiasing entra en esa paridad: si tocás `disco`/`pintarMezcla` en un lado, corré `:engine:test`.
- El workflow `android-apk.yml` corre manual (`workflow_dispatch`) y en cada PR contra main; el workflow falso `build-debug-apk.yml` (APK placeholder aapt2/d8) fue eliminado.
- Commits: esperar orden explícita del usuario (nunca commit proactivo).
