# Estructura del repositorio Dibujo

Mapa rápido. El detalle completo (responsabilidades, flujos y dónde tocar cada
cambio) está en `ESTRUCTURA.md`; el contexto técnico fino, en `AI_CONTEXT.md`.

## Módulos

| Ruta | Rol |
|---|---|
| `core/` | Dominio puro Kotlin: `Lienzo`, `MotorDibujo`, `Lapiz`, `Historial`, `Cuantizador`, `BackendRender`. Sin Android, JNI ni IO. |
| `engine/` | Motor dual: `MotorKotlin` (respaldo) + `MotorNativo` (JNI) y `Motores.crear()`. El C++17 vive en `engine/src/main/cpp/`. |
| `ui/` | Consola JVM (`Main.kt`, `ControladorConsola`, `VistaHoja`, `Menu`). |
| `app/` | Android Compose Material 3: navegación, `DibujoViewModel` (MVI), calco, GPU, Buril y persistencia. |
| `tools/` | Entrenamiento y datasets de Buril (`buril.py`, cuadernos de Kaggle/Colab, preparadores A/B). |
| `.github/workflows/` | CI: APK debug, entrenamiento, datasets y OpenCode. |
| `.opencode/skills/` | Skills de seguridad para C++ y Android (ver `.opencode/skills/README.md`). |

## Documentación

| Archivo | Contenido |
|---|---|
| `README.md` | Presentación y uso. |
| `README_AI.md` | Resumen ultra-rápido para agentes. |
| `AI_CONTEXT.md` | Arquitectura, semánticas y decisiones técnicas. |
| `ESTRUCTURA.md` | Mapa detallado del proyecto y guía de cambios. |
| `AGENTS.md` | Reglas de trabajo para agentes. |
| `ROADMAP.md` | Estado y pendientes. |

## Comandos

```bash
./gradlew build               # build completo + tests + motor nativo
./gradlew test                # tests de todos los módulos
./gradlew :engine:test        # paridad C++ ↔ Kotlin (MotorTest)
./gradlew :app:assembleDebug  # APK Android debug
./gradlew :ui:run             # consola
```

## Regla de oro

Todo cambio de rasterizado, cámara o historial se implementa en C++ **y**
Kotlin, y pasa `MotorTest` (paridad píxel a píxel).
