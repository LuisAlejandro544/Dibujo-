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
  (la fórmula vive en `Lapiz.grosorEfectivo()`; el C++ la replica). DOBLE y
  PINTAR usan el grosor tal cual.
- **Antialiasing**: `disco()` calcula la cobertura de cada píxel como
  `0.5 + radio − distancia` (distancia en `Double` y `sqrt` correctamente
  redondeado en ambos lados) y mezcla con el fondo con `pintarMezcla` /
  `pintarMezcla()` (aritmética entera idéntica). Por eso los trazos tienen
  borde suave y `Lienzo.pintarMezcla` no debe "optimizarse" en un solo lado.
- **Trazo redondo = cápsula, no discos**: `lineaGruesa`/`polilineaGruesa`
  calculan la cobertura de cada píxel por distancia al segmento y guardan el
  **máximo** en una máscara; al final se mezcla **una sola vez** por píxel. Así
  los segmentos de una polilínea que comparten vértice no acumulan cobertura
  (antes se estampaba un disco cada ≤1 px y los bordes quedaban irregulares).
  La cobertura se redondea con `floor(v + 0.5)` en ambos motores.
- **Render de presentación bilineal**: `renderizar` (Kotlin y C++) muestrea
  los 4 texeles vecinos con la misma asociación de operaciones
  (`arriba/abajo + lerp` en `Double`, redondeo `floor(v + 0.5)`), con el centro
  del texel en la coordenada entera. GL y Vulkan usan `texture()` con filtro
  `LINEAR`. Al acercar ya no se ven bloques duros.
- BORRADOR pinta con el color de fondo de la hoja; la vista previa de Compose
  lo imita (fondo + grosor efectivo) para que el borrado se vea al instante.
- **Textura del trazo**: `Textura` (`LISA`, `GRANO`, `RUGOSA`, `TRAMA`,
  `SALPICADA`) modula el alpha final de cada píxel con `factor(x, y)` (255 fijo
  para LISA). El ruido es entero determinista (mezcla de 32 bits + máscaras),
  idéntico en `Textura.kt` y `DibujoEngine.cpp`; el ordinal viaja por JNI y
  debe conservar el orden. Nunca se aplica a BORRADOR ni PINTAR. La paridad la
  cubre `MotorTest.texturaParidadNativoKotlin`.
- FUERA de hoja en render = gris `0xFFECECEC`.

### Cámara
`renderizar()` define la transformación canonical (con rotación):
**pantalla = R(rot)·(hoja·zoom) − pan**, o sea **hoja = R(−rot)·(pantalla + pan) / zoom**
(`R` es la rotación en el plano; con `rot = 0` queda la fórmula vieja
`hoja = (pantalla + pan)/zoom`). El coseno/seno se calculan en `Double` y se
castean a `Float` en AMBOS lados para que la paridad no dependa de la última
ulp de la libm. De ahí salen (ver `README_AI.md`):
- pan natural: `motor.pan(-dx, -dy)` para que el contenido siga al dedo.
- zoom anclado: tras `motor.zoom(f)`, corregir `pan` con
  `pan = R(rot)·(hojaFoco·zoomNuevo) − focoPantalla` (así lo hacen
  `DibujoViewModel.zoomear` y `rotar`).
- rotación anclada (`MotorDibujo.rotar`, gesto de dos dedos): se guarda el
  punto de hoja bajo el foco y se reancla el pan tras girar; `centrarVista`
  deja `rot = 0`. El giro se normaliza a `(-π, π]`.
- Límites de zoom: `ZOOM_MIN = 0.05`, `ZOOM_MAX = 64` (antes 0.25..8), en
  Kotlin y C++ (`kZoomMin`/`kZoomMax`).

### Herramienta DOBLE
`Herramienta.DOBLE` (ordinal 5, mismo orden que `Tool::DOBLE` en C++) dibuja
**dos trazos paralelos** separados `separacion = (grosor + 2) * 0.5`. Ya no se
calcula la normal segmento a segmento: `polilineaDoble` desplaza la
**polilínea completa** con una normal suavizada por vértice (promedio de las
direcciones de los segmentos vecinos), así las dos líneas no tiemblan en los
empalmes. Luego cada lado se rasteriza con `polilineaGruesa`; un toque sin
arrastre dibuja un único disco/bloque centrado. La paridad la cubre
`MotorTest.dobleParidadNativoKotlin`.

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

El balde sólo pinta la región **cerrada** que contiene el toque. Si la región
alcanza el borde de la hoja (fondo abierto), no pinta nada y devuelve `false`:
así tocar afuera de una figura no inunda toda la hoja en blanco. La detección
marca el tramo que toca `x==0`, `x==ancho-1`, `y==0` o `y==alto-1`.

Como el dibujo a mano deja aperturas de pocos píxeles en el contorno (y una
figura grande, como una cara o un brazo, tiene más chance de quedar con un
hueco que un trazo chico), antes de darse por vencido el balde **cierra huecos
pequeños** y reintenta: hace un cierre morfológico (dilatar y erosionar con un
cuadrado separable de radio `Lienzo.RADIO_CIERRE = 3`, o sea cierra huecos de
hasta 6 px) sobre la máscara "no es del color de origen" y vuelve a inundar. Si
aun así la región llega al borde, no se pinta nada. Sólo se pintan los píxeles
que siguen siendo del color de origen, nunca el trazo ni su antialiasing. Fuera
de la hoja se cuenta como barrera para poder cerrar huecos cerca del borde. El
C++ replica el mismo cierre (`cerrarHuecos`), así la paridad se mantiene
(`MotorTest.rellenoCierraHuecoParidadNativoKotlin`).

