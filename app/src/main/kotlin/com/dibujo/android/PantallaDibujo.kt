package com.dibujo.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as ColorAndroid
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mode
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dibujo.core.BackendRender
import com.dibujo.core.Camara
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Forzamos iconos oscuros en las barras del sistema: el tema de la app
        // es claro y sin esto, con el teléfono en modo oscuro, la hora y la
        // batería quedaban blancas sobre fondo claro (invisibles).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                ColorAndroid.TRANSPARENT,
                ColorAndroid.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                ColorAndroid.TRANSPARENT,
                ColorAndroid.TRANSPARENT,
            ),
        )
        setContent {
            TemaDibujo {
                PantallaDibujo()
            }
        }
    }
}

/**
 * Tema de la app. Fija `fontScale = 1` para que el tamaño del texto no
 * dependa de la configuración de letra del teléfono del usuario (la UI de una
 * app de dibujo debe verse igual en todos los equipos).
 */
@Composable
fun TemaDibujo(content: @Composable () -> Unit) {
    val densidadBase = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = densidadBase.density,
            fontScale = 1f,
        ),
    ) {
        MaterialTheme(content = content)
    }
}

/** Pantalla principal: barra superior (acciones) + hoja táctil + herramientas. */
@Composable
fun PantallaDibujo(vm: DibujoViewModel = viewModel()) {
    val estadoState = vm.estado.collectAsState()
    val estado = estadoState.value
    var mostrarConfig by remember { mutableStateOf(false) }
    var mostrarExpandir by remember { mutableStateOf(false) }
    var mostrarCalco by remember { mutableStateOf(false) }

    // Selector de imagen del sistema (Photo Picker): no requiere permisos.
    val elegirImagen = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> vm.elegirCalco(uri?.toString()) }

    // --- Exportar el dibujo a la galería ---------------------------------
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var exportando by remember { mutableStateOf(false) }
    // Los textos se leen en composición (no con `context.getString` dentro de
    // la corrutina) para que respeten cambios de configuración.
    val textoExportadoOk = stringResource(R.string.exportado_ok)
    val textoExportadoError = stringResource(R.string.exportado_error)
    val textoExportadoSinPermiso = stringResource(R.string.exportado_sin_permiso)

    // Copia la hoja a un Bitmap y la guarda en la galería. El copiado y la
    // compresión van fuera del hilo principal (hojas grandes = varios MB).
    fun exportarAhora() {
        if (exportando) return
        val ancho = estadoState.value.hojaAncho
        val alto = estadoState.value.hojaAlto
        if (ancho <= 0 || alto <= 0) return
        exportando = true
        alcance.launch {
            val bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    val pixeles = IntArray(ancho * alto)
                    if (!vm.copiarHoja(pixeles)) {
                        null
                    } else {
                        Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888)
                    }
                }.getOrNull()
            }
            val guardado = if (bitmap != null) {
                val marca = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val ok = withContext(Dispatchers.IO) {
                    ExportadorGaleria.guardar(contexto, bitmap, "dibujo_$marca")
                }
                bitmap.recycle()
                ok
            } else {
                false
            }
            exportando = false
            Toast.makeText(
                contexto,
                if (guardado) textoExportadoOk else textoExportadoError,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // En API < 29 hace falta WRITE_EXTERNAL_STORAGE; desde Android 10 (Q) el
    // guardado por MediaStore no pide permisos.
    val permisoEscritura = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) {
            exportarAhora()
        } else {
            Toast.makeText(
                contexto,
                textoExportadoSinPermiso,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val alExportar = {
        val faltaPermiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            contexto.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (faltaPermiso) {
            permisoEscritura.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            exportarAhora()
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                estado = estado,
                alDeshacer = vm::deshacer,
                alRehacer = vm::rehacer,
                alCentrar = vm::centrar,
                alEnderezar = vm::enderezar,
                alLimpiar = vm::limpiar,
                alExportar = alExportar,
                exportando = exportando,
                alAbrirConfig = { mostrarConfig = true },
            )
        },
        bottomBar = {
            BarraHerramientas(
                estado = estado,
                vm = vm,
                alAbrirExpandir = { mostrarExpandir = true },
                alElegirImagen = {
                    elegirImagen.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                alAlternarCalco = vm::alternarCalco,
                alAbrirCalco = { mostrarCalco = true },
            )
        },
        // Transparente: deja ver la superficie GPU que va en una capa detrás
        // de la ventana (el `SurfaceView`). Las barras y la vista previa del
        // trazo se dibujan encima en la ventana de Compose.
        containerColor = Color.Transparent,
    ) {
        // A propósito NO aplicamos `margenes`: la hoja ocupa toda la ventana
        // (detrás de las barras). Así la superficie GPU, la capa de gestos y el
        // motor comparten EXACTAMENTE el mismo origen y tamaño (pantalla
        // completa), y el trazo cae justo donde se dibuja. Las barras se pintan
        // encima y siguen recibiendo el toque.
        AreaDibujo(estadoState, vm, Modifier.fillMaxSize())
    }

    if (mostrarConfig) {
        DialogoConfiguracion(
            estado = estado,
            alElegirBackend = vm::elegirBackend,
            alCerrar = { mostrarConfig = false },
        )
    }

    if (mostrarExpandir) {
        DialogoExpandir(
            estado = estado,
            alExpandir = { extraAncho, extraAlto ->
                vm.expandirHoja(extraAncho, extraAlto)
                mostrarExpandir = false
            },
            alCerrar = { mostrarExpandir = false },
        )
    }

    if (mostrarCalco) {
        DialogoCalco(
            estado = estado,
            alElegirImagen = {
                elegirImagen.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            alAlternar = vm::alternarCalco,
            alCambiarAlfa = vm::cambiarCalcoAlfa,
            alQuitar = { vm.elegirCalco(null) },
            alCerrar = { mostrarCalco = false },
        )
    }
}

/** Barra superior: título + motor + deshacer/rehacer/centrar/limpiar/config. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraSuperior(
    estado: DibujoEstado,
    alDeshacer: () -> Unit,
    alRehacer: () -> Unit,
    alCentrar: () -> Unit,
    alEnderezar: () -> Unit,
    alLimpiar: () -> Unit,
    alExportar: () -> Unit,
    exportando: Boolean,
    alAbrirConfig: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.absorberToque(),
        title = {
            Column {
                Text(stringResource(R.string.app_name))
                Text(
                    stringResource(R.string.estado_motor, estado.motorNombre),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            BotonIcono(
                icono = Icons.AutoMirrored.Rounded.Undo,
                descripcion = stringResource(R.string.cd_deshacer),
                habilitado = estado.puedeDeshacer,
                alPulsar = alDeshacer,
            )
            BotonIcono(
                icono = Icons.AutoMirrored.Rounded.Redo,
                descripcion = stringResource(R.string.cd_rehacer),
                habilitado = estado.puedeRehacer,
                alPulsar = alRehacer,
            )
            BotonIcono(
                icono = Icons.Rounded.CenterFocusWeak,
                descripcion = stringResource(R.string.cd_centrar),
                alPulsar = alCentrar,
            )
            BotonIcono(
                icono = Icons.Rounded.ScreenRotation,
                descripcion = stringResource(R.string.cd_enderezar),
                habilitado = estado.camara.rotacion != 0f,
                alPulsar = alEnderezar,
            )
            BotonIcono(
                icono = Icons.Rounded.Delete,
                descripcion = stringResource(R.string.cd_limpiar),
                alPulsar = alLimpiar,
            )
            BotonIcono(
                icono = Icons.Rounded.SaveAlt,
                descripcion = stringResource(R.string.cd_exportar),
                habilitado = !exportando,
                alPulsar = alExportar,
            )
            BotonIcono(
                icono = Icons.Rounded.Settings,
                descripcion = stringResource(R.string.cd_configuracion),
                alPulsar = alAbrirConfig,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * Área de dibujo: superficie GPU con caída a CPU y capa de gestos encima.
 *
 * La hoja se presenta por GPU sólo si el equipo la soporta, no falló antes y
 * el motor nativo está disponible; si no, se pinta por CPU en un `Canvas`.
 * Los gestos y la vista previa del trazo van siempre en una capa Compose
 * encima, así funcionan igual con GPU o CPU.
 */
@Composable
private fun AreaDibujo(
    estadoState: State<DibujoEstado>,
    vm: DibujoViewModel,
    modifier: Modifier = Modifier,
) {
    val estado = estadoState.value
    var dimension by remember { mutableStateOf(IntSize.Zero) }
    val usarGpu = estado.gpuDisponible && !estado.gpuFallo && vm.handleMotor() != 0L

    Box(
        modifier.onSizeChanged { tamano ->
            if (tamano != dimension) dimension = tamano
            vm.inicializar(tamano.width, tamano.height)
        },
    ) {
        if (usarGpu) {
            // Recrear al cambiar de backend: un renderizador OpenGL no sirve
            // para Vulkan y viceversa. También al cambiar el tamaño de la hoja
            // (expandir): la textura se asigna con las dimensiones del motor,
            // así que un renderizador recreado evita gestionar la reasignación.
            key(estado.backendEfectivo, estado.hojaAncho, estado.hojaAlto) {
                SuperficieGpu(estadoState, vm, Modifier.fillMaxSize())
            }
        } else {
            LienzoCpu(estadoState, vm, dimension, Modifier.fillMaxSize())
        }
        // Imagen de calco: capa translúcida sobre la hoja (GPU o CPU) y debajo
        // del trazo en curso, para poder calcar viendo el dibujo al mismo tiempo.
        CapaCalco(estadoState, Modifier.fillMaxSize())
        CapaGestos(estadoState, vm, Modifier.fillMaxSize())

        // Mientras el motor/renderizador se crea (al abrir la app o al cambiar
        // de backend) tapamos la superficie para no mostrar el cuadro en negro.
        if (estado.motorNombre.isEmpty() || estado.gpuCargando) {
            PantallaCarga(estado.backendEfectivo, Modifier.fillMaxSize())
        }
    }
}

/**
 * Pantalla de carga del render GPU: cubre el área de dibujo mientras el
 * renderizador nativo (OpenGL ES o Vulkan) se inicializa y publica su primer
 * cuadro. Evita el destello negro de una superficie aún sin inicializar.
 */
@Composable
private fun PantallaCarga(
    backend: BackendRender,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.cd_cargando_render)
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(
                    R.string.cargando_render,
                    stringResource(backend.recursoNombre()),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Hoja rasterizada por CPU: reserva para equipos sin render GPU. */
@Composable
private fun LienzoCpu(
    estadoState: State<DibujoEstado>,
    vm: DibujoViewModel,
    dimension: IntSize,
    modifier: Modifier = Modifier,
) {
    val buffer = remember(dimension) {
        IntArray((dimension.width * dimension.height).coerceAtLeast(1))
    }
    val bitmap = remember(dimension) {
        if (dimension.width > 0 && dimension.height > 0) {
            Bitmap.createBitmap(dimension.width, dimension.height, Bitmap.Config.ARGB_8888)
        } else {
            null
        }
    }

    Canvas(modifier) {
        // Leer el State dentro del draw suscribe el repintado a *todo* cambio
        // del estado. Sin esto, undo/redo/limpiar (que sólo tocan `marco`)
        // cambiaban el motor pero el Canvas no se volvía a pintar: el famoso
        // "se unde y no hace nada".
        val fotograma = estadoState.value.marco
        val bmp = bitmap ?: return@Canvas
        val vw = size.width.toInt().coerceAtLeast(1)
        val vh = size.height.toInt().coerceAtLeast(1)
        if (fotograma >= 0) {
            vm.renderizar(buffer, vw, vh)
            bmp.setPixels(buffer, 0, vw, 0, 0, vw, vh)
        }
        drawImage(bmp.asImageBitmap())
    }
}

/**
 * Capa transparente de entrada: captura los gestos y dibuja la vista previa
 * del trazo en curso. Va encima de la hoja (GPU o CPU) para que la marca se
 * vea al instante sin esperar a que el motor la rasterice.
 */
@Composable
private fun CapaGestos(
    estadoState: State<DibujoEstado>,
    vm: DibujoViewModel,
    modifier: Modifier = Modifier,
) {
    // EDICION: un dedo dibuja; MOVER: pan y pinch de la cámara.
    val modo = estadoState.value.modo
    Canvas(
        modifier.pointerInput(modo) {
            if (modo == Modo.MOVER) {
                // Pan + pinch de zoom + rotación de dos dedos, todos anclados
                // al foco (el punto bajo los dedos). La rotación llega en
                // grados y el motor la usa en radianes.
                detectTransformGestures { foco, pan, zoom, rotacion ->
                    if (zoom != 1f) vm.zoomear(zoom, foco.x, foco.y)
                    if (rotacion != 0f) {
                        vm.rotar(Math.toRadians(rotacion.toDouble()).toFloat(), foco.x, foco.y)
                    }
                    if (pan != Offset.Zero) vm.panear(pan.x, pan.y)
                }
            } else {
                detectDragGestures(
                    onDragStart = { vm.iniciarTrazo(it.x, it.y) },
                    onDragEnd = { vm.soltarTrazo() },
                    onDragCancel = { vm.cancelarTrazo() },
                    onDrag = { cambio, _ ->
                        vm.continuarTrazo(cambio.position.x, cambio.position.y)
                        cambio.consume()
                    },
                )
            }
        }
            // Toque sin arrastre: punto con el lápiz o relleno con PINTAR.
            .pointerInput(modo) {
                if (modo == Modo.EDICION) {
                    detectTapGestures { punto -> vm.tocarHoja(punto.x, punto.y) }
                }
            },
    ) {
        val estado = estadoState.value
        val trazo = estado.trazoEnCurso
        val camara = estado.camara
        val lapiz = estado.lapiz
        // El borrador se previsualiza como lo rasteriza el motor: pinta con el
        // fondo de la hoja y usa su grosor efectivo, así la marca se ve
        // "borrada" al instante mientras arrastrás el dedo.
        val borran = lapiz.herramienta == Herramienta.BORRADOR
        if (trazo != null && trazo.size >= 2 && lapiz.herramienta != Herramienta.PINTAR) {
            val colorTrazo = if (borran) Color(Colores.BLANCO) else Color(lapiz.color)
            val grosorTrazo = lapiz.grosorEfectivo()
            val path = Path()
            val primero = aPantallaCamara(camara, trazo[0], trazo[1])
            path.moveTo(primero.x, primero.y)
            var i = 2
            while (i + 1 < trazo.size) {
                val p = aPantallaCamara(camara, trazo[i], trazo[i + 1])
                path.lineTo(p.x, p.y)
                i += 2
            }
            drawPath(
                path,
                color = colorTrazo,
                style = Stroke(width = grosorTrazo * camara.zoom),
            )
        }
    }
}

/** Punto de pantalla de una coordenada de hoja según la cámara (con rotación). */
private fun aPantallaCamara(cam: Camara, hx: Float, hy: Float): Offset {
    val zx = hx * cam.zoom
    val zy = hy * cam.zoom
    val c = cos(cam.rotacion.toDouble()).toFloat()
    val s = sin(cam.rotacion.toDouble()).toFloat()
    return Offset(c * zx - s * zy - cam.panX, s * zx + c * zy - cam.panY)
}

/**
 * Imagen de calco: capa translúcida que se dibuja sobre la hoja (GPU o CPU) y
 * debajo de la vista previa del trazo. La imagen se ajusta (manteniendo la
 * proporción) dentro de la hoja y sigue la cámara (pan/zoom), así se puede
 * calcar con el dibujo a la vista.
 */
@Composable
private fun CapaCalco(
    estadoState: State<DibujoEstado>,
    modifier: Modifier = Modifier,
) {
    val estado = estadoState.value
    val uri = estado.calcoUri
    val contexto = LocalContext.current
    val bitmap = remember(uri) {
        uri?.let { ruta -> cargarBitmap(contexto, Uri.parse(ruta)) }
    }
    if (bitmap == null || !estado.calcoVisible ||
        estado.hojaAncho <= 0 || estado.hojaAlto <= 0
    ) {
        return
    }
    val descripcion = stringResource(R.string.cd_calco)
    Canvas(modifier.semantics { contentDescription = descripcion }) {
        val imagen = bitmap.asImageBitmap()
        if (imagen.width <= 0 || imagen.height <= 0) return@Canvas
        val cam = estado.camara
        val escala = minOf(
            estado.hojaAncho.toFloat() / imagen.width,
            estado.hojaAlto.toFloat() / imagen.height,
        )
        val anchoHoja = imagen.width * escala
        val altoHoja = imagen.height * escala
        val xHoja = (estado.hojaAncho - anchoHoja) / 2f
        val yHoja = (estado.hojaAlto - altoHoja) / 2f
        // La imagen se dibuja en coordenadas de hoja y el `withTransform` la
        // lleva a pantalla igual que el motor: pantalla = R(rot)·(hoja·zoom) − pan.
        // (Orden de transformaciones = se aplica la primera envuelta: T·R·S.)
        withTransform({
            translate(-cam.panX, -cam.panY)
            rotate(
                degrees = Math.toDegrees(cam.rotacion.toDouble()).toFloat(),
                pivot = Offset.Zero,
            )
            scale(scaleX = cam.zoom, scaleY = cam.zoom, pivot = Offset.Zero)
        }) {
            drawImage(
                image = imagen,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(imagen.width, imagen.height),
                dstOffset = IntOffset(xHoja.roundToInt(), yHoja.roundToInt()),
                dstSize = IntSize(anchoHoja.roundToInt(), altoHoja.roundToInt()),
                alpha = estado.calcoAlfa,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** Lado máximo al que se reduce la imagen de calco al decodificarla. */
private const val MAX_CALCO = 2048

/**
 * Decodifica la imagen elegida reduciéndola si es muy grande ([MAX_CALCO] por
 * lado). Devuelve null si no se puede leer. El muestreo se calcula en potencias
 * de dos (`inSampleSize`) para no agotar la memoria con fotos enormes.
 */
private fun cargarBitmap(contexto: Context, uri: Uri): Bitmap? = runCatching {
    val resolver = contexto.contentResolver
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var escala = 1
    while (limites.outWidth / escala > MAX_CALCO || limites.outHeight / escala > MAX_CALCO) {
        escala *= 2
    }
    val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
}.getOrNull()

/**
 * Barra inferior. Tres filas para no depender del scroll horizontal (que era
 * incómodo): modo + acciones de hoja/calco, herramientas + grosor, y paleta.
 */
@Composable
private fun BarraHerramientas(
    estado: DibujoEstado,
    vm: DibujoViewModel,
    alAbrirExpandir: () -> Unit,
    alElegirImagen: () -> Unit,
    alAlternarCalco: () -> Unit,
    alAbrirCalco: () -> Unit,
) {
    Surface(modifier = Modifier.absorberToque(), tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                // Deja libre la barra de navegación (gestos o botones): sin
                // esto la paleta quedaba debajo y no se podía tocar.
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Fila 1: modo + acciones de calco y hoja.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FiltroChip(
                    seleccionado = estado.modo == Modo.EDICION,
                    etiqueta = stringResource(
                        if (estado.modo == Modo.EDICION) R.string.modo_editar else R.string.modo_mover,
                    ),
                    icono = if (estado.modo == Modo.EDICION) Icons.Rounded.Gesture else Icons.Rounded.PanTool,
                    descripcion = stringResource(
                        if (estado.modo == Modo.EDICION) R.string.cd_modo_editar else R.string.cd_modo_mover,
                    ),
                    alPulsar = { vm.alternarModo() },
                )
                Spacer(Modifier.weight(1f))
                IconoToggle(
                    icono = Icons.Rounded.AddPhotoAlternate,
                    descripcion = stringResource(R.string.cd_calco_elegir),
                    seleccionado = false,
                    alPulsar = alElegirImagen,
                )
                IconoToggle(
                    icono = if (estado.calcoVisible) {
                        Icons.Rounded.Visibility
                    } else {
                        Icons.Rounded.VisibilityOff
                    },
                    descripcion = stringResource(R.string.cd_calco_alternar),
                    seleccionado = estado.calcoVisible,
                    habilitado = estado.calcoUri != null,
                    alPulsar = alAlternarCalco,
                )
                IconoToggle(
                    icono = Icons.Rounded.Layers,
                    descripcion = stringResource(R.string.cd_calco_opciones),
                    seleccionado = false,
                    alPulsar = alAbrirCalco,
                )
                IconoToggle(
                    icono = Icons.Rounded.OpenInFull,
                    descripcion = stringResource(R.string.cd_expandir),
                    seleccionado = false,
                    alPulsar = alAbrirExpandir,
                )
            }

            // Fila 2: herramientas (iconos, sin scroll) + grosor.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Herramientas(estado, vm)
                Spacer(Modifier.weight(1f))
                BotonIcono(
                    icono = Icons.Rounded.Remove,
                    descripcion = stringResource(R.string.cd_grosor_menos),
                    alPulsar = { vm.cambiarGrosor(-1) },
                )
                Text(
                    stringResource(R.string.estado_grosor, estado.lapiz.grosor),
                    style = MaterialTheme.typography.labelLarge,
                )
                BotonIcono(
                    icono = Icons.Rounded.Add,
                    descripcion = stringResource(R.string.cd_grosor_mas),
                    alPulsar = { vm.cambiarGrosor(1) },
                )
            }

            // Fila 3: paleta de colores.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Colores.PALETA.forEach { color ->
                    CabezaColor(
                        color = color,
                        seleccionado = estado.lapiz.color == color.argb,
                        onElegir = { vm.elegirColor(color.argb) },
                    )
                }
            }
        }
    }
}

/** Diálogo para expandir la hoja: elige cuántos px agregar de ancho y alto. */
@Composable
private fun DialogoExpandir(
    estado: DibujoEstado,
    alExpandir: (Int, Int) -> Unit,
    alCerrar: () -> Unit,
) {
    var extraAncho by remember { mutableStateOf(512f) }
    var extraAlto by remember { mutableStateOf(512f) }
    val maxAncho = (com.dibujo.core.Lienzo.MAX_LADO - estado.hojaAncho).coerceAtLeast(0)
    val maxAlto = (com.dibujo.core.Lienzo.MAX_LADO - estado.hojaAlto).coerceAtLeast(0)
    val ancho = extraAncho.roundToInt().coerceIn(0, maxAncho)
    val alto = extraAlto.roundToInt().coerceIn(0, maxAlto)

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.expandir_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.expandir_actual, estado.hojaAncho, estado.hojaAlto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.expandir_ancho, ancho),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = extraAncho.coerceIn(0f, maxAncho.toFloat()),
                    onValueChange = { extraAncho = it },
                    valueRange = 0f..maxAncho.toFloat().coerceAtLeast(1f),
                    steps = ((maxAncho / 256) - 1).coerceAtLeast(0),
                    enabled = maxAncho > 0,
                )
                Text(
                    stringResource(R.string.expandir_alto, alto),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = extraAlto.coerceIn(0f, maxAlto.toFloat()),
                    onValueChange = { extraAlto = it },
                    valueRange = 0f..maxAlto.toFloat().coerceAtLeast(1f),
                    steps = ((maxAlto / 256) - 1).coerceAtLeast(0),
                    enabled = maxAlto > 0,
                )
                if (maxAncho == 0 && maxAlto == 0) {
                    Text(
                        stringResource(R.string.expandir_maximo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alExpandir(ancho, alto) },
                enabled = ancho > 0 || alto > 0,
            ) {
                Text(stringResource(R.string.expandir_aplicar))
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

/** Diálogo de la imagen de calco: elegir, mostrar/ocultar, opacidad y quitar. */
@Composable
private fun DialogoCalco(
    estado: DibujoEstado,
    alElegirImagen: () -> Unit,
    alAlternar: () -> Unit,
    alCambiarAlfa: (Float) -> Unit,
    alQuitar: () -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.calco_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.calco_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (estado.calcoUri == null) R.string.calco_sin_imagen else R.string.calco_cargada,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.calco_opacidad,
                        (estado.calcoAlfa * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = estado.calcoAlfa,
                    onValueChange = alCambiarAlfa,
                    valueRange = 0.1f..1f,
                    enabled = estado.calcoUri != null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alElegirImagen) {
                Text(stringResource(R.string.calco_elegir))
            }
        },
        dismissButton = {
            Row {
                if (estado.calcoUri != null) {
                    TextButton(onClick = alAlternar) {
                        Text(
                            stringResource(
                                if (estado.calcoVisible) R.string.calco_ocultar else R.string.calco_mostrar,
                            ),
                        )
                    }
                    TextButton(onClick = alQuitar) {
                        Text(stringResource(R.string.calco_quitar))
                    }
                }
                TextButton(onClick = alCerrar) {
                    Text(stringResource(R.string.cerrar))
                }
            }
        },
    )
}

@Composable
private fun DialogoConfiguracion(
    estado: DibujoEstado,
    alElegirBackend: (BackendRender) -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.config_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.config_backend),
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(Modifier.selectableGroup()) {
                    BackendRender.entries.forEach { backend ->
                        OpcionBackend(
                            backend = backend,
                            seleccionado = estado.backendSeleccionado == backend,
                            habilitado = backend.disponible(estado.vulkanSoportado),
                            alElegir = { alElegirBackend(backend) },
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        R.string.config_backend_efectivo,
                        stringResource(estado.backendEfectivo.recursoNombre()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.config_opengl_version, estado.openglEsVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        when {
                            estado.gpuActivo -> R.string.config_render_gpu
                            estado.gpuFallo -> R.string.config_render_cpu_fallo
                            else -> R.string.config_render_cpu
                        },
                        estado.gpuDescripcion,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!estado.vulkanSoportado) {
                    Text(
                        stringResource(R.string.config_vulkan_no_soportado),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cerrar))
            }
        },
    )
}

