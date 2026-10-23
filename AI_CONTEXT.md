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

### Herramienta DOBLE (trazo doble configurable)
`Herramienta.DOBLE` (ordinal 5, mismo orden que `Tool::DOBLE` en C++) dibuja
**dos trazos con un solo gesto**: el **principal** (con la herramienta
`herramientaPrincipal`: lápiz, portamina o carbóncillo) y el **acompañante** o
"la doble" (con `herramientaDoble`, su `colorDoble` = *color de sombra* y su
`ladoDoble`). Todo eso vive en `Lapiz` (`core/.../Lapiz.kt`) y viaja al motor
por JNI (`nativoConfigurarLapiz` gana cuatro parámetros: la herramienta
principal, la del doble, el lado y el color de sombra).

- **Lado** (`LadoDoble`): `MISMO` (el acompañante se dibuja **encima** del
  principal: queda debajo, así se ve como sombra/contorno si es más grueso o
  de otro color), `IZQUIERDA` y `DERECHA` (desplazado perpendicular; la normal
  `(-dy, dx)` apunta a la derecha del sentido de marcha en coordenadas de
  pantalla). Como el acompañante va **al revés**, el lado se mide respecto del
  propio doble (su izquierda), no del gesto.
- **Sentido inverso (automático)**: el acompañante **nace donde termina** el
  gesto y **vuelve** hacia donde empezaste: si dibujás hacia la derecha, el
  doble va hacia la izquierda. No se configura, sale de la geometría.
- **Separación**: `separacion = (grosor + 2) / 2` px (`TrazoDoble.separacion`),
  con el grosor **efectivo del trazo principal**. Antes el DOBLE pintaba dos
  líneas simétricas respecto del gesto; ahora el principal queda **donde se
  dibuja** y el acompañante va al lado elegido.
- **Geometría**: `TrazoDoble.acompanante` (`core/.../TrazoDoble.kt`) es la
  **única** implementación: invierte el recorrido del gesto y desplaza la
  polilínea al lado elegido; la normal se suaviza por vértice (dirección de la
  cuerda entre vecinos) para que las dos líneas no tiemblan en los empalmes. El
  C++ la replica (`trazoAcompanante` en `DibujoEngine.cpp`) y la app la usa para
  la vista previa y para el doble vectorial (que queda con los puntos
  invertidos: el vector también va al revés).
- **Pincel vectorial** (`HerramientaDoble.PLUMA`): el motor **no** lo dibuja
  (los vectores son objetos vivos de `:app`); la app crea el `TrazoVectorial`
  con la ruta del acompañante, el color de sombra y el grosor, y lo registra en
  el **mismo paso de historial** que los píxeles (`PasoHistorial.Ambos`).
- Paridad: `MotorTest.dobleConfigurableNativoKotlin`,
  `dobleMismoLadoParidadNativoKotlin`, `dobleDibujaElAcompananteAlReves` y
  `dobleConPincelVectorialNoLoDibujaElMotor`.
- En la consola (`:ui`) el menú de lápices suma las opciones 9 (herramienta
  del doble), 10 (lado) y 11 (color de sombra).

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