El relleno es por **tramos** ("spans"): al procesar una semilla se extiende el
tramo horizontal contiguo de color `origen`, se marca entero y sólo se apila
una semilla por cada tramo nuevo arriba/abajo. Antes se apilaba píxel por píxel
(~10^7 `push` en una hoja de pantalla), lo que hacía que el balde tardara 2-3 s.
El algoritmo por tramos es lineal y da exactamente el mismo conjunto rellenado;
el C++ y el Kotlin deben cambiarse juntos (`MotorTest.rellenoParidadConBarreras`
verifica la paridad con barreras aleatorias). Como la región se marca antes de
pintar, el aborto por borde tampoco modifica la hoja.

### Limpiar trazos (visión por computadora local)
`MotorDibujo.limpiarTrazos()` adelgaza **toda** la tinta de la hoja a un
esqueleto de 1 px con el método de Zhang-Suen y la vuelve a dibujar con ancho
uniforme (`Lienzo.RADIO_LIMPIEZA = 1.0f`; el C++ usa `1.0f`). Es la herramienta
para el usuario que repasa el mismo trazo muchas veces y la línea queda gruesa e
irregular: el resultado es una línea pareja. Cada píxel del esqueleto conserva
el color que tenía en la hoja original (el esqueleto es un subconjunto de la
tinta). El algoritmo es puramente entero/booleano, así que el C++ y Kotlin dan
exactamente el mismo resultado; `MotorTest.limpiarTrazosParidadNativoKotlin` lo
verifica. Si la hoja está vacía devuelve `false` y no ensucia el historial.

### Limpiar trazos con modelo (IA local, LiteRT) — "Buril"
Además del adelgazado algorítmico, `:app` trae un **modelo propio** que limpia
los repases con una red: `app/src/main/assets/modelos/limpiar_trazos.tflite`
(mini U-Net 32/64/128: entrada `[1,64,64,1]` máscara binaria de tinta; salida
`[1,64,64,1]` línea limpia). Es chico (float16, ~0,9 MB, ~466k parámetros) y la
inferencia corre **en el equipo**: no descarga nada. El modelo se llama
**Buril** (herramienta de grabado: saca una línea fina de un repase grueso) y
su versionado va en la release y en el manifiesto `.json`
(`"modelo": "Buril v1.0.0-experimental.1a"`, con `--nombre`/`--version`).

**Ese `.tflite` NO se versiona** (`.gitignore`): para no engordar el repo se
entrena fuera y se **inyecta en el APK** antes de `assembleDebug`. El buzón es
la **release `modelo-buril`** (un asset `.tflite` + su `.json`, historial de
versiones), no la cache de Actions. **Lo entrena Kaggle** (cuaderno
`tools/buril_kaggle.ipynb`: GPU T4 x2 gratis, secretos `GH_TOKEN`, *Save &
Run All* en segundo plano sin corte por inactividad); **Google Colab**
(`tools/buril_colab.ipynb`) queda para **probar otros modelos** y experimentos
cortos; como respaldo, el workflow `entrenar-limpiar-trazos-planb.yml`. El
**dataset `.npz`** se cachea en una release aparte, **`dataset-buril`**, para que
sobreviva entre sesiones (mismo buzón que usan el workflow de dataset y el de
entrenamiento, con el nombre versionado
`pares_esq_lado{LADO}_pc{POR_CATEGORIA}_{VERSION}.npz`). Los **puntos de
control** (para reanudar) se guardan en `checkpoints-buril`.
Ver `app/src/main/assets/modelos/LEEME.md`.

- `LimpiadorTrazosIa` (`:app`) carga el modelo con **LiteRT**
  (`com.google.ai.edge.litert:litert`, patrón `CompiledModel.create(assets,
  ruta, Options(acelerador))` + `createInputBuffers`/`createOutputBuffers`/
  `writeFloat`/`run`/`readFloat`). Prueba **GPU primero** (`Accelerator.GPU`) y,
  si el equipo o el modelo no lo admiten, **cae a CPU** (`Accelerator.CPU`) con
  el mismo binario float16 (I/O float32). Lo corre por parches 64×64 con paso 48
  (solape 16 px) recomponiendo por máximo. **Contrato de polaridad**: la app
  arma el parche con `tinta = 1, fondo = 0` (`pixeles != fondo`), y así se
  entrena (ver abajo); el `.npz` guarda la tinta oscura y `buril.py` la invierte
  al cargar. Tras umbralar, la máscara se **adelgaza a un esqueleto de 1 px con
  `Lienzo.limpiarTrazos` (Zhang-Suen)**, el mismo protocolo del benchmark: un
  modelo que devuelva la línea gruesa o con manchas no ensucia la hoja. Cada
  píxel del esqueleto toma el color de la tinta original más cercana (búsqueda
  en un radio de 6 px) para conservar el color del trazo.
- El resultado se aplica con `MotorDibujo.reemplazarHoja(pixeles)`: reemplaza la
  hoja completa (validando el tamaño) y deja el estado anterior en el historial.
  Existe en **los dos motores** (`MotorKotlin` y `DibujoEngine` vía
  `nativoReemplazarHoja`) y su paridad la cubre
  `MotorTest.reemplazarHojaParidadNativoKotlin`.
- Si el modelo no está empaquetado o falla, `LimpiadorTrazosIa.limpiar` devuelve
  `null` y `DibujoViewModel.limpiarTrazosConModelo` cae al adelgazado algorítmico.
  `DibujoEstado.modeloIaDisponible` decide si se ofrece el botón en el diálogo.
- Reproducir el modelo: `tools/buril.py` (requiere
  `tensorflow` + `numpy`; en GPU conviene `tensorflow`, no `tensorflow-cpu`)
  escribe el `.tflite` y su `.json` de métricas. **No se ejecuta en el build**:
  el binario lo entrena Kaggle (o Colab/el CI) y se publica en la release
  `modelo-buril`, que `android-apk.yml` baja e inyecta en el APK (ver arriba),
  así el repo no carga el modelo. `--nombre`/`--version` fijan la identidad que
  va al manifiesto.
