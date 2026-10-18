# ROADMAP — Dibujo

Estado actualizado del proyecto. Al cerrar una tarea de acá, marcarla y
mencionarla en el PR.

## Hecho

- [x] Dominio puro (`:core`): hoja ARGB, lápices (4 herramientas, puntas, grosor 1..64), paleta, historial undo/redo, parser de comandos.
- [x] Motor dual en paridad: `DibujoEngine` (C++17 / JNI en `:engine`) y `MotorKotlin` (respaldo), con `MotorTest` píxel a píxel.
- [x] App de consola (`:ui`): menús, vista ANSI con colores, modos EDICION/MOVER, cámara.
- [x] CI: workflow manual `build-debug-apk.yml` (APK placeholder aapt2/d8 + consola Linux con motor nativo).
- [x] **App Android real (`:app`)**: Compose Material 3, ViewModel dueño del motor (hoja sobrevive rotaciones), dibujo táctil con vista previa del trazo, paleta, herramientas, grosor, deshacer/rehacer/limpiar/centrar, modo MOVER con pan y pinch anclado, edge-to-edge, adaptive icon.
- [x] **Motor C++ compilado para Android**: `externalNativeBuild` con NDK en `:app` (arm64-v8a + x86_64); en el dispositivo corre `MotorNativo` por JNI (el respaldo queda como red de seguridad).
- [x] CI: workflow manual `android-apk.yml` → APK debug firmado vía AGP (`assembleDebug`).
- [x] Documentación para humanos e IAs: `README`, `README_AI`, `AI_CONTEXT`, `AGENTS`, `AGENTS_AI`, este roadmap.
- [x] **Iconos Material 3**: dependencia `material-icons-extended` (BOM) y UI con `TopAppBar` + iconos (undo/redo se habilitan/deshabilitan según el historial). Eliminado el workflow falso `build-debug-apk.yml` (APK placeholder) y `android-apk.yml` ahora también corre en PRs contra main.
- [x] **Borrado instantáneo**: la vista previa del borrador pinta con el fondo de la hoja y su grosor efectivo (`Lapiz.grosorEfectivo()`), igual que el motor. Historial limitado a 30 pasos (C++ y Kotlin) para acotar la RAM.
- [x] Requisitos: `minSdk 26` (Android 8) ya era el requisito desde la estructura inicial; confirmado.
- [x] **Arreglos de diseño (issue 16)**: undo/redo repinta (el `Canvas` observa el `State` en el draw), barras del sistema legibles (iconos oscuros + padding de la barra de navegación para que la paleta se pueda tocar), tamaño de letra fijo (`fontScale = 1`) y trazos con antialiasing en C++ y Kotlin (paridad `MotorTest`).
- [x] **Selección de backend gráfico (issue 16)**: `BackendRender` + detección de Vulkan 1.1 / OpenGL ES + diálogo de configuración (Vulkan bloqueado sin soporte) y persistencia en `SharedPreferences`. La preferencia queda cableada; el render GPU es el paso siguiente.
- [x] **Render GPU de presentación (OpenGL ES / Vulkan)**: `engine/src/main/cpp/gpu/` presenta la hoja como textura con la cámara resuelta en el shader (`RenderizadorGL` con EGL+GLES3 y `RenderizadorVulkan`); `SuperficieGpu` (`SurfaceView` + hilo de render) la muestra en `:app` con caída automática a `LienzoCpu` si el backend falla. El rasterizado del trazo sigue en CPU (paridad `MotorTest` intacta).
- [x] **Arreglos del render GPU (issue 16, ronda 2)**: el lienzo ya no queda negro hasta tocar (la primera hoja se sube con revisión 0 en GL y Vulkan); pantalla de carga mientras el renderizador inicia o se cambia de backend; y la hoja ocupa toda la ventana (barras encima) para que superficie GPU, gestos y motor compartan origen y el trazo caiga donde se dibuja. Pendiente: verificar en dispositivo real.
- [x] **Mejoras de interfaz y funciones nuevas (issue 18)**: barra inferior de tres filas sin scroll incómodo (iconos de herramienta compactos con estado, acciones de calco/expandir y grosor); **imagen de calco translúcida** para calcar (Photo Picker, mostrar/ocultar y opacidad); **expandir la hoja** de a poco en ancho/alto (diálogo con sliders, paridad C++/Kotlin y limpieza del historial); **paleta ampliada a 29 colores**; y **herramienta PINTAR (balde)** exclusiva para rellenar regiones (paridad `MotorTest`).
- [x] **Herramientas de depuración (issue 19)**: panel propio en el source set `debug` con icono en el launcher (`PanelDebugActivity`), monitor siempre activo (`MonitorDibujo` + `MonitorProvider`) de RAM por categoría, hilos activos/cambios, acciones, logs W/E/F y crashes; reporte copiable/compartible y LeakCanary 2.14 (icono propio desactivado, se entra desde el panel). Nada de esto entra en release.
- [x] **Pulido de la hoja (issue 24)**: corregida la desincronización de la cámara al hacer mucho zoom o tras **expandir** la hoja (el `MotorNativo` leía una cámara espejo en Kotlin que divergía del clamp del C++; ahora `camara()` lee del nativo con `nativoLeerCamara`); **rotación libre de la hoja** con gesto de dos dedos anclado al foco y botón "enderezar" (transformación `pantalla = R(rot)·(hoja·zoom) − pan`, en C++, Kotlin y los shaders GL/Vulkan); **límites de zoom 0.05..64** (antes 0.25..8); y **herramienta DOBLE** (dos trazos paralelos). Paridad `MotorTest` ampliada a rotación y DOBLE.