Con **trazos vectoriales vivos** (`TrazoVectorial`) el motor nativo no puede
verlos: viven en `:app` y no en la hoja. Por eso `DibujoViewModel.tocarHoja` y
`soltarTrazo` desvían el balde a `calcularRellenoConVectores`: copia la hoja,
`VectorHoja.hornearBarrera` dibuja los vectores en una máscara `ALPHA_8` (1
byte por píxel) y pinta su cobertura con un color centinela, `Lienzo.rellenar`
corre con esa barrera y al final los píxeles centinela se restauran desde la
copia. El relleno entra así en el historial de píxeles y los vectores siguen
vivos; el cálculo va en `Dispatchers.Default`. Si la máscara no se puede
reservar, se rellena como antes (sin barrera vectorial) para no fallar.

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
(`tools/buril_colab.ipynb`) entrena y **reanuda desde la release de checkpoints**
(no llena el Drive; Drive es opcional); como respaldo, el workflow
`entrenar-limpiar-trazos-planb.yml`. El **dataset `.npz`** se cachea en una
release aparte, **`dataset-buril`**, para que sobreviva entre sesiones (mismo
buzón que usan el workflow de dataset y el de entrenamiento, con el nombre
versionado `pares_esq_lado{LADO}_pc{POR_CATEGORIA}_{VERSION}.npz`). Los **puntos
de control** (pesos + estado del optimizador, para reanudar) se guardan en la
release **`checkpoints-buril`** (tag configurable a `checkpoints-1.a` etc., con
nombre visible libre; se suben cada ~2 min durante el entrenamiento).
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
- **Rápido y con pulido (~15 min en T4)**: `--lote` (256, fase rápida) y
  `--lote-final` (128, pulido) dividen las épocas (`--fase-rapida`, 75 %; por
  defecto **30 épocas**, `--aumentos 2`, `--parches-por-imagen 8`). El LR
  arranca con `--calentamiento` (1 época) y decae en coseno. En GPU se usa
  **precisión mixta** float16 (`--precision auto`), **XLA** (`--xla auto`) y
  `--pasos-por-ejecucion`; `--distribucion auto` usa **MirroredStrategy** con
  las 2 T4 de Kaggle. La aumentación es **grafo nativo de TF** (sin
  `tf.numpy_function`/GIL) y la validación corre cada `--validacion-cada` épocas.
  El clDice **no** se pide además como métrica (ya está en la pérdida) y usa el
  **esqueleto real** del objetivo (2º canal), con `--cldice-iteraciones 5`. Con
  `--checkpoints` se guarda `ultimo.weights.h5` + `mejor.weights.h5` +
  `estado.json` + `optimizador.npz` por época; `--reanudar <carpeta>` reanuda
  **también el optimizador** (momentos y pasos, no reinicia el LR); `--paciencia`
  activa parada temprana y al final se exporta el **mejor**.
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
  así que premia la conectividad y no sólo la superposición; como el objetivo
  lleva la banda en el canal 0 y el **esqueleto de 1 px** en el canal 1, sólo se
  esqueletiza la predicción). El **LR arranca con calentamiento y decae en
  coseno** (`--lr`, por defecto `2e-3`), y el entrenamiento se parte en **fase
  rápida** (lote 256) y **pulido** (lote 128) con `--fase-rapida 0.75`; por
  defecto se corren **30 épocas** con **`--aumentos 2`**. En GPU va con **precisión
  mixta + XLA** (`--precision auto`, `--xla auto`). La **aumentación es al vuelo**
  con `tf.data` y **ops nativas** (`flujo`: `repeat(aumentos+1)`, shuffle,
  `Dataset.map` con `deterministic=False`), no materializando el set ×(1+aumentos)
  en RAM ni pasando por el GIL de `tf.numpy_function`. Antes
  `aumentar_conjunto` creaba las copias en float32 y las concatenaba: con
  `pc=200`/`aumentos=3` y hojas de 512 px eso pasaba de 10 GB y el proceso moría
  con **`SIGKILL: 9`** (OOM del kernel), no por el tamaño del modelo. La
  memoria ahora es del orden del lote.
  Exporta `.tflite` **float16** (I/O float32, corre en GPU y CPU),
  evalúa **IoU y PSNR** sobre validación con el modelo exportado y guarda un
  `.json` de métricas + atribución (con `"modelo": "Buril <version>"`). La vía
  recomendada es **Kaggle**: `tools/buril_kaggle.ipynb` (driver fino: clona el
  repo, recupera el `.npz` de `dataset-buril` y los checkpoints de
  `checkpoints-buril`, entrena con `--distribucion auto` —MirroredStrategy en las
  2 T4— y la cascada precisión mixta+XLA → float32 → float32 sin XLA → 1 GPU,
  sube checkpoints cada ~2 min y publica en `modelo-buril`). Se corre con **Save
  Version → Save & Run All (Commit)**, que no tiene *idle timeout*.
  `tools/buril_colab.ipynb` también entrena Buril **reanudando desde la release
  `checkpoints-buril`** (sube pesos, estado y optimizador cada ~2 min; Drive es
  opcional) y `tools/kernel-metadata.json` permite subirlo
  con `kaggle kernels push -p tools`. El workflow
  `.github/workflows/entrenar-limpiar-trazos-planb.yml` queda como **respaldo**
  (CPU, misma versión, mismos buzones). El `.npz` se cachea en la release
  `dataset-buril` y el `.tflite` en `modelo-buril`; `android-apk.yml` baja el
  `.tflite` de esa release y lo copia a `app/src/main/assets/modelos/` antes de
  `assembleDebug`. Además, `android-apk.yml` intenta bajar los checkpoints de la
  release (input `tag_checkpoints`, por defecto `checkpoints-buril`) y, si los
  encuentra, los publica como artefacto `buril-checkpoints` de la corrida (no
  entran al APK). Por eso el binario **no se versiona** (ver `LEEME.md`).
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
Undo/redo por **instantáneas comprimidas** de la hoja (RLE de píxeles iguales,
sin pérdida). El tope es **configurable**
(5..100, por defecto **30**) desde *Opciones → Historial*; `MotorDibujo.limiteHistorial`
lo aplica en caliente al motor (`Historial.fijarMaximo` en Kotlin y
`DibujoEngine::fijarLimiteHistorial` en C++, con el mismo recorte de pasos
viejos). Ambos lados tienen que coincidir. El estado Compose expone
`puedeDeshacer`/`puedeRehacer` y habilita/deshabilita los botones. Importante:
el `Canvas` lee el `State` dentro del `draw` (`estadoState.value`), no sólo
durante la composición; si no, undo/redo cambian el motor pero no repintan
(era el bug "se unde y no hace nada").