- **Rápido y con pulido**: `--lote` (64, fase rápida) y `--lote-final` (32,
  pulido) dividen las épocas (`--fase-rapida`, 75 %). El LR arranca con
  `--calentamiento` (2 épocas) y decae en coseno; en la fase de pulido el lote
  es menor y el LR ya bajo, para refinar detalle. En GPU se usa **precisión mixta**
  float16 (`--precision auto`) y **XLA** (`--xla auto`), que aceleran sin cambiar
  la calidad (el `.tflite` se exporta float16 igual; se clona el modelo a float32
  para que el conversor TFLite no pida Flex ops). El clDice **no** se pide además
  como métrica (ya está en la pérdida). Con `--checkpoints` se guarda
  `ultimo.weights.h5` por época y `mejor.weights.h5` (menor `val_loss`), y
  `--reanudar <carpeta>` continúa; al final se exporta el **mejor**.
- Datos reales (opcional): `--reales <carpeta>` carga pares reales
  `entrada/`+`objetivo/` (p. ej. SketchBench o el set de Simo-Serra) y los
  mezcla con los sintéticos; `--aumentos N` agrega copias aumentadas (volteos,
  giros de 90°, desplazamientos y, sólo sobre la entrada, difuminado,
  engrosado/adelgazado, contraste y ruido). La red es una mini U-Net de 3
  niveles (32/64/128, ~0,9 MB en float16), más ancha que la v1 (24/32/48) pero
  todavía chica para el móvil. Sigue leyendo y
  escribiendo parches `64×64×1`, así que no hay que tocar `LimpiadorTrazosIa`.
  Al terminar evalúa **IoU y PSNR** sobre validación con el `.tflite` exportado y
  guarda un manifiesto con las métricas y la atribución del dataset.
- **Paso A — dataset real cacheado (`tools/preparar_dataset_limpiar_trazos.py`)**:
  baja **SketchBench** (Yan et al., TOG 2020; el benchmark de limpieza de bocetos
  con ground truth de artistas), empareja `rough/pixel` con `gt/pixel`
  (variante `norm_cleaned`), binariza el objetivo con **Otsu** y lo **adelgaza a
  1 px** (Zhang-Suen), y guarda `.cache/limpiar_trazos/procesado/pares_esq_lado512.npz`
  (425 pares de 141 bocetos, ~21 MB) + `manifiesto_*.json`. Fases `descargar` /
  `preprocesar` / `todo` / `estado` / `autoprueba`, reanudables y cacheadas, para
  no exceder el corte de ~1 h de CI: el `.zip` de 817 MB se verifica por
  `sha256` (el host de GMU sirve un certificado que no valida, por eso `--inseguro`
  + hash fijo). El workflow `.github/workflows/preparar-dataset-limpiar-trazos.yml`
  lo corre y sube el `.npz` como artefacto. **Ojo licencias**: la mayoría de los
  bocetos son CC y el resto sólo uso académico (`sketch_tags.csv`); para uso
  comercial hay que filtrar. El set de Simo-Serra (68 pares) NO se redistribuye
  por copyright, sólo se puede usar `--reales` con una copia propia.
- **Paso B (entrenamiento) — listo**: `buril.py --pares <npz>`
  recorta parches `64×64` con tinta del cache (`--parches-por-imagen`) y los
  mezcla con los sintéticos, separando entrenamiento/validación **por boceto**
  (sin fuga). **Polaridad**: el cache guarda la tinta oscura (fondo 255) y el
  loader la invierte a `tinta = 1, fondo = 0` para que coincida con la app, el
  benchmark y el set sintético; equivocarse acá hacía que el móvil recibiera la
  polaridad opuesta y Buril saliera ruidoso. El **objetivo de entrenamiento es
  una banda de 3 px** (`--banda`, por defecto 1), no una línea dura de 1 px: una
  convolución no puede acertar el 1 px y devolvía manchas, y la app adelgaza con
  Zhang-Suen igual. La **pérdida es BCE + Dice + 0.5·clDice** (el clDice, Shit
  et al. CVPR'21, se calcula con un esqueleto morfológico blando diferenciable,
  así que premia la conectividad y no sólo la superposición). El **LR arranca con
  calentamiento y decae en coseno** (`--lr`, por defecto `1e-3`), y el
  entrenamiento se parte en **fase rápida** (lote 64) y **pulido** (lote 32) con
  `--fase-rapida 0.75`; por defecto se corren **60 épocas** con **`--aumentos 3`**.
  En GPU va con **precisión mixta + XLA** (`--precision auto`, `--xla auto`). La **aumentación es al vuelo** con `tf.data` (`flujo`:
  `repeat(aumentos+1)` + `Dataset.map` con `num_parallel_calls`, semilla por
  llamada), no materializando el set ×(1+aumentos) en RAM. Antes
  `aumentar_conjunto` creaba las copias en float32 y las concatenaba: con
  `pc=200`/`aumentos=3` y hojas de 512 px eso pasaba de 10 GB y el proceso moría
  con **`SIGKILL: 9`** (OOM del kernel), no por el tamaño del modelo. La
  memoria ahora es del orden del lote.
  Exporta `.tflite` **float16** (I/O float32, corre en GPU y CPU),
  evalúa **IoU y PSNR** sobre validación con el modelo exportado y guarda un
  `.json` de métricas + atribución (con `"modelo": "Buril <version>"`). La vía
  recomendada es **Kaggle**: `tools/buril_kaggle.ipynb` (driver fino: clona el
  repo, recupera el `.npz` de `dataset-buril` y los checkpoints de
  `checkpoints-buril`, entrena con la cascada precisión mixta+XLA → float32 y
  publica en `modelo-buril`). Se corre con **Save Version → Save & Run All
  (Commit)**, que no tiene *idle timeout*. `tools/buril_colab.ipynb` queda para
  **pruebas** y `tools/kernel-metadata.json` permite subirlo con
  `kaggle kernels push -p tools`. El workflow
  `.github/workflows/entrenar-limpiar-trazos-planb.yml` queda como **respaldo**
  (CPU, misma versión, mismos buzones). El `.npz` se cachea en la release
  `dataset-buril` y el `.tflite` en `modelo-buril`; `android-apk.yml` baja el
  `.tflite` de esa release y lo copia a `app/src/main/assets/modelos/` antes de
  `assembleDebug`. Por eso el binario **no se versiona** (ver `LEEME.md`).
