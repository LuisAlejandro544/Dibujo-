# Dibujo

App de dibujo en Kotlin con motor nativo en C++ (JNI). Lienzo en blanco (la hoja),
lápices con color, grosor y punta, y dos modos: **EDICION** (dibujar) y
**MOVER** (recorrer la hoja libremente con pan/zoom).

## Módulos

| Módulo | Qué hace |
|---|---|
| `:core` | Dominio puro: hoja (`Lienzo`), lápices, paleta de colores, historial, parser de comandos. |
| `:engine` | Motor de dibujo: núcleo C++ (`src/main/cpp`, compilado con CMake) conectado por JNI, con respaldo puro Kotlin (`MotorKotlin`). |
| `:ui` | App de consola: menús de lápices/colores, modos, vista ANSI. |

La lógica de dibujo (rasterizado de trazos, disco grosor, historial, cámara)
está implementada dos veces con la misma semántica: una en C++ (`DibujoEngine`)
y una en Kotlin (`MotorKotlin`). Un test de paridad garantiza que ambas
producen píxeles idénticos; si la librería nativa no está disponible, la app
sigue funcionando con el respaldo.

## Requisitos

- JDK 17
- Para el motor nativo: `cmake` + `g++` (opcional; sin ellos se usa el respaldo Kotlin)
- No requiere instalar Gradle: el wrapper (`./gradlew`) lo descarga solo.

## Compilar y ejecutar

```bash
./gradlew build          # compila todo + tests (incluye build CMake)
./gradlew test           # solo tests
./gradlew :ui:run        # ejecuta la app (motor nativo automático)
./gradlew :ui:runDebug   # sin colores ANSI
# o distribución:
./gradlew :ui:installDist && ./ui/build/install/ui/bin/ui
```

## Uso

Dentro de la app escribe `h` para la ayuda completa:

- `w/a/s/d [n]` mueve el cursor (EDICION) o panea la vista (MOVER)
- `1..9` elige color rápido; `c` abre el menú de colores; `l` el de lápices
- `p` pinta un punto en el cursor; `.` baja/solta la pluma (trazo continuo)
- `m` alterna modo EDICION <-> MOVER
- `+`/`-` zoom, `f` centra la vista, `z`/`y` deshacer/rehacer, `x` limpia
- `q` salir

## Estructura del código nativo

- `engine/src/main/cpp/Types.h` — tipos (`Tool`, `Modo`, color ARGB)
- `engine/src/main/cpp/DibujoEngine.h/.cpp` — núcleo: hoja ARGB, trazos, undo/redo, cámara
- `engine/src/main/cpp/dibujo_engine.cpp` — puente JNI de `com.dibujo.engine.NativeEngine`
- `engine/src/main/cpp/CMakeLists.txt` — build CMake (requiere `JDK_HOME` para `jni.h`)

Prueba del motor en Kotlin: `Motores.crear(ancho, alto)` devuelve el motor
nativo si está disponible o el respaldo; la UI nunca sabe la diferencia.