**Compresión de las instantáneas (RLE).** Antes cada paso guardaba una copia
completa de la hoja (4 bytes por píxel): con una hoja de 1080×2000 eran ~8,6 MB
por paso (~260 MB con 30 pasos) y con una de 4096×4096, 64 MB por paso (~2 GB):
la app se quedaba sin memoria antes de poder deshacer. Ahora:
- Kotlin: `core/.../InstantaneaHoja.kt` (`capturar`/`restaurar`) y
  `core/.../Historial.kt` las usa.
- C++: `comprimirHoja`/`restaurarHoja` en `engine/src/main/cpp/DibujoEngine.cpp`
  (`Instantanea` en el `.h`), y `pasado_`/`futuro_` son vectores de
  instantáneas.
- El formato es el mismo en los dos lados: pares (cantidad, color) y, si el
  RLE no conviene (imagen ruidosa), la copia cruda (así el historial nunca
  ocupa más que antes).
- La compresión es **sin pérdida**: deshacer/rehacer devuelven la hoja
  idéntica. La paridad la cubre `MotorTest.historialComprimidoParidadNativoKotlin`.
- Ojo al tocar el historial: `terminarTrazo` (y `limpiarHoja`, `limpiarTrazos`,
  `reemplazarHoja`, `transformarHoja`) guardan la instantánea **antes** de
  modificar la hoja.

`DibujoViewModel` mantiene además un **historial unificado** en la app
(`PasoHistorial`: `Pixeles`, `Vectores(antes, despues)` o
`Ambos(antes, despues)`, mismo tope configurable) que intercala los snapshots
del motor con los cambios de los objetos vectoriales vivos, sin duplicar la
hoja. `registrarPasoPixeles` se llama cuando el motor guarda un snapshot
(`terminarTrazo`, `limpiarTrazos`), `registrarPasoVectores` en cada edición
de pluma y `registrarPasoAmbos` en `limpiar`/transformar la hoja. Así
deshacer/rehacer respeta el orden real entre píxeles y vectores; al expandir
o abrir otro dibujo el motor limpia su historial y el unificado también. Los
textos siguen fuera del historial (se hornean o descartan al rehacer).

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
| Compose | BOM 2026.09.00 + `kotlin.plugin.compose` 2.4.21; `androidx.compose.animation` (transiciones de pantalla) |
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
  `TopAppBar` (título + motor; acciones deshacer/rehacer/centrar) y barra
  inferior con herramientas/chips de modo/grosor y la paleta. Las herramientas
  son seis (…+ DOBLE), con botones compactos (`IconoToggle(tamano = 34.dp)`)
  para que entren en una fila sin scroll. Todo lo que no es dibujar (calco,
  texto, textura, IA local, enderezar, trazo asistido, estabilizador,
  simetría, guardar, importar, ajustar/transformar/expandir el lienzo,
  exportar, limpiar y configuración) vive en **una sola** hoja inferior:
  *Herramientas de la hoja* (botón "…" de la barra de dibujo).
- **Transformar y color (issue 52)**: *Herramientas de la hoja* suma
  "Transformar dibujo" (`DialogoTransformar`: voltear H/V, escalar 0.25–2× con
  `Slider` y el botón **"Ajustar libremente"** que abre el modo de ajuste por
  gestos — ver *Ajuste libre del dibujo*; todo deshacible). El trazo asistido de
  **líneas rectas** también vive ahí (con tilde cuando está activo).
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
    `TextOverflow.Ellipsis`. Hoy ese `DropdownMenu` ya no existe: todas sus
    acciones se mudaron a la hoja inferior *Herramientas de la hoja* (ver
    *Una sola lista de herramientas*), así que la barra superior quedó en lo
    mínimo (volver, título, deshacer, rehacer y centrar); el subtítulo
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
- **Una sola lista de herramientas**: el `DropdownMenu` "más opciones" de la
  barra superior y la hoja inferior *Herramientas de la hoja* eran dos listas
  distintas para lo mismo (calco/texto/textura/IA en una; enderezar, trazo
  asistido, estabilizador, simetría, guardar, importar, ajustar, transformar,
  exportar, limpiar y configuración en la otra). Ahora **todo vive en la hoja
  inferior** y el `DropdownMenu` se borró: la barra superior queda en volver,
  título, deshacer, rehacer y centrar.
  - `HojaHerramientasHoja` agrupa por bloques con `HorizontalDivider`: calco →
    ayudas al trazo → contenido (texto/textura/IA) → el lienzo → cierre
    (limpiar y configuración). El botón "…" de la barra de dibujo la abre.
  - La hoja es larga: usa `rememberModalBottomSheetState(skipPartiallyExpanded =
    true)` y la columna de contenido lleva `verticalScroll`, si no el texto se
    cortaría contra el borde inferior.
  - Los interruptores (calco visible, líneas rectas, estabilizador, simetría)
    **dejan la hoja abierta** para que se vea la tilde (`AccionHoja.marcada`);
    las acciones que abren un diálogo o el selector del sistema la cierran
    primero (si no, el `AlertDialog` quedaría detrás del `ModalBottomSheet`).
  - `AccionHoja` announce el estado con `contentDescription` = "etiqueta,
    opción activa" (string `opcion_activa`, en minúsculas y sin concordancia
    de género para que sirva para las tres herramientas).
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
- **Calco con detalle real al hacer zoom**: la versión reducida no alcanza
  cuando el usuario amplía mucho, y el calco se veía pixelado. `CapaCalco`
  calcula cuántos píxeles de pantalla cubre un píxel de la imagen **original**
  (`escala · zoom / factor`, con `factor = original / reducida`); si es ≥ 1,
  relee del archivo **sólo la zona visible** con `BitmapRegionDecoder`
  (`decodificarRegionImagen` en `ImagenCalco.kt`, presupuesto de
  `MAX_PIXELES_REGION_CALCO` píxeles) y dibuja esa zona: al cerca se ve el
  detalle real de la foto. La zona se recalcula con un anti-rebote de 120 ms
  (`RETRASO_DETALLE_CALCO`) para no releer el archivo en cada cuadro de un
  gesto; mientras tanto, el resto de la imagen se dibuja con la versión
  reducida **recortada** en cuatro bandas (`dibujarCalcoFuera`) para que el
  alfa del calco no se aplique dos veces en la misma zona. La cuenta de
  coordenadas es la del motor: `hoja = R(−rot)·(pantalla + pan) / zoom`
  (`regionVisibleHoja`).