- **Plan B — dataset comercial (Quick, Draw!)**:
  `tools/preparar_dataset_limpiar_trazos_planb.py` +
  `tools/datasets_limpiar_trazos_planb.json`. Nace de una razón de licencias:
  SketchBench tiene bocetos **solo de uso académico**, así que un modelo
  entrenado con él no es limpio para vender la app (Plan A queda sólo para
  investigación/evaluación). El Plan B usa **Quick, Draw!** (Google), **CC BY
  4.0**: permite uso comercial y **no obliga a abrir el código**, sólo pide
  atribución (`"The Quick, Draw! dataset by Google, Inc. (CC BY 4.0)."`).
  - `descargar` baja **sólo la cabecera** de cada categoría (rango HTTP
    `bytes=0..max_bytes`, el host de Cloud Storage lo sirve con
    `accept-ranges`; cada categoría completa pesa ~40-100 MB y no hace falta) y
    es reanudable. `preprocesar` lee los trazos vectoriales reales (0..255,
    simplificados), los centra/escala en la hoja y **fabrica los pares**:
    `y` = trazo limpio de 1 px (polilínea de Bresenham) y `x` = el mismo trazo
    **repasado a mano simulado** (varias pasadas, jitter perpendicular, radios
    1.5-4.5 px y manchas sueltas), el mismo *inverse dataset construction* de
    Simo-Serra pero sobre datos comerciales. El `.npz` guarda el esqueleto de
    1 px; `buril.py` lo engorda a banda (`--banda`) recién al entrenar, así el
    benchmark puede seguir midiendo contra el esqueleto ideal. La rasterización es propia (sólo
    `numpy`, sin Pillow ni CairoSVG); los puntos se **densifican** antes de
    estampar para que ningún trazo quede punteado.
  - El `.npz` usa el **mismo formato que el Plan A** (x grises uint8, y binaria
    {0,1}, split por `key_id`), así que el entrenamiento lo consume sin cambios
    (`buril.py --pares <npz>`). Fases `descargar` /
    `preprocesar` / `todo` / `estado` / `autoprueba` (sin red) y workflow
    `.github/workflows/preparar-dataset-limpiar-trazos-planb.yml` (cachea las
    cabeceras y el `.npz`, sube artefacto). Todo es rápido (~0.02 s por par en
    512).
  - La **atribución** que exige la licencia se muestra en la pantalla de
    *Opciones* (`config_atribucion_quickdraw`). Se eligió float16 (no int8)
    porque el delegado GPU del móvil trabaja con float; a este tamaño la
    ganancia de int8 no compensa perder la GPU.

### Benchmark de limpieza de trazos (Buril vs. rivales)
`tools/benchmark_limpiar_trazos.py` (CLI + `autoprueba`) y
`tools/benchmark_limpiar_trazos.ipynb` (**cuaderno autónomo de Colab**: la lógica va
embebida en una celda, así **no clona el repo ni importa el `.py`** ni depende de
qué rama tenga el archivo; baja Buril de `modelo-buril` y el `.npz` de `dataset-buril`
**sin token**, muestra las imágenes en línea y, opcionalmente, levanta una interfaz
Gradio). Toma N muestras del `split=validacion` del `.npz` (que ya trae el
esqueleto limpio de 1 px como *ground truth*) y las pasa por **los mismos** modelos;
después normaliza la salida de todos (**umbral + esqueleto Zhang-Suen**) y mide
IoU/Dice esqueleto↔esqueleto, IoU de máscara, precisión/recall, **clDice** (topología)
y **PSNR**, más latencia y peso. La rejilla y la interfaz Gradio muestran la salida
**normalizada** (lo que el usuario ve tras el adelgazado de la app), no la cruda. Rivales: **Simo-Serra** (`bobbens/sketch_simplification`
GAN, pesos convertidos en `Rayonapp/sketch_simplification`; licencia **CC-BY-NC-SA,
no comercial**), **Anime2Sketch** (MIT), **Informative Drawings** (MIT),
**ControlNet Lineart** y **PidiNet** (Apache-2.0, vía `controlnet-aux`); baseline
algorítmico **Zhang-Suen**. Es honesto *sólo* como comparación de forma con
protocolo fijo: Buril es un especialista en línea limpia para el móvil, no un
limpiador general; no busca un "ganador" universal. Salidas en `.cache/benchmark/`
(`resultados.json` + `rejilla.png`, no versionados). Autoprueba sin red:
`python3 tools/benchmark_limpiar_trazos.py autoprueba` (el cuaderno corre la misma
autoprueba embebida antes de medir).

### Paleta del calco (color local, sin red neuronal)
`Cuantizador` (`:core`) implementa k-medias (k-means++ con semilla fija, por eso
es determinista) para sacar los colores dominantes de una imagen. La app
(`DibujoViewModel.analizarCalco`) decodifica el calco, muestrea una grilla
64×64 descartando transparencias y el fondo casi blanco, y pide 8 colores; al
tocar uno se elige y la herramienta pasa a PINTAR. Es aprendizaje no supervisado
local: **0 MB** y ninguna dependencia nueva.

### Historial
Undo/redo por snapshots completos de la hoja. Límite de pasos: **30**
(`Historial` en Kotlin y `kMaxHistorial` en C++ tienen que coincidir). El
estado Compose expone `puedeDeshacer`/`puedeRehacer` (leídos del motor
después de cada operación) y habilita/deshabilita los botones. Importante:
el `Canvas` lee el `State` dentro del `draw` (`estadoState.value`), no sólo
durante la composición; si no, undo/redo cambian el motor pero no repintan
(era el bug "se unde y no hace nada").