- [x] **IA local ligera (issue 25)**: herramienta **limpiar trazos** (adelgazado de Zhang-Suen, con paridad C++/Kotlin cubierta por `MotorTest.limpiarTrazosParidadNativoKotlin`) para los trazos repasados; **paleta del calco** (`Cuantizador` con k-medias en `:core`, sin dependencias: analiza la imagen que calcas y sugiere sus colores para colorear); y **paleta ampliada a 58 colores**, con brillos (neón), metálicos y pasteles.
- [x] **Limpiar trazos con modelo IA local (issue 25)**: además del adelgazado algorítmico, `:app` trae un **modelo propio chico** (~105 KB, float16, `app/src/main/assets/modelos/limpiar_trazos.tflite`) entrenado con datos sintéticos (`tools/entrenar_limpiar_trazos.py`, reproducible); `LimpiadorTrazosIa` lo corre por CPU con **LiteRT** (dependencia nueva), `MotorDibujo.reemplazarHoja` (C++ y Kotlin en paridad, `MotorTest.reemplazarHojaParidadNativoKotlin`) aplica el resultado con undo y, si el modelo falta o falla, se cae al algoritmo. También en este PR: **menú "más opciones"** en la barra superior (corrige el título que se dibujaba en vertical) y **pinch de zoom sin giro accidental** (`panZoomLock = true`).
- [x] **Mejora de IA, balde y exportación (issue 28)**: el **balde** ahora sólo rellena la región **cerrada** tocada (si alcanza el borde de la hoja no pinta nada; antes inundaba todo el fondo blanco), en C++ y Kotlin con paridad (`MotorTest`); el script de entrenamiento acepta **datasets reales** (`--reales entrada/objetivo`, p. ej. SketchBench) y **aumentación** de dominio, con una mini U-Net algo más ancha (sigue 64×64×1, sin tocar la app); la **exportación** muestra un diálogo con el **peso del PNG** y ofrece **guardar en galería o compartir** en cualquier app (`FileProvider`, sin permisos); y el **icono del borrador** pasó a un borrador real (Material Symbols `ink_eraser`).
- [x] **Herramientas legibles (seguimiento issue 28)**: el borrador usa el `ink_eraser` en **contorno** y la herramienta **Doble** deja el icono de "copiar" por uno propio de **dos trazos paralelos**; además, cada botón de herramienta muestra su **nombre debajo** (antes sólo iconos), así el doble lápiz/pincel se encuentra de un vistazo. Fila de herramientas a lo ancho y grosor + paleta en la fila siguiente.
- [x] **El balde cierra huecos del contorno (seguimiento issue 28)**: el dibujo a mano deja aperturas de pocos píxeles y las figuras grandes (cara, brazo) quedaban sin rellenar porque el relleno se escapaba al borde y se abortaba; ahora, antes de rendirse, el balde **cierra huecos de hasta 6 px** (cierre morfológico con cuadrado separable de radio `RADIO_CIERRE = 3`) y reintenta, pintando sólo los píxeles del color de origen. En C++ y Kotlin con paridad (`MotorTest.rellenoCierraHuecoParidadNativoKotlin`).
- [x] **Pantalla principal y sistema de guardados (issue 30)**: la app abre en un **menú** con tres pantallas propias (`PantallaMenu`, `PantallaCargar`, `PantallaOpciones`; navegación simple en `Navegacion.kt`, sin dependencias nuevas). **Continuar dibujando** reanuda el borrador autoguardado y **Cargar dibujo** lista la biblioteca de copias guardadas; la **Configuración dejó de ser un diálogo** dentro del lienzo y ahora es su propia pantalla. Persistencia en `AlmacenDibujos` (PNG en `filesDir/dibujos`, sin permisos): borrador `actual` autoguardado al salir del lienzo y en `ON_STOP`, más copias de biblioteca creadas con "Guardar en la app" (con miniatura, abrir y borrar). Falta restaurar el historial y la cámara entre aperturas.

## En curso

- [ ] Pulir más la UI Android con feedback del usuario (nueva barra superior/inferior con iconos en el PR del issue 13).

## Próximas (prioridad)

1. **Historial entre sesiones**: al reabrir un dibujo guardado, restaurar la pila de deshacer/rehacer (hoy se recupera la hoja, no el historial).
2. **Firma de release**: keystore de release + `assembleRelease` firmado en CI (hoy solo debug).
3. **Rasterizado en GPU**: hoy la GPU sólo presenta la hoja como textura con la cámara en el shader; el trazo y su antialiasing siguen en CPU. Moverlos a GPU (tiles/VBOs) manteniendo paridad con `MotorTest`.
4. **Cámara al abrir**: modo MOVER centrado con zoom inicial para hojas más grandes que la pantalla.
5. **Abrir dibujos desde archivos** (importar): compartir imagen ya está hecho (issue 28); falta abrir/importar un PNG del dispositivo.
6. **Rueda de herramienta**: selector rápido tipo popover al mantener presionado el chip del modo.
7. **i18n**: strings.xml ya preparado; evaluar inglés si el usuario lo pide.

## Ideas (sin fecha)

- Capas (layers) con undo por capa: el historial actual es de un solo nivel (por snapshots).
- iOS: el motor dual ya admite un front-end nuevo (el C++ es portable); Compose Multiplatform / KMP como candidata.
- Borrador por capas/bitmaps (destination-out) si algún día la hoja deja de ser opaca.
- **IA aprendida (hecho, ampliable)**: el *sketch cleanup* ya usa un modelo
  propio con LiteRT (issue 25). El entrenamiento ya acepta datasets reales
  (`--reales`) y aumentación (issue 28); lo siguiente sería empaquetar el
  modelo más grande entrenado así y evaluar cuantización int8 + delegado
  GPU/NPU. El resto de las herramientas de IA siguen siendo algoritmos locales
  (visión + k-medias).