- **Botón directo del calco y aviso de modo** (issue 88): la barra superior
  tiene un botón de ojo (`Visibility`/`VisibilityOff`) que muestra u oculta la
  imagen de calco al instante, sin abrir el diálogo (sólo aparece si hay
  imagen). Sobre el lienzo flota `IndicadorModo`, una pastilla que dice
  "Dibujando" o "Moviendo" (icono + texto) para saber si el dedo pinta o
  desplaza la vista; es sólo informativa y no intercepta toques.
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
- **Filtros de la imagen de calco**: `core/.../FiltrosImagen.kt` tiene los
  filtros (`FiltroImagen`: `NINGUNO`, `GRIS`, `CONTRASTE`, `UMBRAL`,
  `INVERTIR`, `BORDES`, `SIN_FONDO`) sobre buffers ARGB. Son operaciones de
  **imagen**, no de dibujo: viven en `:core` (como el `Cuantizador`) y **no
  aplican la paridad** C++/Kotlin porque no tocan el motor. El estado del
  ViewModel es `calcoFiltro` + `calcoAjuste` (el parámetro depende del filtro:
  umbral, contraste, sensibilidad o fondo; el rango y el valor por defecto los
  dan `FiltrosImagen.rango`/`valorPorDefecto`). `CapaCalco` decodifica y
  filtra **fuera del hilo principal** (`produceState` + `Dispatchers.IO` /
  `Dispatchers.Default`) y mantiene la imagen anterior mientras recalcula (no
  parpadea). Sirve para que una foto sea calicable de verdad.
- **Estabilizador de mano**: `core/.../EstabilizadorTrazo.kt` suaviza los
  puntos de entrada (filtro exponencial). Se aplica en **espacio de pantalla**
  (allí el temblor mide siempre lo mismo) y recién después se pasan a la hoja:
  `DibujoViewModel.iniciarTrazo`/`continuarTrazo` suavizan y después llaman a
  `aHoja`. El nivel (`estado.estabilizador`, 0..0,95) se guarda en las
  preferencias (`CLAVE_ESTABILIZADOR`), se cambia desde *Herramientas de la
  hoja* (toggle)
  y con un deslizador en *Opciones → Valores de dibujo*. No toca el motor
  (mismo criterio que el calco: **no aplica la paridad**).
- **Simetría del trazo** (`core/.../Simetria.kt`): el motor dibuja el trazo y
  sus reflejos respecto del centro de la hoja (`NINGUNA`, `VERTICAL`,
  `HORIZONTAL`, `CUADRADA`). **Sí es cambio de motor**: va en C++ y en Kotlin
  con paridad (`MotorTest.simetriaParidadNativoKotlin`).
  - El reflejo es una resta: `x' = (ancho - 1) - x` en vertical y
    `y' = (alto - 1) - y` en horizontal (sin trigonometría: así C++ y Kotlin
    dan el mismo píxel). Ojo: `MotorKotlin.reflejar` y
    `DibujoEngine.cpp:reflejarTrazo` tienen que decir exactamente lo mismo.
  - Los reflejos se dibujan al **terminar** el trazo (`terminarTrazo`), así el
    gesto es un solo paso del historial y se deshace de una vez. Por eso la
    vista previa (que es de la app, en `CapaGestos`) tiene que dibujar los
    reflejos a mano para que coincida con lo que pasa al soltar.
  - La simetría radial (N ejes) quedó **pendiente**: necesita cos/sin y ahí
    C++ y Kotlin pueden diferir en la última ulp (el mismo problema que la
    rotación de la cámara, por eso la paridad de cámara se prueba en 0°/90°/180°).