### Transformar la hoja (voltear / mover / escalar)
`MotorDibujo.transformarHoja(voltearH, voltearV, dx, dy, escala): Boolean`
(issue 52) transforma el contenido **dentro de la misma hoja**: recorre cada
píxel destino y mapea hacia atrás con un `double` (espejo horizontal/vertical,
desplazamiento en px de hoja y escala alrededor del **centro**), así al ampliar
no quedan huecos; lo que no se cubre queda con el fondo. Redondeo unificado
`floor(v + 0.5)` en Kotlin y C++ para mantener la paridad. Devuelve `false`
(no-op) si `escala <= 0` o si no hay espejo ni desplazamiento ni escala.
La operación **entra al historial** (se deshace). Paridad cubierta por
`MotorTest.transformarParidadNativoKotlin` y `voltearDosVecesVuelveAlOriginal`.

**Ajuste libre del dibujo (issue 57)**: en vez de mover el dibujo con flechas,
el diálogo "Transformar dibujo" abre un modo (`DibujoEstado.ajustandoDibujo`)
donde la **vista hace de vista previa**: un dedo mueve y dos escalan (como el
calco reposicionable), sin rotación. Al tocar "Listo"
(`DibujoViewModel.terminarAjusteDibujo`) el cambio de cámara (pan/zoom) se
convierte en una única llamada a `transformarHoja` (una sola entrada de
historial) y la cámara se restaura, así el resultado no salta. La conversión
sale de `hoja = R(−rot)·(pantalla + pan)/zoom`: con `z0,pan0` la cámara al
entrar y `z1,pan1` la actual,
`escala = z1/z0` y
`desplazamiento = R(−rot)·(pan0 − pan1)/z0 + (escala − 1)·centro`
(centro `((ancho−1)/2, (alto−1)/2)`, igual que `transformarHoja`). La vista
previa por cámara y el horneado final son el **mismo mapeo**, así que no hay
salto (sólo el borde fuera de hoja pasa de gris a fondo al vaciarse). Cualquier
acción que toque cámara u hoja (`asegurarSinAjuste`: centrar, deshacer,
limpiar, expandir, transformar, cambiar de modo…) cancela el ajuste pendiente
sin hornearlo.

### Trazo asistido (líneas rectas)
`MotorDibujo.asistenciaRecta(activa: Boolean)`: al soltar el trazo, si está
activa, se descarta el recorrido tembloroso y se dibuja una **recta** entre el
primer y el último punto; no aplica a `Herramienta.PINTAR` ni a toques sin
arrastre. Paridad en `MotorTest.asistenciaEnderezaElTrazo` y
`asistenciaRectaParidadNativoKotlin`. En la UI se activa con un botón visible en
la **barra superior** (`BotonIcono` con `seleccionado`, se resalta al encenderse)
y el estado vive en `DibujoViewModel.asistenciaRecta` (aplicado en `inicializar`
y `abrirDibujo`).

### Color: rueda HSV, cuentagotas y recientes (issue 52)
- **Rueda HSV** (`app/.../RuedaHsv.kt`, `DialogoColor`): tono por ángulo,
  saturación por radio (centro = blanco) y brillo en un `Slider`; muestra hex.
  La rueda se pinta **siempre a brillo pleno** (el brillo sólo afecta a la
  muestra y al hex): oscurecerla con (1 − valor) la dejaba casi negra con
  colores oscuros y no se podía elegir el tono.
  Mientras se arrastra se **previsualiza** sobre el lápiz
  (`previsualizarColorHsv`, no toca recientes); recién "Listo"
  (`confirmarColorHsv`) lo registra.
- **Cuentagotas**: `DibujoViewModel.recogerColor(px, py)` lee
  `MotorDibujo.pixelHoja(x, y)` en coordenadas de hoja, aplica el color, lo
  registra y **se desactiva** (un toque = un color); fuera de la hoja no hace
  nada. En `CapaGestos`, si `cuentagotasActivo`, el tap toma color en vez de
  pintar.
- **Recientes**: `MAX_RECIENTES = 12`, dedup por argb (más nuevo primero), se
  llenan con la paleta, el cuentagotas y la rueda; se muestran primero en la
  fila de color, separadas de la paleta fija por una línea.

## 4. Build

| Pieza | Valor |
|---|---|
| Gradle | 9.8.1 (wrapper, `gradlew`) |
| Kotlin | 2.4.21 (root `build.gradle.kts`; alias en `gradle/libs.versions.toml`) |
| AGP | 9.4.1 (con *built-in Kotlin*: NO aplicar `org.jetbrains.kotlin.android` en `:app`) |
| Compose | BOM 2026.09.00 + `kotlin.plugin.compose` 2.4.21 |
| LeakCanary | 2.14 (solo `debugImplementation` en `:app`) |
| LiteRT | `com.google.ai.edge.litert:litert` 2.3.0 (Google Maven; runtime del modelo IA de `:app`; delegado GPU con caída a CPU; trae `&lt;abi&gt;/libLiteRt.so` + `liblitert_jni.so`) |
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
  con foco (MOVER) que además de pan/zoom entrega `rotation` (en grados): la
  capa de gestos la pasa a radianes y llama `DibujoViewModel.rotar`. La vista
  previa del trazo aplica la cámara completa (rotación incluida) y la `CapaCalco`
  se transforma con `withTransform { translate(-pan); rotate(rot, Offset.Zero);
  scale(zoom, Offset.Zero) }` para quedar alineada con la hoja.
- **Edge-to-edge** desde la Activity (`enableEdgeToEdge()`), `Scaffold` con
  `TopAppBar` (título + motor; acciones deshacer/rehacer/centrar/enderezar y un
  menú **"más opciones"** con limpiar/exportar/configuración; "enderezar" queda
  habilitado sólo si hay rotación) y barra inferior con
  herramientas/chips de modo/grosor y la paleta. Las herramientas son seis
  (…+ DOBLE), con botones compactos (`IconoToggle(tamano = 34.dp)`) para que
  entren en una fila sin scroll.
