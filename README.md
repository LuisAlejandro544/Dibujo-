# Dibujo

App de dibujo en Kotlin con motor nativo en C++ (JNI). Lienzo en blanco (la hoja),
lápices con color, grosor y punta, y dos modos: **EDICION** (dibujar) y
**MOVER** (recorrer la hoja libremente con pan/zoom).

Compila en dos frentes con el mismo dominio:

- **Consola JVM** (`:ui`): menús ANSI, funciona en cualquier Linux/mac/Windows con JDK 17.
- **App Android** (`:app`): APK con UI Compose, compilado con AGP. El motor C++
  se compila para Android con el NDK e ingresa al APK como `libdibujo_engine.so`.

## Módulos

| Módulo | Qué hace |
|---|---|
| `:core` | Dominio puro: hoja (`Lienzo`), lápices, paleta de colores, historial, parser de comandos. |
| `:engine` | Motor de dibujo: núcleo C++ (`src/main/cpp`) conectado por JNI, con respaldo puro Kotlin (`MotorKotlin`). |
| `:ui` | App de consola: menús de lápices/colores, modos, vista ANSI. |
| `:app` | App Android (Compose + Material 3 + ViewModel): pantalla táctil de dibujo y barra de herramientas. |

La lógica de dibujo (rasterizado de trazos, disco grosor, historial, cámara)
está implementada dos veces con la misma semántica: una en C++ (`DibujoEngine`)
y una en Kotlin (`MotorKotlin`). Un test de paridad garantiza que ambas
producen píxeles idénticos; si la librería nativa no está disponible, la app
sigue funcionando con el respaldo.

## Requisitos

- JDK 17
- Para el motor nativo de escritorio: `cmake` + `g++` (opcional; sin ellos se usa el respaldo Kotlin)
- Para la app Android: Android SDK (compileSdk 37) y NDK + CMake, que Gradle usa/instala por el SDK Manager
- No requiere instalar Gradle: el wrapper (`./gradlew`, versión 9.8.1) lo descarga solo.

## Compilar y ejecutar

### Consola (escritorio)

```bash
./gradlew build          # compila todo + tests (incluye build CMake nativo de escritorio)
./gradlew test           # solo tests
./gradlew :ui:run        # ejecuta la app (motor nativo automático)
./gradlew :ui:runDebug   # sin colores ANSI
# o distribución:
./gradlew :ui:installDist && ./ui/build/install/ui/bin/ui
```

### App Android

```bash
./gradlew :app:assembleDebug     # APK debug firmado en app/build/outputs/apk/debug/
./gradlew :app:installDebug      # instala en un dispositivo/emulador conectado (adb)
```

En CI hay un workflow (`.github/workflows/android-apk.yml`) que genera el APK
debug listo para instalar: corre manualmente (`workflow_dispatch`) y en cada
PR contra main, siempre con `./gradlew :app:assembleDebug` (compilación
estándar de Android vía AGP + NDK).

## Uso (consola)

Dentro de la app escribe `h` para la ayuda completa:

- `w/a/s/d [n]` mueve el cursor (EDICION) o panea la vista (MOVER)
- `1..9` elige color rápido; `c` abre el menú de colores; `l` el de lápices
- `p` pinta un punto en el cursor; `.` baja/solta la pluma (trazo continuo)
- `m` alterna modo EDICION <-> MOVER
- `+`/`-` zoom, `f` centra la vista, `z`/`y` deshacer/rehacer, `x` limpia
- `q` salir

## Uso (Android)

- Tocá y arrastrá en la hoja para dibujar (chip **Dibujar**).
- El chip alterna a **Mover**: arrastra la hoja y pinch para zoom.
- Barra superior: título + motor activo, deshacer/rehacer (se habilitan
  según el historial), centrar y limpiar, todo con iconos Material.
- Barra inferior: modo, herramientas (lápiz/portamina/carbón/borrador con
  icono), grosor +/– y paleta de colores.

## Estructura del código nativo

- `engine/src/main/cpp/Types.h` — tipos (`Tool`, `Modo`, color ARGB)
- `engine/src/main/cpp/DibujoEngine.h/.cpp` — núcleo: hoja ARGB, trazos, undo/redo, cámara
- `engine/src/main/cpp/dibujo_engine.cpp` — puente JNI de `com.dibujo.engine.NativeEngine`
- `engine/src/main/cpp/CMakeLists.txt` — build CMake: usa `JDK_HOME` en escritorio y el sysroot del NDK en Android

Prueba del motor en Kotlin: `Motores.crear(ancho, alto)` devuelve el motor
nativo si está disponible o el respaldo; la UI nunca sabe la diferencia.

## Documentación

- `README_AI.md` — resumen condensado del proyecto para IAs.
- `AI_CONTEXT.md` — contexto técnico completo (arquitectura, semántica, decisiones).
- `AGENTS.md` — cómo trabaja el agente de código en este repo (lo lee opencode).
- `AGENTS_AI.md` — los agentes IA del repo y cómo se usan.
- `ROADMAP.md` — qué sigue.

## Convenciones

- Todo el código y los mensajes están en español (identificadores, strings, docs).
- Toolchain: Kotlin 2.4.21, Gradle 9.8.1 (wrapper), AGP 9.4.1, JDK 17 (compileSdk 37, minSdk 26, targetSdk 37).
- `gradle.properties` con configuration cache y build cache activados.