- **Vectorizador y export a SVG** (`core/.../VectorizadorHoja.kt`): convierte
  la hoja raster en un SVG con un `<path>` por figura. Pasos: máscara de tinta
  (distancia al color de fondo > [VectorizadorHoja.UMBRAL_TINTA]), componentes
  conexas (8 vecinos), contorno con el **algoritmo de Moore** (criterio de
  terminación de Jacob), agujeros (el papel encerrado por la tinta: el interior
  de un círculo) recortados con `fill-rule="evenodd"`, y simplificación con
  `RamerDouglasPeucker` (que también usa la pluma vectorial de `:app`).
  Es una operación de **imagen**: no toca el motor y **no aplica la paridad**.
  En la app: botón "SVG" en el diálogo de exportar, que vectoriza fuera del
  hilo principal y lo comparte (`ExportadorGaleria.compartirArchivo`).
- **Texturas del trazo** (issue 70): el acceso vive en la hoja de **más
  herramientas** (los tres puntos horizontales); `DialogoTextura` muestra cada
  opción con un **trazo de muestra** rasterizado con el mismo `Textura.aplicar`
  del motor y `DibujoViewModel.elegirTextura` actualiza el `Lapiz`; el valor se
  propaga a `MotorKotlin`/`MotorNativo` y al C++.
- **Doble trazo configurable** (issue 78): la herramienta **Doble** se
  configura en `DialogoDoble.kt` (archivo propio): con qué herramienta va el
  trazo acompañante (lápiz, portamina, carbóncillo o **pincel vectorial**), de
  qué lado (**sobre el trazo**, a la **izquierda** o a la **derecha** del
  sentido en que se dibuja el doble, que va al revés del gesto) y de qué
  **color de sombra**. El diálogo se abre al elegir
  la herramienta Doble por primera vez (después, desde *Herramientas de la
  hoja* → "Configurar el doble trazo", que muestra el color de sombra) y la
  configuración se aplica **al instante** (el motor se reconfigura). El color de
  sombra sigue al del lápiz mientras el usuario no elija uno propio
  (`DibujoEstado.colorDoblePropio`). La **vista previa** dibuja el doble
  mientras se arrastra (`TrazoDoble.acompanante` + `rutaEnPantalla` en
  `PantallaDibujo.kt`).
- **Texto vivo** (issue 70): `TextoEnHoja` es un objeto del estado
  (`DibujoViewModel.insertarTexto`), no píxeles horneados. `CapaTextos` lo dibuja
  encima de la hoja siguiendo la cámara; un toque lo selecciona y los gestos lo
  mueven (un dedo) y escalan (dos, anclado al foco) igual que el calco, con
  barra de edición ("Editar"/"Quitar"/"Listo"). Se puede volver a editar en
  cualquier momento con `actualizarTextoSeleccionado` (texto, tipografía y
  tamaño). `TextoHoja` compone con `Canvas`/`Paint` las 5 tipografías del
  sistema; `transformarTextos` acompaña al voltear/mover/escalar el dibujo.
  Los cambios de texto viven fuera del historial del motor (deshacer/rehacer
  sólo cubre trazos), así que las operaciones que rehacen la hoja los hornean o
  los descartan.
  Al exportar se hornean en la copia (`copiarHojaConTextos`); al guardar en la
  app viajan en `<id>.textos.json` (`AlmacenDibujos`) para reabrir el dibujo con
  los textos todavía editables. Recortar/redimensionar los hornea porque la
  hoja se rehace. Vive sólo en `:app`, así que no altera la paridad del motor.