- **Transformar y color (issue 52)**: el menú "más opciones" suma
  "Transformar dibujo" (`DialogoTransformar`: voltear H/V, escalar 0.25–2× con
  `Slider` y el botón **"Ajustar libremente"** que abre el modo de ajuste por
  gestos — ver *Ajuste libre del dibujo*; todo deshacible). El trazo asistido de
  **líneas rectas** pasó al menú "más opciones" (con tilde cuando está activo).
  La paleta arranca con los colores recientes antes de la paleta fija, y el
  círculo del color actual abre la rueda HSV (`DialogoColor` en `RuedaHsv.kt`).
  Particularidades que costaron bugs reales:
  - Las barras del sistema usan `SystemBarStyle.light(...)`: el tema es claro
    y, con el teléfono en modo oscuro, `auto()` dejaba hora/batería blancas
    sobre fondo claro (invisibles).
  - La barra inferior lleva `windowInsetsPadding(WindowInsets.navigationBars)`;
    sin eso la paleta quedaba debajo de la barra de navegación y no se tocaba.
  - `TemaDibujo` fija `fontScale = 1` en `LocalDensity`: el tamaño de letra
    no depende de la configuración de Android del usuario.
  - **Título en vertical (bug de diseño)**: las siete acciones iban sueltas en
    el `TopAppBar` y en pantallas angostas no dejaban ancho para el título, que
    se dibujaba carácter por carácter. Se movió lo poco frecuente a un
    `DropdownMenu` ("más opciones") y el título quedó con `maxLines = 1` +
    `TextOverflow.Ellipsis`. La barra superior se dejó en lo mínimo
    (volver, título, deshacer, rehacer, centrar y "más opciones"); el subtítulo
    "Motor: …" se quitó (la información sigue en Opciones).
  - **Giro accidental en el pinch**: el gesto de dos dedos usaba
    `detectTransformGestures` sin `panZoomLock`, así que un pellizco de zoom
    también rotaba la hoja. Se pasó `panZoomLock = true`: la rotación sólo se
    activa si el gesto EMPIEZA girando (el umbral de rotación se compara con el
    touch slop).
- **Material Icons**: `androidx.compose.material:material-icons-extended`
  (BOM, iconos Rounded; deshacer/rehacer vía `Icons.AutoMirrored`). Sin
  símbolos de texto: cada icono lleva `contentDescription` desde strings.xml.
- **Cero texto duro**: todo por `stringResource`.
- **Barra inferior ordenada en tres franjas** (issue 63): se pasó de cuatro
  filas amontonadas a tres franjas con propósito: (1) herramientas en una
  tarjeta `Surface` donde el seleccionado se resalta con un fondo tonal, (2)
  color actual (abre la rueda HSV) + paleta deslizable + cuentagotas, y (3) modo
  de gesto (`FiltroChip`) + control de grosor agrupado (`Surface` redondeada con
  `−`/`+`) + botón "más". Las acciones secundarias (elegir/ver/ajustar calco,
  IA local, expandir hoja) se movieron a una hoja inferior
  (`ModalBottomSheet` → `HojaHerramientasHoja`). La paleta tiene 58 colores
  (`Colores.PALETA`), con brillos (neón), metálicos y pasteles al final; los
  primeros 12 conservan su índice (la consola y sus tests los eligen por
  número), los nuevos van al final.
- **Tema Material 3 propio** (issue 63): `TemaDibujo` define
  `EsquemaDibujo` (violeta suave sobre fondos casi blancos, `lightColorScheme`)
  y `FormasDibujo` (`Shapes` con esquinas redondeadas coherentes) en vez del
  esquema por defecto. La barra inferior tiene esquinas superiores redondeadas.
- **Imagen de calco** (issue 18): `CapaCalco` es una capa Compose translúcida
  sobre la hoja (GPU o CPU) y debajo del trazo; la imagen se ajusta dentro de
  la hoja y sigue la cámara. Se elige con el Photo Picker (sin permisos), se
  muestra/oculta al instante (`calcoVisible`) y tiene opacidad ajustable. El
  `calcoUri` vive en el `ViewModel` (sobrevive rotaciones) y el `Bitmap` se
  decodifica reducido (`MAX_CALCO = 2048`) al componer.
- **Calco reposicionable y escalable** (issue 35): además del ajuste natural, el
  usuario mueve (un dedo) y escala (dos dedos) la imagen. Al elegirla se
  **auto-centra SIEMPRE** (`calcoOffsetX/Y = 0`, `calcoEscala = 1`) y se entra en
  modo ajuste (`calcoEnEdicion`); hay un botón "Centrar" que la devuelve a su
  estado automático. El offset vive en **coordenadas de hoja**
  (`DibujoViewModel.moverCalco` convierte el delta de pantalla con `R(−rot)/zoom`)
  y la escala se ancla al foco (`escalarCalco`, centro `C' = F + ratio·(C − F)`),
  así el calco queda pegado a la hoja y no a la pantalla. `CapaGestos` prioriza
  los gestos del calco cuando `calcoEnEdicion` (mismo `detectTransformGestures`
  que la cámara, pero sin dibujar) y `CapaCalco` dibuja un marco guía azul
  mientras se ajusta. No toca el motor (vive en Compose), por eso **no aplica la
  paridad**; el flag es de UI (`calcoEnEdicion`), no un valor nuevo de `Modo`
  (que es de `:core` y usa la consola).
- **Texturas del trazo** (issue 70): el botón `Texture` de `FilaPincel` abre
  `DialogoTextura` (chips) y `DibujoViewModel.elegirTextura` actualiza el
  `Lapiz`; el valor se propaga a `MotorKotlin`/`MotorNativo` y al C++.
