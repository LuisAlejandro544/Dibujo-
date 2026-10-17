# Dibujo

App de dibujo en Kotlin (JVM) — lienzo en consola con pintar, líneas, rectángulos, flood fill, undo/redo y guardar/abrir.

## Requisitos
- JDK 17
- No requiere instalar Gradle: el wrapper (`./gradlew`) lo descarga solo.

## Compilar y ejecutar
```bash
./gradlew build          # compila + corre los tests
./gradlew run            # ejecuta la app
./gradlew test           # solo tests
# o con jar fat:
./gradlew jar && java -jar build/libs/dibujo-0.1.0.jar
# o distribucion instalada:
./gradlew installDist && ./build/install/dibujo/bin/dibujo
```

Dentro de la app escribe `h` para ver los comandos.