- **Pluma vectorial** (issue 72; fase 1 y 2): `TrazoVectorial`
  (`VectorHoja.kt`) es un objeto vivo más de `:app`; la herramienta
  `Herramienta.PLUMA` (última del enum, con `Tool::PLUMA` en C++ sólo para
  conservar el ordinal de JNI) captura un arrastre como polilínea en px de
  hoja. Al soltar se simplifica con Ramer-Douglas-Peucker y se dibuja suavizada
  con Catmull-Rom → Bézier cúbicas, en una capa Compose (`CapaVectores`) encima
  del raster y debajo de los textos. Un toque selecciona el vector (caja guía),
  un dedo lo mueve y dos lo escalan; la barra inferior permite **Quitar** o
  **Listo**.
  - **Color**: el trazo nuevo usa `lapiz.color` y, con un vector seleccionado,
    elegir color (rueda HSV, paleta o calco) lo recolorea; es un paso de
    historial.
  - **Textura**: el trazo guarda `textura` y el horneado la aplica con
    `VectorHoja.rasterizar` (`TrazoRasterizado`: bitmap recortado a su caja +
    origen), usando `Textura.aplicar` en coordenadas de hoja para que el grano
    no se corra al mover el vector. Tope `MAX_RASTER_PIXELES = 4_000_000`; si se
    excede, cae a dibujar la ruta sin textura. La capa viva cachea por id el
    bitmap texturizado (`RasterVivo`) y dibuja liso el vector en edición y el
    trazo en curso (la textura aparece al soltar).
  - **Deshacer/rehacer**: integrado en el historial unificado del ViewModel
    (ver "Historial"). Un gesto de mover/escalar es un solo paso: la capa de
    gestos llama `iniciarTransformacionVector` al apoyar y
    `confirmarTransformacionVector` al terminar.
  - **Balde**: el relleno con PINTAR toma los vectores como contorno (se
    hornean como barrera centinela en una copia, ver "Herramienta PINTAR").
  - **Persistencia/horneado**: se hornea sólo al exportar/miniatura o al rehacer
    la hoja (recortar/redimensionar), viaja en `<id>.vectores.json` (con
    `textura`, por defecto LISA) y `transformarDibujo`/terminar el ajuste libre
    le aplican el mismo mapeo que a los textos. No toca el rasterizado ni la
    paridad. Fase 2 pendiente: edición de nodos, relleno, figuras y export SVG.
- **Importar imagen** (issue 35): entrada explícita en *Herramientas de la hoja*
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
  (`enum Destino { MENU, DIBUJO, CARGAR, OPCIONES, OPCIONES_MOTOR,
  OPCIONES_DIBUJO, OPCIONES_RETROALIMENTACION, OPCIONES_HISTORIAL }` con
  `rememberSaveable`) y un `when` dentro de `AnimatedContent` (Compose
  `animation`): al entrar a un apartado el contenido entra deslizando desde la
  derecha y el anterior se desvanece, y al volver el sentido se invierte según
  `profundidad(Destino)`; sin Navigation Compose. `MainActivity` compone
  `AppDibujo()`. El `DibujoViewModel` es el de la Activity, así que la hoja
  **sobrevive** al ir y volver del menú (no se recrea el motor).
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
- **Configuración por apartados** (cada uno en su archivo/pantalla, unificados
  por `ComponentesOpciones.kt` con `MarcoOpcion`, `TarjetaAjuste` y `FilaOpcion`):
  - **`PantallaOpciones.kt`**: índice con tarjetas (motor, valores de dibujo,
    retroalimentación e historial) y los **créditos de IA** (atribución de
    Quick, Draw! que exige la licencia CC BY 4.0 del dataset de entrenamiento).
  - **`PantallaOpcionesMotor.kt`**: backend OpenGL ES / Vulkan / automático,
    versión de OpenGL ES, Vulkan soportado y render GPU en uso.
  - **`PantallaOpcionesDibujo.kt`**: herramienta, grosor y textura con los que
    arranca un dibujo nuevo (`DibujoEstado.herramientaInicial`, `grosorInicial`,
    `texturaInicial`; se aplican en `nuevoDibujo()`). El número del grosor se
    actualiza **mientras se arrastra** el slider (el valor se persiste al
    soltar) y cada textura muestra su trazo real con `VistaPreviaTextura`
    (`VistaPreviaTextura.kt`, compartido con el diálogo del lienzo).
  - **`PantallaOpcionesRetroalimentacion.kt`**: vibración háptica y sonidos al
    dibujar/deshacer/rehacer (`Retroalimentacion.kt`, `Vibrador` +
    `ToneGenerator`; vibración activada y sonidos apagados por defecto).
  - **`PantallaOpcionesHistorial.kt`**: tope del historial (5..100, por defecto
    30) aplicado en caliente con `MotorDibujo.limiteHistorial`; el número
    también se refresca en vivo mientras se arrastra el slider.
- **`AlmacenDibujos.kt`**: persistencia en `filesDir/dibujos` como PNG (ARGB sin
  pérdida), sin permisos.
  - **Borrador en curso** (`actual.png`, [AlmacenDibujos.SLOT_ACTUAL]): el dibujo
    que se está editando. `DibujoViewModel.autoguardarActual()` lo escribe cuando
    el estado tiene `modificado = true`, al salir del lienzo (onDispose de la
    pantalla) y al irse la app a segundo plano (`ON_STOP`). *Continuar dibujando*
    lo reanuda.
  - **Biblioteca**: "Guardar en la app" (en *Herramientas de la hoja*) crea una
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
- **Estado triestado del cuadro** (`EstadoCuadro` en `RenderizadorGpu.h`):
  `PRESENTADO` (0), `REINTENTAR` (1) y `FALLO` (2). Antes `render()` devolvía
  `bool` y **cualquier** error transitorio (superficie 0×0 durante un cambio
  brusco de interfaz, `VK_ERROR_OUT_OF_DATE_KHR`, formatos no disponibles)
  terminaba en `gpuFracaso()` → la app caía **definitivamente** al render por
  CPU (de ahí el "lag" que veía el usuario). Ahora sólo `FALLO` (dispositivo
  perdido, fallo de `vkQueueSubmit`/`vkQueuePresentKHR`) cae a CPU; lo demás se
  reintenta en el cuadro siguiente. La superficie 0×0 marca
  `superficieInvalida_` y no es fatal; `VK_ERROR_SURFACE_LOST_KHR` reconstruye
  la `VkSurfaceKHR` desde la `ANativeWindow`.