- **Insertar texto** (issue 70): `TextoHoja.dibujar` compone el texto sobre un
  `IntArray` con `Canvas`/`Paint` (las 5 tipografías del sistema: `sans-serif`,
  `serif`, `monospace`, `cursive`, `casual`); `DibujoViewModel.insertarTexto`
  lo aplica con `reemplazarHoja` fuera del hilo principal, centrado en la vista
  actual y como un paso deshacible. Vive sólo en `:app`, así que no altera la
  paridad del motor.
- **Importar imagen** (issue 35): entrada explícita en el menú "más opciones"
  que abre el Photo Picker y fija la imagen de calco (sin permisos).
- **Recortar / redimensionar lienzo** (issue 35): diálogo "Recortar o
  redimensionar lienzo". *Recortar al dibujo* calcula la caja del contenido
  (`recortarAlContenido` en `ImagenCalco.kt`, píxeles ≠ fondo) y descarta los
  márgenes en blanco **sin perder ningún trazo** (el recuadro es justamente el
  de la tinta). *Redimensionar* rehace la hoja al tamaño pedido centrando el
  dibujo (`lienzoCentrado`). Los dos usan `DibujoViewModel.abrirDibujo` (recrea
  la hoja con el tamaño nuevo y reasigna la textura GPU); al cambiar el tamaño
  el historial arranca limpio, igual que al expandir. Corre fuera del hilo
  principal (`ajustandoLienzo`).
- **Exportación inteligente** (issue 35): el diálogo de exportación (issue 28)
  suma el interruptor **"Recortar al dibujo"** (por defecto activo): el PNG que
  se guarda o comparte elimina el fondo sin usar y deja sólo lo dibujado. Es
  **no destructivo** (`recortarAlContenido` actúa sobre la copia, nunca sobre la
  hoja del motor), así no puede dañar la creación del usuario. Cambiar el
  interruptor rearma el PNG (`LaunchedEffect` en `PantallaDibujo`).
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
- **Diálogo de exportación + compartir** (issue 28): antes de guardar, al
  tocar exportar se codifica el PNG (`ExportadorGaleria.codificarPng`) fuera
  del hilo principal y `PantallaDibujo` muestra `DialogoExportar` con el peso
  (`formatearTamano`, KB/MB) y dos acciones: **Guardar en galería**
  (`guardarBytes`, pide permiso sólo en API<29) y **Compartir**
  (`ExportadorGaleria.compartir`, `ACTION_SEND` de un PNG temporal en caché via
  `FileProvider` con autoridad `${applicationId}.fileprovider`; declarado en el
  manifest con `res/xml/file_paths.xml`). Compartir no pide permisos. Se
  evitan los bytes duplicados: se codifica una vez y se decide después.
- **Iconos propios de herramientas** (`IconosDibujo.kt`): `material-icons-extended`
  del BOM actual no trae un borrador real, así que hay dos `ImageVector` propios.
  `IconoBorrador` usa el path oficial de Material Symbols `ink_eraser` en versión
  **contorno** (viewport `0 0 960 960`, coordenadas positivas tal como las exporta
  Android; se lee mejor que la rellena y que la escoba `CleaningServices`).
  `IconoTrazoDoble` dibuja **dos trazos paralelos** con `stroke` y reemplaza al
  `ContentCopy` de la herramienta DOBLE, que parecía un botón de "copiar". En la
  fila de herramientas cada botón muestra su nombre debajo (`labelSmall`), así
  todas se identifican de un vistazo.
- Icono de app: adaptive icon XML (sin PNG) con `monochrome`.

### Pantalla principal, navegación y sistema de guardados (issue 30)
Al abrir la app ya no se entra directo al lienzo: se muestra un **menú
principal** con cuatro accesos, cada uno en su propio archivo/pantalla.

- **Navegación** (`Navegacion.kt`, `AppDibujo`): un estado simple
  (`enum Destino { MENU, DIBUJO, CARGAR, OPCIONES }` con `rememberSaveable`) y un
  `when`; sin dependencias nuevas (no se agregó Navigation Compose). `MainActivity`
  compone `AppDibujo()`. El `DibujoViewModel` es el de la Activity, así que la
  hoja **sobrevive** al ir y volver del menú (no se recrea el motor).
- **`PantallaMenu.kt`** (`PantallaMenu`): cuatro tarjetas grandes.
  - *Nuevo dibujo*: empieza una hoja en blanco desde cero. Llama
    `DibujoViewModel.nuevoDibujo()`, que **descarta el motor** (`close()` y
    `motor = null`) para que al volver al lienzo se cree una hoja nueva con el
    tamaño por defecto de la vista y el historial limpio (no arrastra el tamaño
    de una hoja expandida/redimensionada ni el deshacer del dibujo anterior),
    y borra el autoguardado (`AlmacenDibujos.borrarActual()`);
    la biblioteca no se toca. Si hay un dibujo en curso (`hayBorrador`), pide
    confirmación antes de descartarlo.
  - *Continuar dibujando*: si el motor ya está en memoria, sólo vuelve al
    lienzo; si no, carga el borrador autoguardado y lo reabre.
  - *Cargar dibujo*: va a la biblioteca.
  - *Opciones*: va a la pantalla de configuración.
- **`PantallaCargar.kt`** (`PantallaCargar`): lista las copias guardadas con
  miniatura (`AlmacenDibujos.miniatura`, potencias de dos), fecha y tamaño;
  permite abrir (carga los píxeles y llama `DibujoViewModel.abrirDibujo`) o
  borrar (con confirmación).
- **`PantallaOpciones.kt`** (`PantallaOpciones`): la configuración dejó de ser un
  diálogo dentro del lienzo y tiene **pantalla dedicada**. Reúne la elección de
  backend (reusa `OpcionBackend` y `BackendRender.recursoNombre()`, ahora
  `internal` en este archivo) y los **créditos de IA** (atribución de Quick,
  Draw! que exige la licencia CC BY 4.0 del dataset de entrenamiento).