@Composable
private fun OpcionBackend(
    backend: BackendRender,
    seleccionado: Boolean,
    habilitado: Boolean,
    alElegir: () -> Unit,
) {
    val etiqueta = stringResource(backend.recursoNombre())
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = seleccionado,
                enabled = habilitado,
                role = Role.RadioButton,
                onClick = alElegir,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = seleccionado,
            onClick = null,
            enabled = habilitado,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = if (habilitado) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun BackendRender.recursoNombre(): Int = when (this) {
    BackendRender.AUTOMATICO -> R.string.backend_automatico
    BackendRender.OPENGL -> R.string.backend_opengl
    BackendRender.VULKAN -> R.string.backend_vulkan
}

/**
 * Absorbe el toque dentro de la vista. La hoja ocupa toda la ventana (detrás de
 * las barras): sin esto, un toque sobre una zona vacía de una barra caería a la
 * capa de dibujo de atrás y dejaría una marca. Los hijos de la barra (botones,
 * chips, scroll) siguen funcionando: se recorren primero.
 */
private fun Modifier.absorberToque(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun BotonIcono(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    alPulsar: () -> Unit,
    habilitado: Boolean = true,
) {
    IconButton(
        onClick = alPulsar,
        enabled = habilitado,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = icono,
            contentDescription = descripcion,
        )
    }
}

@Composable
private fun FiltroChip(
    seleccionado: Boolean,
    etiqueta: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    alPulsar: () -> Unit,
) {
    FilterChip(
        selected = seleccionado,
        onClick = alPulsar,
        label = { Text(etiqueta) },
        leadingIcon = {
            Icon(icono, contentDescription = null, Modifier.size(18.dp))
        },
        modifier = Modifier.semantics { contentDescription = descripcion },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun CabezaColor(
    color: com.dibujo.core.Color,
    seleccionado: Boolean,
    onElegir: () -> Unit,
) {
    val descripcion = stringResource(R.string.cd_color, color.nombre)
    Box(
        Modifier
            .size(34.dp)
            .background(Color(color.argb), CircleShape)
            .border(
                width = if (seleccionado) 3.dp else 1.dp,
                color = if (seleccionado) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = onElegir)
            .semantics { contentDescription = descripcion },
    )
}

@Composable
private fun Herramientas(
    estado: DibujoEstado,
    vm: DibujoViewModel,
) {
    // Botones de icono compactos: entran todos en una fila, sin scroll. El
    // DOBLE va al final, con el mismo tamaño reducido para que quepan seis.
    val pares = listOf(
        Triple(Herramienta.LAPIZ, R.string.herramienta_lapiz, Icons.Rounded.Draw),
        Triple(Herramienta.PORTAMINA, R.string.herramienta_portamina, Icons.Rounded.Mode),
        Triple(Herramienta.CARBONCILLO, R.string.herramienta_carboncillo, Icons.Rounded.Brush),
        Triple(Herramienta.BORRADOR, R.string.herramienta_borrador, Icons.Rounded.CleaningServices),
        Triple(Herramienta.PINTAR, R.string.herramienta_pintar, Icons.Rounded.FormatColorFill),
        Triple(Herramienta.DOBLE, R.string.herramienta_doble, Icons.Rounded.ContentCopy),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pares.forEach { (herramienta, etiquetaRes, icono) ->
            val etiqueta = stringResource(etiquetaRes)
            IconoToggle(
                icono = icono,
                descripcion = etiqueta,
                seleccionado = estado.lapiz.herramienta == herramienta,
                tamano = 34.dp,
                alPulsar = { vm.elegirHerramienta(herramienta) },
            )
        }
    }
}

/**
 * Botón de icono compacto con estado seleccionado (fondo tonal). Se usa en la
 * barra inferior para que todas las herramientas entren en una fila sin scroll
 * horizontal, que era incómodo en pantallas angostas.
 */
@Composable
private fun IconoToggle(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    seleccionado: Boolean,
    alPulsar: () -> Unit,
    habilitado: Boolean = true,
    tamano: Dp = 40.dp,
) {
    Box(
        Modifier
            .size(tamano)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (seleccionado) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(enabled = habilitado, onClick = alPulsar)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (habilitado) {
                if (seleccionado) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
        )
    }
}