- **Sin parada de la GPU al subir la hoja**: la copia `staging → hoja` se
  graba en el **command buffer del propio cuadro** (`grabarSubidaHoja`) con sus
  barreras de disposición, en vez de un command buffer propio con
  `vkQueueWaitIdle` (que serializaba CPU y GPU en cada trazo). La subida
  inicial (`subirHojaInicial`) sí usa `vkQueueWaitIdle`: ocurre una sola vez.
- **Recursos por cuadro en vuelo** (`kMaxCuadrosEnVuelo = 2`): command buffer,
  semáforos de adquisición/presentación, valla y buffer de subida son uno por
  ranura, así un cuadro no pisa lo que el otro todavía está usando. Antes el
  command buffer y el semáforo de adquisición eran únicos y se reutilizaban
  con dos cuadros en vuelo (corrida de sincronización). `vw_`/`vh_` y
  `reiniciarCadena_` son atómicos (los escribe el hilo de la interfaz).
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
  `nativoGpuRender` devuelve un `Int` con el `EstadoCuadro` (0/1/2), no un
  booleano: el hilo de render necesita distinguir "reintentá" de "perdés".
- Vulkan GLSL se compila a SPIR-V en configure (glslc del NDK) e incrusta en
  `shaders_vk_generado.h`; el CMake lo hace sólo bajo `if(ANDROID)`.

**Lado Kotlin:** `SuperficieGpu` (`:app`) es un `SurfaceView` + hilo de render
(`HiloRenderGpu`, `ReentrantLock`/`Condition`) que crea el renderizador y pide
cuadros on-demand. El hilo presenta un primer cuadro **antes** de avisar
`gpuListo`: así la pantalla de carga se retira con la hoja ya pintada y no se
ve un destello negro. `DibujoEngine` expone `revision()`/`copiarHoja()`/
`leerCamara()` con mutex para que el hilo GPU lea un snapshot coherente.
`HiloRenderGpu` trata el estado del cuadro: `0` sigue, `1` reintenta con
espera creciente (16→80 ms, para no martillear la GPU mientras la superficie
tarda en estar lista) y `2` es el único que dispara `gpuFracaso()`. También
reintenta la **creación** del renderizador (25 veces) por si la superficie
todavía no está lista al abrir la pantalla.
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
- El historial guarda instantáneas **comprimidas** (RLE sin pérdida): tope
  configurable 5..100 (30 por defecto, compartido C++/Kotlin) y la memoria por
  paso cae de ~8,6 MB a decenas de KB en una hoja normal (ver "Historial").
- La simetría del trazo es de **espejo** (vertical, horizontal y cuadrada). La
  **radial** quedó pendiente: necesita cos/sin y ahí C++ y Kotlin pueden
  diferir en la última ulp.
- El export a **SVG** vectoriza el dibujo raster (contornos con Moore). El
  resultado es una aproximación: las curvas no son Bézier y las figuras muy
  curvas quedan poligonales. La **simetría radial** y el *skeleton* (trazo con
  ancho, en vez de contorno relleno) son la paso siguiente.

## 7. Datos útiles para agentes

