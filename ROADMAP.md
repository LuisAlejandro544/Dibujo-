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

## En curso

- [ ] Revisión del PR #12 (workflow APK manual) — ya convive con el APK real de `:app`.
- [ ] Análisis de mockups/capturas del usuario para pulir la UI Android (paleta final, layout de la barra).

## Próximas (prioridad)

1. **Persistencia**: guardar/cargar la hoja (PNG a galería o archivo propio) y restaurar el historial de la sesión.
2. **Firma de release**: keystore de release + `assembleRelease` firmado en CI (hoy solo debug).
3. **Render acelerado**: reemplazar el render CPU O(vw·vh) por textura (composabled) o mover la rasterización pesada al motor nativo con render directo (OpenGL ES / SurfaceView); mantener paridad con `MotorTest`.
4. **Cámara al abrir**: modo MOVER centrado con zoom inicial para hojas más grandes que la pantalla.
5. **Exportar/importar dibujos**: compartir imagen (Intent SHARE) y abrir desde archivos.
6. **Rueda de herramienta**: selector rápido tipo popover al mantener presionado el chip del modo.
7. **i18n**: strings.xml ya preparado; evaluar inglés si el usuario lo pide.

## Ideas (sin fecha)

- Capas (layers) con undo por capa: el historial actual es de un solo nivel.
- iOS: el motor dual ya admite un front-end nuevo (el C++ es portable); Compose Multiplatform / KMP como candidata.
- Íconos material-icons-extended solo si hace falta: hoy todo con texto/unicode + strings.xml.