- **`AlmacenDibujos.kt`**: persistencia en `filesDir/dibujos` como PNG (ARGB sin
  pérdida), sin permisos.
  - **Borrador en curso** (`actual.png`, [AlmacenDibujos.SLOT_ACTUAL]): el dibujo
    que se está editando. `DibujoViewModel.autoguardarActual()` lo escribe cuando
    el estado tiene `modificado = true`, al salir del lienzo (onDispose de la
    pantalla) y al irse la app a segundo plano (`ON_STOP`). *Continuar dibujando*
    lo reanuda.
  - **Biblioteca**: "Guardar en la app" (menú "más opciones" del lienzo) crea una
    copia con marca de tiempo; son las que lista *Cargar dibujo*. El export a la
    galería sigue existiendo y es aparte.
- **Carga en el `DibujoViewModel`** (`abrirDibujo(ancho, alto, pixeles)`):
  siempre **recrea el motor** con el tamaño guardado y publica la hoja nueva
  (el render GPU se reasigna: el `key` de `SuperficieGpu` incluye
  `hojaAncho`/`hojaAlto`). El historial arranca limpio, también cuando el tamaño
  coincide con el motor actual. `publicarContenido` refleja la cámara (centrada)
  del motor nuevo. La pantalla de dibujo vuelve al menú con flecha/atrás
  (`BackHandler`).
- Estados nuevos en `DibujoEstado`: `modificado` (hay cambios sin autoguardar).
  `abrirDibujo` lo deja en `true` (lo abierto pasa a ser el borrador en curso).
  Nota de concurrencia: `autoguardarActual` copia la hoja **en el hilo que llama**
  (no después de `close()`), y sólo comprime el PNG fuera del hilo principal; se
  marca `modificado = false` antes de escribir para no perder cambios nuevos.

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
  `hoja = R(−rot)·(pantalla + pan) / zoom` (`rot` en el uniform `uRot`) con
  `texture()` y sampler `LINEAR` (muestreo bilineal; el centro del texel cae en
  la coordenada entera, de ahí `uv = (hoja + 0.5) / uHojaTam`). El color se lee
  del `0xAARRGGBB` en bytes B,G,R,A y se reordena a RGBA. Parámetros en un
  bloque std430 de 64 bytes —`rot` + 3 de relleno, `static_assert` lo fija—
  (`ParametrosPresentacion.h`). El rasterizado del trazo sigue en CPU (paridad
  `MotorTest` intacta); la GPU sólo presenta.
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
- El dibujo editable ya se guarda/reabre en la app (`AlmacenDibujos`, ver
  "Pantalla principal, navegación y sistema de guardados"): hay **borrador en
  curso** autoguardado y **biblioteca** de copias. Lo que todavía no se
  restaura es el **historial** de la sesión (deshacer entre aperturas) ni la
  cámara: al reabrir sólo se recupera la hoja. Release sin firmar y sin más
  metadatos por dibujo (nombre propio).
- Release de `:app` no está firmado para distribución (solo debug).
- El historial es por snapshots: 30 pasos (compartidos C++/Kotlin) para
  acotar la RAM del dispositivo (ver "Historial").

## 7. Datos útiles para agentes

- Horario del usuario: America/Caracas; respondé en español nativo, fixeáte en `opencode.json`.
- Tests: JUnit Platform; paridad en `engine/src/test/kotlin/com/dibujo/engine/MotorTest.kt` (incluye paridad de undo/redo con `historialParidad` y de `reemplazarHoja`). El antialiasing entra en esa paridad: si tocás `disco`/`pintarMezcla` en un lado, corré `:engine:test`.
- **Skills** (`.opencode/skills/`, ver su `README.md`): para el motor nativo hay `semgrep` + `codeql` + `sarif-parsing` (SAST) y `address-sanitizer` (ASan), todos de Trail of Bits; para `:app`, `android-security-audit` (OWASP MASVS v2.0). Antes de instalar o actualizar cualquier skill, escaneá con `npx -y @mondoohq/skillcheck@0.4.0 --project .` (debe dar PASS). Los skills de Trail of Bits son CC BY-SA 4.0 y están pineados a un commit; `c-review` y `modern-cpp` se descartaron (Workflow de Claude Code / solape con `cpp-expert-skill`).
- Modelo IA (**Buril**): el `.tflite` se regenera con `tools/buril.py` (Python + `tensorflow` + `numpy`). **No se versiona** (`.gitignore`). El **buzón oficial es la release `modelo-buril`**, que lo llena el cuaderno de **Kaggle** `tools/buril_kaggle.ipynb` (recomendado: GPU T4 x2, *Save & Run All* en segundo plano sin corte por inactividad; con formulario de versión, por defecto `v1.0.0-experimental.1a`), el de Colab `tools/buril_colab.ipynb` (ahora para pruebas) o `.github/workflows/entrenar-limpiar-trazos-planb.yml` (respaldo, entrena con el `.npz` del Plan B); `android-apk.yml` baja el `.tflite` de esa release y lo inyecta en `app/src/main/assets/modelos/` antes de armar el APK. El `.npz` se cachea en la release **`dataset-buril`** (sobrevive entre sesiones) y los checkpoints en **`checkpoints-buril`** (reanudar) con el asset versionado `pares_esq_lado{LADO}_pc{POR_CATEGORIA}_{VERSION}.npz`. Los datasets reales viven en dos planes: **Plan A** (SketchBench, investigación) en `tools/preparar_dataset_limpiar_trazos.py` + `.github/workflows/preparar-dataset-limpiar-trazos.yml`, y **Plan B** (Quick, Draw!, CC BY 4.0, apto para monetizar) en `tools/preparar_dataset_limpiar_trazos_planb.py` + `.github/workflows/preparar-dataset-limpiar-trazos-planb.yml`. Los dos producen el mismo `.npz`, que se consume con `buril.py --pares <npz>`.
- El workflow `android-apk.yml` corre manual (`workflow_dispatch`) y en cada PR contra main; el workflow falso `build-debug-apk.yml` (APK placeholder aapt2/d8) fue eliminado.
- Commits: esperar orden explícita del usuario (nunca commit proactivo).