- Horario del usuario: America/Caracas; respondé en español nativo, fixeáte en `opencode.json`.
- Tests: JUnit Platform; paridad en `engine/src/test/kotlin/com/dibujo/engine/MotorTest.kt` (incluye paridad de undo/redo con `historialParidad` y de `reemplazarHoja`). El antialiasing entra en esa paridad: si tocás `disco`/`pintarMezcla` en un lado, corré `:engine:test`.
- **REA (Reverse Engineer Anything)**: MCP local registrado en `opencode.json` como `rea` (`npx -y rea-agents@6.7.0 mcp`); exige **Node >= 22.19**. Sirve para analizar estáticamente **nuestro APK** o software propio/autorizado: `inspect_android_package` (manifest, permisos, SDK), `search_android_classes`, `inspect_android_class` / `inspect_android_method`, `trace_android_references`, recursos con apktool y apps Flutter. En GitHub Actions, `opencode.yml` usa Node 22, cachea y verifica `jadx-headless-mcp-0.7.1-all.jar` (SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`) y exporta `REA_JADX_MCP_JAR`, que el servidor MCP hereda. Límites del runner: **sin adb/emulador/dispositivo** y **sin Hopper/Ghidra/IDA**, así que no hay análisis nativo de binarios; sólo estático (APK/JS/.NET). Uso legítimo: apps propias o con autorización; no copiar código, assets ni marcas de terceros. Lo que se reconstruya se implementa igual en C++ y Kotlin y pasa `MotorTest`; REA no reemplaza la paridad.
- Nuevo workflow manual `.github/workflows/rea-reverse-engineering.yml` (`workflow_dispatch`, disparo con `gh workflow run rea-reverse-engineering.yml` o `scripts/ci/dispatch-rea.sh`) que prepara el entorno completo de REA: JDK 21, Node 22, Python 3.11, jadx 0.7.1, apktool, adb, binutils (objdump), gdb, rizin, tshark y Ghidra headless. Acepta scripts `.py`/`.js` a ejecutar (`script_py`/`script_js`) y sube artefactos. Helpers: `scripts/ci/ejecutar-py.sh`, `scripts/ci/ejecutar-js.sh`.
- **Skills** (`.opencode/skills/`, ver su `README.md`): para el motor nativo hay `semgrep` + `codeql` + `sarif-parsing` (SAST) y `address-sanitizer` (ASan), todos de Trail of Bits; para `:app`, `android-security-audit` (OWASP MASVS v2.0). Antes de instalar o actualizar cualquier skill, escaneá con `npx -y @mondoohq/skillcheck@0.4.0 --project .` (debe dar PASS). Los skills de Trail of Bits son CC BY-SA 4.0 y están pineados a un commit; `c-review` y `modern-cpp` se descartaron (Workflow de Claude Code / solape con `cpp-expert-skill`).
- **Subagentes propios** (`.opencode/agents/*.md`, formato markdown de opencode 1.19.3; el nombre del archivo **es** el nombre del agente): `diseno-kotlin` (Compose/UI, busca diseño en la web), `logica-kotlin` (MVI, coroutines, paridad C++/Kotlin), `auditor-seguridad` (OWASP MASVS, SAST/SCA, **solo lectura**), `experto-opengl` (EGL/GLES 3.x, `engine/src/main/cpp/gpu`), `experto-vulkan` (spec/VUIDs/MoltenVK) e `investigador-web` (docs, CVEs y código real; **solo lectura**). Todos fijan `model: opencode/minimax-m3.1-flash-free` (el mismo del agente primario `build`), `task: deny` (sin anidamiento) y `question: deny` (no hay interacción en el runner). Se invocan desde el primario con la herramienta `task` o con `@nombre`; aparecen en `opencode agent list`. Ojo: un subagente = 1 llamada extra de LLM, así que conviene paralelizar y no encadenar.
- Modelo IA (**Buril**): el `.tflite` se regenera con `tools/buril.py` (Python + `tensorflow` + `numpy`). **No se versiona** (`.gitignore`). El **buzón oficial es la release `modelo-buril`**, que lo llena el cuaderno de **Kaggle** `tools/buril_kaggle.ipynb` (recomendado: GPU T4 x2, *Save & Run All* en segundo plano sin corte por inactividad; con formulario de versión, por defecto `v1.0.0-experimental.1a`), el de Colab `tools/buril_colab.ipynb` (entrena y reanuda desde checkpoints en la release; Drive opcional) o `.github/workflows/entrenar-limpiar-trazos-planb.yml` (respaldo, entrena con el `.npz` del Plan B); `android-apk.yml` baja el `.tflite` de esa release y lo inyecta en `app/src/main/assets/modelos/` antes de armar el APK (además baja los checkpoints de `checkpoints-buril` si existen y los publica como artefacto `buril-checkpoints`). El `.npz` se cachea en la release **`dataset-buril`** (sobrevive entre sesiones) y los checkpoints en **`checkpoints-buril`** (reanudar; tag configurable con nombre visible libre) con el asset versionado `pares_esq_lado{LADO}_pc{POR_CATEGORIA}_{VERSION}.npz`. Los datasets reales viven en dos planes: **Plan A** (SketchBench, investigación) en `tools/preparar_dataset_limpiar_trazos.py` + `.github/workflows/preparar-dataset-limpiar-trazos.yml`, y **Plan B** (Quick, Draw!, CC BY 4.0, apto para monetizar) en `tools/preparar_dataset_limpiar_trazos_planb.py` + `.github/workflows/preparar-dataset-limpiar-trazos-planb.yml`. Los dos producen el mismo `.npz`, que se consume con `buril.py --pares <npz>`.
- El workflow `android-apk.yml` corre manual (`workflow_dispatch`) y en cada PR contra main; el workflow falso `build-debug-apk.yml` (APK placeholder aapt2/d8) fue eliminado.
- Commits: esperar orden explícita del usuario (nunca commit proactivo).
