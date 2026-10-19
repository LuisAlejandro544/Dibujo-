package com.dibujo.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as ColorAndroid
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mode
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dibujo.core.BackendRender
import com.dibujo.core.Camara
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo
import com.dibujo.core.Punta
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
                AppDibujo()
            }
        }
    }
}

/**
 * Paleta clara de la app: violeta suave sobre fondos casi blancos. Da un aire
 * profesional y mantiene el contraste del lienzo (que va detrás de la ventana).
 */
private val EsquemaDibujo = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

/** Esquinas redondeadas coherentes en toda la app. */
private val FormasDibujo = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
        MaterialTheme(
            colorScheme = EsquemaDibujo,
            shapes = FormasDibujo,
            content = content,
        )
    }
}

/** Pantalla principal: barra superior (acciones) + hoja táctil + herramientas. */
@Composable
fun PantallaDibujo(
    vm: DibujoViewModel = viewModel(),
    alVolver: () -> Unit = {},
    alAbrirOpciones: () -> Unit = {},
) {
    val estadoState = vm.estado.collectAsState()
    val estado = estadoState.value
    var mostrarExpandir by remember { mutableStateOf(false) }
    var mostrarCalco by remember { mutableStateOf(false) }
    var mostrarIa by remember { mutableStateOf(false) }
    // Recortar/redimensionar el lienzo (diálogo desde "más opciones").
    var mostrarAjustarLienzo by remember { mutableStateOf(false) }
    // Limpiar la hoja borra todo y es irreversible si no se deshace: se pide
    // confirmación (ahora vive dentro del menú de la barra superior).
    var mostrarLimpiar by remember { mutableStateOf(false) }
    // Transformar el dibujo (voltear/mover/escalar) y la rueda HSV.
    var mostrarTransformar by remember { mutableStateOf(false) }
    var mostrarColor by remember { mutableStateOf(false) }
    // Acciones secundarias de la hoja (calco, IA, expandir): viven en una hoja
    // inferior para no amontonar la barra de dibujo.
    var mostrarMasHerramientas by remember { mutableStateOf(false) }
    // Textura del trazo y herramienta de texto (tipografías del sistema).
    var mostrarTextura by remember { mutableStateOf(false) }
    var mostrarTexto by remember { mutableStateOf(false) }

    // Volver al menú principal (botón atrás del sistema o de la barra).
    BackHandler(onBack = alVolver)

    // Autoguardado al salir de la app (pantalla de fondo): así "Continuar
    // dibujando" retoma el borrador tal como quedó, aunque Android mate el proceso.
    val duenoCiclo = LocalLifecycleOwner.current
    DisposableEffect(duenoCiclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) vm.autoguardarActual()
        }
        duenoCiclo.lifecycle.addObserver(observador)
        onDispose {
            duenoCiclo.lifecycle.removeObserver(observador)
            // Al salir del lienzo (al menú o a opciones) también se autoguarda:
            // la navegación interna no dispara ON_STOP.
            vm.autoguardarActual()
        }
    }

    // Selector de imagen del sistema (Photo Picker): no requiere permisos.
    val elegirImagen = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> vm.elegirCalco(uri?.toString()) }

    // --- Exportar el dibujo a la galería ---------------------------------
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var exportando by remember { mutableStateOf(false) }
    // El diálogo de exportación se abre al pedirlo y arma el PNG en el momento
    // con el ajuste de recorte elegido (así el interruptor cambia el resultado).
    var mostrarExportar by remember { mutableStateOf(false) }
    // "Inteligente": recorta el fondo sin usar y guarda sólo lo dibujado.
    var recortarExport by remember { mutableStateOf(true) }
    // PNG ya codificado: mientras no sea null el diálogo muestra el peso y
    // habilita guardar o compartir.
    var imagenLista by remember { mutableStateOf<ExportadorGaleria.Imagen?>(null) }
    // Los textos se leen en composición (no con `context.getString` dentro de
    // la corrutina) para que respeten cambios de configuración.
    val textoExportadoOk = stringResource(R.string.exportado_ok)
    val textoExportadoError = stringResource(R.string.exportado_error)
    val textoExportadoSinPermiso = stringResource(R.string.exportado_sin_permiso)
    val textoCompartirError = stringResource(R.string.compartir_error)
    val textoGuardadoOk = stringResource(R.string.guardado_ok)
    val textoGuardadoError = stringResource(R.string.guardado_error)

    // Copia la hoja a un Bitmap, la codifica a PNG y la deja lista para
    // guardar/compartir. Si [recortar] recorta al contenido dibujado (el fondo
    // sin usar desaparece), sin tocar el dibujo del usuario. El copiado y la
    // compresión van fuera del hilo principal (hojas grandes = varios MB).
    fun prepararExportacion(recortar: Boolean) {
        if (exportando) return
        val ancho = estadoState.value.hojaAncho
        val alto = estadoState.value.hojaAlto
        if (ancho <= 0 || alto <= 0) return
        exportando = true
        imagenLista = null
        alcance.launch {
            val bytes = withContext(Dispatchers.Default) {
                runCatching {
                    val pixeles = IntArray(ancho * alto)
                    if (!vm.copiarHoja(pixeles)) return@runCatching null
                    val (bw, bh, bp) = if (recortar) {
                        recortarAlContenido(pixeles, ancho, alto, Colores.BLANCO)
                            ?: Triple(ancho, alto, pixeles)
                    } else {
                        Triple(ancho, alto, pixeles)
                    }
                    val bitmap = Bitmap.createBitmap(bp, 0, bw, bw, bh, Bitmap.Config.ARGB_8888)
                    val png = ExportadorGaleria.codificarPng(bitmap)
                    bitmap.recycle()
                    png
                }.getOrNull()
            }
            exportando = false
            if (bytes == null) {
                Toast.makeText(contexto, textoExportadoError, Toast.LENGTH_SHORT).show()
                mostrarExportar = false
            } else {
                val marca = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                imagenLista = ExportadorGaleria.Imagen(bytes, "dibujo_$marca")
            }
        }
    }

    // Guarda el PNG ya preparado en la galería (solo se llama tras el diálogo).
    fun guardarExportacion() {
        val imagen = imagenLista ?: return
        alcance.launch {
            val guardado = withContext(Dispatchers.IO) {
                ExportadorGaleria.guardarBytes(contexto, imagen.bytes, imagen.nombre)
            }
            imagenLista = null
            mostrarExportar = false
            Toast.makeText(
                contexto,
                if (guardado) textoExportadoOk else textoExportadoError,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Abre el selector del sistema para compartir el PNG en cualquier app.
    fun compartirExportacion() {
        val imagen = imagenLista ?: return
        val ok = ExportadorGaleria.compartir(contexto, imagen.bytes, imagen.nombre)
        imagenLista = null
        mostrarExportar = false
        if (!ok) Toast.makeText(contexto, textoCompartirError, Toast.LENGTH_SHORT).show()
    }

    // En API < 29 hace falta WRITE_EXTERNAL_STORAGE; desde Android 10 (Q) el
    // guardado por MediaStore no pide permisos. Compartir no usa permisos.
    val permisoEscritura = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) {
            guardarExportacion()
        } else {
            Toast.makeText(
                contexto,
                textoExportadoSinPermiso,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val alExportar = { mostrarExportar = true }

    // Al abrir el diálogo o cambiar el interruptor de recorte se (re)arma el
    // PNG con ese ajuste. Se hace acá y no en el clic para que el interruptor
    // cambie el resultado antes de guardar/compartir.
    LaunchedEffect(mostrarExportar, recortarExport) {
        if (mostrarExportar && imagenLista == null && !exportando) {
            prepararExportacion(recortarExport)
        }
    }

    val alGuardarEnGaleria = {
        val faltaPermiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            contexto.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (faltaPermiso) {
            permisoEscritura.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            guardarExportacion()
        }
    }

    // Copia el dibujo en la biblioteca de la app (filesDir/dibujos): queda
    // disponible en "Cargar dibujo". El copiado y la compresión van fuera del
    // hilo principal.
    fun guardarEnApp() {
        val ancho = estadoState.value.hojaAncho
        val alto = estadoState.value.hojaAlto
        if (ancho <= 0 || alto <= 0) return
        alcance.launch {
            val pixeles = IntArray(ancho * alto)
            val leida = withContext(Dispatchers.Default) { vm.copiarHoja(pixeles) }
            val guardado = leida && withContext(Dispatchers.IO) {
                AlmacenDibujos.guardarCopia(contexto, ancho, alto, pixeles) != null
            }
            if (guardado) {
                vm.marcarGuardado()
                Toast.makeText(contexto, textoGuardadoOk, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(contexto, textoGuardadoError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                estado = estado,
                alVolver = alVolver,
                alDeshacer = vm::deshacer,
                alRehacer = vm::rehacer,
                alCentrar = vm::centrar,
                alEnderezar = vm::enderezar,
                alLimpiar = { mostrarLimpiar = true },
                alGuardar = { guardarEnApp() },
                alExportar = alExportar,
                alImportarImagen = {
                    elegirImagen.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                alAjustarLienzo = { mostrarAjustarLienzo = true },
                alTransformar = { mostrarTransformar = true },
                alAlternarAsistencia = vm::alternarAsistenciaRecta,
                exportando = exportando,
                alAbrirConfig = alAbrirOpciones,
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
                alTerminarCalco = vm::terminarEdicionCalco,
                alTerminarAjuste = vm::terminarAjusteDibujo,
                alCancelarAjuste = vm::cancelarAjusteDibujo,
                alAbrirIa = {
                    mostrarIa = true
                    if (estado.calcoUri != null && estado.coloresCalco.isEmpty()) {
                        vm.analizarCalco()
                    }
                },
                alAbrirColor = { mostrarColor = true },
                alAbrirMasHerramientas = { mostrarMasHerramientas = true },
                alAbrirTextura = { mostrarTextura = true },
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
            alAlternarEdicion = {
                vm.alternarEdicionCalco()
                // Se cierra para poder mover/escalar la imagen con los gestos.
                mostrarCalco = false
            },
            alCentrar = vm::centrarCalco,
            alQuitar = { vm.elegirCalco(null) },
            alCerrar = { mostrarCalco = false },
        )
    }

    if (mostrarIa) {
        DialogoIa(
            estado = estado,
            alLimpiarTrazos = vm::limpiarTrazos,
            alLimpiarTrazosIa = vm::limpiarTrazosConModelo,
            alAnalizar = vm::analizarCalco,
            alElegir = vm::usarColorCalco,
            alCerrar = { mostrarIa = false },
        )
    }

    if (mostrarAjustarLienzo) {
        DialogoAjustarLienzo(
            estado = estado,
            alRecortar = {
                vm.recortarAlDibujo()
                mostrarAjustarLienzo = false
            },
            alRedimensionar = { ancho, alto ->
                vm.redimensionarLienzo(ancho, alto)
                mostrarAjustarLienzo = false
            },
            alCerrar = { mostrarAjustarLienzo = false },
        )
    }

    if (mostrarLimpiar) {
        DialogoLimpiar(
            alConfirmar = {
                vm.limpiar()
                mostrarLimpiar = false
            },
            alCerrar = { mostrarLimpiar = false },
        )
    }

    if (mostrarExportar) {
        DialogoExportar(
            peso = imagenLista?.let { ExportadorGaleria.formatearTamano(it.bytes.size) },
            recortar = recortarExport,
            calculando = exportando,
            alCambiarRecorte = {
                // Al cambiar el recorte se descarta el PNG anterior para que el
                // `LaunchedEffect` lo vuelva a armar con el ajuste nuevo.
                recortarExport = it
                imagenLista = null
            },
            alGuardar = alGuardarEnGaleria,
            alCompartir = { compartirExportacion() },
            alCerrar = {
                mostrarExportar = false
                imagenLista = null
            },
        )
    }

    if (mostrarTransformar) {
        DialogoTransformar(
            ocupado = estado.ajustandoLienzo,
            alVoltearHorizontal = {
                vm.transformarDibujo(true, false, 0f, 0f, 1f)
            },
            alVoltearVertical = {
                vm.transformarDibujo(false, true, 0f, 0f, 1f)
            },
            alAjustarLibre = {
                vm.iniciarAjusteDibujo()
                mostrarTransformar = false
            },
            alEscalar = { escala ->
                vm.transformarDibujo(false, false, 0f, 0f, escala)
                mostrarTransformar = false
            },
            alCerrar = { mostrarTransformar = false },
        )
    }

    if (mostrarColor) {
        DialogoColor(
            colorInicial = estado.lapiz.color,
            recientes = estado.coloresRecientes,
            alPrevisualizar = vm::previsualizarColorHsv,
            alConfirmar = { vm.confirmarColorHsv(it) },
            alCerrar = { mostrarColor = false },
        )
    }

    if (mostrarMasHerramientas) {
        HojaHerramientasHoja(
            estado = estado,
            alCerrar = { mostrarMasHerramientas = false },
            alElegirImagen = {
                mostrarMasHerramientas = false
                elegirImagen.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            alAlternarCalco = vm::alternarCalco,
            alAbrirCalco = {
                mostrarMasHerramientas = false
                mostrarCalco = true
            },
            alAbrirIa = {
                mostrarMasHerramientas = false
                mostrarIa = true
                if (estado.calcoUri != null && estado.coloresCalco.isEmpty()) {
                    vm.analizarCalco()
                }
            },
            alAbrirExpandir = {
                mostrarMasHerramientas = false
                mostrarExpandir = true
            },
            alAbrirTexto = {
                mostrarMasHerramientas = false
                mostrarTexto = true
            },
        )
    }

    if (mostrarTextura) {
        DialogoTextura(
            actual = estado.lapiz.textura,
            alElegir = { vm.elegirTextura(it) },
            alCerrar = { mostrarTextura = false },
        )
    }

    if (mostrarTexto) {
        DialogoTexto(
            alInsertar = { texto, tipografia, tamano ->
                vm.insertarTexto(texto, tipografia, tamano)
                mostrarTexto = false
            },
            alCerrar = { mostrarTexto = false },
        )
    }
}

/**
 * Barra superior sobria: volver, título, deshacer/rehacer y centrar a la
 * vista; todo lo demás (enderezar, trazo asistido, guardar, importar, ajustar
 * lienzo, transformar, exportar, limpiar y configuración) vive en el menú de
 * "más opciones". Antes había cinco iconos sueltos y el título quedaba
 * aplastado ("Dib…", con el motor en una segunda línea). El título se acota a
 * una línea con elipsis por si igual falta espacio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraSuperior(
    estado: DibujoEstado,
    alVolver: () -> Unit,
    alDeshacer: () -> Unit,
    alRehacer: () -> Unit,
    alCentrar: () -> Unit,
    alEnderezar: () -> Unit,
    alLimpiar: () -> Unit,
    alGuardar: () -> Unit,
    alExportar: () -> Unit,
    alImportarImagen: () -> Unit,
    alAjustarLienzo: () -> Unit,
    alTransformar: () -> Unit,
    alAlternarAsistencia: () -> Unit,
    exportando: Boolean,
    alAbrirConfig: () -> Unit,
) {
    var menuAbierto by remember { mutableStateOf(false) }
    TopAppBar(
        modifier = Modifier.absorberToque(),
        navigationIcon = {
            IconButton(onClick = alVolver) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_volver),
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.app_name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            Box {
                BotonIcono(
                    icono = Icons.Rounded.MoreVert,
                    descripcion = stringResource(R.string.cd_mas_opciones),
                    alPulsar = { menuAbierto = true },
                )
                DropdownMenu(
                    expanded = menuAbierto,
                    onDismissRequest = { menuAbierto = false },
                ) {
                    // Vista: enderezar y trazo asistido.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_enderezar)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = null)
                        },
                        enabled = estado.camara.rotacion != 0f,
                        onClick = {
                            menuAbierto = false
                            alEnderezar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_asistencia_recta)) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.TrendingFlat,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = if (estado.asistenciaRecta) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            menuAbierto = false
                            alAlternarAsistencia()
                        },
                    )
                    HorizontalDivider()
                    // Hoja: guardar, importar, tamaño y transformar.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.guardar_en_app)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                        },
                        onClick = {
                            menuAbierto = false
                            alGuardar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.importar_imagen)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        },
                        onClick = {
                            menuAbierto = false
                            alImportarImagen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lienzo_ajustar_titulo)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.AspectRatio, contentDescription = null)
                        },
                        onClick = {
                            menuAbierto = false
                            alAjustarLienzo()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.transformar_titulo)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Transform, contentDescription = null)
                        },
                        onClick = {
                            menuAbierto = false
                            alTransformar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_exportar)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        },
                        enabled = !exportando,
                        onClick = {
                            menuAbierto = false
                            alExportar()
                        },
                    )
                    HorizontalDivider()
                    // Acción destructiva y configuración, al final.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_limpiar)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuAbierto = false
                            alLimpiar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.config_titulo)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Settings, contentDescription = null)
                        },
                        onClick = {
                            menuAbierto = false
                            alAbrirConfig()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Confirmación antes de borrar toda la hoja (acción destructiva). */
@Composable
private fun DialogoLimpiar(
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.limpiar_titulo)) },
        text = { Text(stringResource(R.string.limpiar_confirmacion)) },
        confirmButton = {
            TextButton(onClick = alConfirmar) {
                Text(stringResource(R.string.limpiar_confirmar))
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

/**
 * Diálogo de exportación: muestra el peso del PNG y ofrece guardarlo en la
 * galería o compartirlo. Trae el interruptor "Recortar al dibujo": cuando está
 * activo, la imagen exportada elimina el fondo sin usar (sólo se guarda lo que
 * se dibujó), sin tocar el dibujo del usuario.
 */
@Composable
private fun DialogoExportar(
    peso: String?,
    recortar: Boolean,
    calculando: Boolean,
    alCambiarRecorte: (Boolean) -> Unit,
    alGuardar: () -> Unit,
    alCompartir: () -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.exportar_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.exportar_peso,
                        peso ?: stringResource(R.string.exportar_calculando),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.exportar_recortar),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(R.string.exportar_recortar_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = recortar, onCheckedChange = alCambiarRecorte)
                }
                if (calculando) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.exportar_calculando))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = alCompartir, enabled = peso != null) {
                    Text(stringResource(R.string.exportar_compartir))
                }
                TextButton(onClick = alGuardar, enabled = peso != null) {
                    Text(stringResource(R.string.exportar_guardar))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cancelar))
            }
        },
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
    // EDICION: un dedo dibuja; MOVER: pan y pinch de la cámara; y si se está
    // ajustando el calco, los gestos mueven/escalan la imagen (no la hoja).
    val modo = estadoState.value.modo
    val cuentagotas = estadoState.value.cuentagotasActivo
    val ajustandoDibujo = estadoState.value.ajustandoDibujo
    val editandoCalco = estadoState.value.calcoEnEdicion &&
        estadoState.value.calcoUri != null &&
        estadoState.value.calcoVisible
    Canvas(
        modifier.pointerInput(modo, editandoCalco, cuentagotas, ajustandoDibujo) {
            if (cuentagotas) {
                // Cuentagotas: un toque toma el color del píxel de la hoja.
                detectTapGestures { punto -> vm.recogerColor(punto.x, punto.y) }
            } else if (editandoCalco) {
                // Un dedo mueve el calco; dos dedos lo escalan (anclado al foco).
                detectTransformGestures(panZoomLock = true) { foco, pan, zoom, _ ->
                    if (zoom != 1f) vm.escalarCalco(zoom, foco.x, foco.y)
                    if (pan != Offset.Zero) vm.moverCalco(pan.x, pan.y)
                }
            } else if (ajustandoDibujo) {
                // Ajuste libre del dibujo: la vista hace de vista previa. Un
                // dedo mueve y dos escalan (sin rotación); al tocar "Listo" el
                // movimiento se hornea en la hoja de una sola vez.
                detectTransformGestures(panZoomLock = true) { foco, pan, zoom, _ ->
                    if (zoom != 1f) vm.zoomear(zoom, foco.x, foco.y)
                    if (pan != Offset.Zero) vm.panear(pan.x, pan.y)
                }
            } else if (modo == Modo.MOVER) {
                // Pan + pinch de zoom + rotación de dos dedos, todos anclados
                // al foco (el punto bajo los dedos). La rotación llega en
                // grados y el motor la usa en radianes.
                //
                // `panZoomLock = true`: la rotación sólo se activa si el gesto
                // EMPIEZA girando. Con el pellizco (sólo zoom/pan) la hoja no
                // se gira por el temblor natural de los dedos, que era el bug
                // "hago zoom y se gira".
                detectTransformGestures(panZoomLock = true) { foco, pan, zoom, rotacion ->
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
            .pointerInput(modo, editandoCalco, cuentagotas, ajustandoDibujo) {
                if (!cuentagotas && !editandoCalco && !ajustandoDibujo && modo == Modo.EDICION) {
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
            val redonda = lapiz.punta == Punta.REDONDA
            drawPath(
                path,
                color = colorTrazo,
                style = Stroke(
                    width = grosorTrazo * camara.zoom,
                    cap = if (redonda) StrokeCap.Round else StrokeCap.Butt,
                    join = if (redonda) StrokeJoin.Round else StrokeJoin.Miter,
                ),
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
 * proporción) dentro de la hoja y se centra sola; el usuario puede desplazarla
 * y escalarla con [DibujoEstado.calcoOffsetX]/[DibujoEstado.calcoOffsetY] y
 * [DibujoEstado.calcoEscala]. Sigue la cámara (pan/zoom), así se puede calcar
 * con el dibujo a la vista.
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
        // Ajuste natural (encaja la imagen en la hoja) por la escala del usuario.
        val escala = minOf(
            estado.hojaAncho.toFloat() / imagen.width,
            estado.hojaAlto.toFloat() / imagen.height,
        ) * estado.calcoEscala
        val anchoHoja = imagen.width * escala
        val altoHoja = imagen.height * escala
        // Centro de la hoja más el desplazamiento del calco (0 = auto-centrado).
        val centroX = estado.hojaAncho / 2f + estado.calcoOffsetX
        val centroY = estado.hojaAlto / 2f + estado.calcoOffsetY
        val xHoja = centroX - anchoHoja / 2f
        val yHoja = centroY - altoHoja / 2f
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
            // Marco guía mientras se ajusta: deja ver hasta dónde llega la
            // imagen cuando el fondo es claro y se confunde con la hoja.
            if (estado.calcoEnEdicion) {
                drawRect(
                    color = Color(0xFF1E88E5),
                    topLeft = Offset(xHoja, yHoja),
                    size = Size(anchoHoja, altoHoja),
                    style = Stroke(width = 2f / cam.zoom),
                )
            }
        }
    }
}

/**
 * Barra inferior agrupada en tres franjas: herramientas, color y ajustes del
 * pincel. Las acciones secundarias (calco, IA, expandir) se movieron a una hoja
 * inferior para dejar respirar la barra: el lienzo manda y cada grupo tiene un
 * propósito claro, como en las apps de dibujo táctiles.
 */
@Composable
private fun BarraHerramientas(
    estado: DibujoEstado,
    vm: DibujoViewModel,
    alAbrirExpandir: () -> Unit,
    alElegirImagen: () -> Unit,
    alAlternarCalco: () -> Unit,
    alAbrirCalco: () -> Unit,
    alTerminarCalco: () -> Unit,
    alTerminarAjuste: () -> Unit,
    alCancelarAjuste: () -> Unit,
    alAbrirIa: () -> Unit,
    alAbrirColor: () -> Unit,
    alAbrirMasHerramientas: () -> Unit,
    alAbrirTextura: () -> Unit,
) {
    Surface(
        modifier = Modifier.absorberToque(),
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Deja libre la barra de navegación (gestos o botones): sin
                // esto la paleta quedaba debajo y no se podía tocar.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Aviso mientras se ajusta el dibujo, con "Listo" y "Cancelar".
            if (estado.ajustandoDibujo) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.OpenWith,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ajuste_dibujo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = alCancelarAjuste,
                        enabled = !estado.ajustandoLienzo,
                    ) {
                        Text(stringResource(R.string.ajuste_cancelar))
                    }
                    TextButton(
                        onClick = alTerminarAjuste,
                        enabled = !estado.ajustandoLienzo,
                    ) {
                        Text(stringResource(R.string.ajuste_listo))
                    }
                }
            }
            // Aviso mientras se ajusta el calco, con el botón para terminar.
            if (estado.calcoEnEdicion) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.OpenWith,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.calco_ajuste_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = alTerminarCalco) {
                        Text(stringResource(R.string.calco_listo))
                    }
                }
            }
            // Franja 1: herramientas, en una tarjeta que agrupa la selección.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Herramientas(estado, vm)
                }
            }
            // Franja 2: color actual, paleta y cuentagotas.
            FilaColor(estado, vm, alAbrirColor)
            // Franja 3: modo de gesto, grosor, textura y más herramientas.
            FilaPincel(estado, vm, alAbrirMasHerramientas, alAbrirTextura)
        }
    }
}

/** Franja de color: color actual (abre la rueda HSV), paleta y cuentagotas. */
@Composable
private fun FilaColor(
    estado: DibujoEstado,
    vm: DibujoViewModel,
    alAbrirColor: () -> Unit,
) {
    val descColor = stringResource(R.string.cd_rueda_color)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Círculo del color actual: toca y se abre la rueda de color.
        Box(
            Modifier
                .size(36.dp)
                .background(Color(estado.lapiz.color), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = alAbrirColor)
                .semantics { contentDescription = descColor },
        )
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Recientes primero (paleta, cuentagotas y rueda HSV) y luego la
            // paleta fija, separadas por una línea.
            estado.coloresRecientes.forEach { argb ->
                CabezaColor(
                    color = com.dibujo.core.Color(
                        "#%06X".format(argb and 0xFFFFFF),
                        argb,
                    ),
                    seleccionado = estado.lapiz.color == argb,
                    onElegir = { vm.elegirColor(argb) },
                )
            }
            if (estado.coloresRecientes.isNotEmpty()) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
            Colores.PALETA.forEach { color ->
                CabezaColor(
                    color = color,
                    seleccionado = estado.lapiz.color == color.argb,
                    onElegir = { vm.elegirColor(color.argb) },
                )
            }
        }
        IconoToggle(
            icono = Icons.Rounded.Colorize,
            descripcion = stringResource(R.string.cd_cuentagotas),
            seleccionado = estado.cuentagotasActivo,
            alPulsar = { vm.alternarCuentagotas() },
        )
    }
}

/** Franja del pincel: modo de gesto, control de grosor y más herramientas. */
@Composable
private fun FilaPincel(
    estado: DibujoEstado,
    vm: DibujoViewModel,
    alAbrirMasHerramientas: () -> Unit,
    alAbrirTextura: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoToggle(
                    icono = Icons.Rounded.Remove,
                    descripcion = stringResource(R.string.cd_grosor_menos),
                    seleccionado = false,
                    alPulsar = { vm.cambiarGrosor(-1) },
                    tamano = 36.dp,
                )
                Text(
                    stringResource(R.string.estado_grosor, estado.lapiz.grosor),
                    style = MaterialTheme.typography.labelLarge,
                )
                IconoToggle(
                    icono = Icons.Rounded.Add,
                    descripcion = stringResource(R.string.cd_grosor_mas),
                    seleccionado = false,
                    alPulsar = { vm.cambiarGrosor(1) },
                    tamano = 36.dp,
                )
            }
        }
        IconoToggle(
            icono = Icons.Rounded.Texture,
            descripcion = stringResource(R.string.cd_textura),
            seleccionado = estado.lapiz.textura != com.dibujo.core.Textura.LISA,
            alPulsar = alAbrirTextura,
            tamano = 36.dp,
        )
        IconoToggle(
            icono = Icons.Rounded.MoreHoriz,
            descripcion = stringResource(R.string.cd_mas_herramientas),
            seleccionado = false,
            alPulsar = alAbrirMasHerramientas,
        )
    }
}

/** Texturas del trazo: lisa, grano, rugosa, trama o salpicada. */
@Composable
private fun DialogoTextura(
    actual: com.dibujo.core.Textura,
    alElegir: (com.dibujo.core.Textura) -> Unit,
    alCerrar: () -> Unit,
) {
    val opciones = listOf(
        com.dibujo.core.Textura.LISA to R.string.textura_lisa,
        com.dibujo.core.Textura.GRANO to R.string.textura_grano,
        com.dibujo.core.Textura.RUGOSA to R.string.textura_rugosa,
        com.dibujo.core.Textura.TRAMA to R.string.textura_trama,
        com.dibujo.core.Textura.SALPICADA to R.string.textura_salpicada,
    )
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.textura_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.textura_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    opciones.forEach { (textura, etiquetaRes) ->
                        FilterChip(
                            selected = actual == textura,
                            onClick = { alElegir(textura) },
                            label = { Text(stringResource(etiquetaRes)) },
                        )
                    }
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

/** Inserta texto en la vista actual con una de las cinco tipografías del sistema. */
@Composable
private fun DialogoTexto(
    alInsertar: (String, Tipografia, Float) -> Unit,
    alCerrar: () -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    var tipografia by remember { mutableStateOf(Tipografia.SANS) }
    var tamano by remember { mutableStateOf(64f) }
    val fuentes = listOf(
        Tipografia.SANS to R.string.fuente_sans,
        Tipografia.SERIF to R.string.fuente_serif,
        Tipografia.MONO to R.string.fuente_mono,
        Tipografia.CURSIVA to R.string.fuente_cursiva,
        Tipografia.CASUAL to R.string.fuente_casual,
    )
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.texto_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.texto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text(stringResource(R.string.texto_campo)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.texto_tipografia),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fuentes.forEach { (fuente, etiquetaRes) ->
                        FilterChip(
                            selected = tipografia == fuente,
                            onClick = { tipografia = fuente },
                            label = { Text(stringResource(etiquetaRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.texto_tamano, tamano.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = tamano,
                    onValueChange = { tamano = it },
                    valueRange = 16f..256f,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alInsertar(texto, tipografia, tamano) },
                enabled = texto.isNotBlank(),
            ) {
                Text(stringResource(R.string.texto_insertar))
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

/**
 * Hoja inferior con las acciones secundarias sobre la hoja: elegir/ver/ajustar
 * la imagen de calco, las herramientas con IA local y expandir el lienzo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaHerramientasHoja(
    estado: DibujoEstado,
    alCerrar: () -> Unit,
    alElegirImagen: () -> Unit,
    alAlternarCalco: () -> Unit,
    alAbrirCalco: () -> Unit,
    alAbrirIa: () -> Unit,
    alAbrirExpandir: () -> Unit,
    alAbrirTexto: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = alCerrar) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.hoja_acciones_titulo),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            AccionHoja(
                icono = Icons.Rounded.AddPhotoAlternate,
                etiqueta = stringResource(R.string.cd_calco_elegir),
                alPulsar = alElegirImagen,
            )
            AccionHoja(
                icono = if (estado.calcoVisible) {
                    Icons.Rounded.Visibility
                } else {
                    Icons.Rounded.VisibilityOff
                },
                etiqueta = stringResource(R.string.cd_calco_alternar),
                habilitado = estado.calcoUri != null,
                alPulsar = alAlternarCalco,
            )
            AccionHoja(
                icono = Icons.Rounded.Layers,
                etiqueta = stringResource(R.string.cd_calco_opciones),
                habilitado = estado.calcoUri != null,
                alPulsar = alAbrirCalco,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            AccionHoja(
                icono = Icons.Rounded.TextFields,
                etiqueta = stringResource(R.string.cd_texto),
                alPulsar = alAbrirTexto,
            )
            AccionHoja(
                icono = Icons.Rounded.AutoFixHigh,
                etiqueta = stringResource(R.string.cd_ia_local),
                alPulsar = alAbrirIa,
            )
            AccionHoja(
                icono = Icons.Rounded.OpenInFull,
                etiqueta = stringResource(R.string.cd_expandir),
                alPulsar = alAbrirExpandir,
            )
        }
    }
}

/** Fila de una acción secundaria dentro de la hoja inferior. */
@Composable
private fun AccionHoja(
    icono: ImageVector,
    etiqueta: String,
    alPulsar: () -> Unit,
    habilitado: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = habilitado, onClick = alPulsar)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .semantics { contentDescription = etiqueta },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = if (habilitado) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyLarge,
            color = if (habilitado) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
        )
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

/**
 * Diálogo para recortar o redimensionar el lienzo. "Recortar al dibujo" deja
 * sólo el recuadro con lo dibujado (descarta los márgenes en blanco sin perder
 * trazos). Los sliders redimensionan la hoja centrando el dibujo. Al cambiar el
 * tamaño, el historial arranca de nuevo (como al expandir).
 */
@Composable
private fun DialogoAjustarLienzo(
    estado: DibujoEstado,
    alRecortar: () -> Unit,
    alRedimensionar: (Int, Int) -> Unit,
    alCerrar: () -> Unit,
) {
    val maxLado = com.dibujo.core.Lienzo.MAX_LADO
    var ancho by remember { mutableStateOf(estado.hojaAncho.toFloat()) }
    var alto by remember { mutableStateOf(estado.hojaAlto.toFloat()) }
    val na = ancho.roundToInt().coerceIn(1, maxLado)
    val nb = alto.roundToInt().coerceIn(1, maxLado)

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.lienzo_ajustar_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.lienzo_ajustar_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lienzo_actual, estado.hojaAncho, estado.hojaAlto),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = alRecortar,
                    enabled = !estado.ajustandoLienzo,
                ) {
                    Text(stringResource(R.string.lienzo_recortar))
                }
                Text(
                    stringResource(R.string.lienzo_recortar_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lienzo_ancho, na),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = ancho.coerceIn(1f, maxLado.toFloat()),
                    onValueChange = { ancho = it },
                    valueRange = 1f..maxLado.toFloat(),
                )
                Text(
                    stringResource(R.string.lienzo_alto, nb),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = alto.coerceIn(1f, maxLado.toFloat()),
                    onValueChange = { alto = it },
                    valueRange = 1f..maxLado.toFloat(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alRedimensionar(na, nb) },
                enabled = !estado.ajustandoLienzo,
            ) {
                Text(stringResource(R.string.lienzo_aplicar))
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cancelar))
            }
        },
    )
}

/**
 * Diálogo "Transformar dibujo": voltea el dibujo (horizontal/vertical) y abre
 * el **ajuste libre** (un dedo mueve, dos dedos escalan, como el calco). El
 * tamaño también se puede fijar con el control deslizante. Cada acción queda en
 * el historial (se puede deshacer).
 */
@Composable
private fun DialogoTransformar(
    ocupado: Boolean,
    alVoltearHorizontal: () -> Unit,
    alVoltearVertical: () -> Unit,
    alAjustarLibre: () -> Unit,
    alEscalar: (Float) -> Unit,
    alCerrar: () -> Unit,
) {
    var escala by remember { mutableStateOf(1f) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.transformar_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.transformar_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = alVoltearHorizontal, enabled = !ocupado) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.transformar_voltear_h))
                    }
                    TextButton(onClick = alVoltearVertical, enabled = !ocupado) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.transformar_voltear_v))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.transformar_mover),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.transformar_libre_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = alAjustarLibre,
                    enabled = !ocupado,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.OpenWith, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.transformar_libre))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.transformar_escala, (escala * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = escala,
                    onValueChange = { escala = it },
                    valueRange = 0.25f..2f,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { alEscalar(escala) }, enabled = !ocupado) {
                Text(stringResource(R.string.transformar_aplicar))
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text(stringResource(R.string.cerrar))
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
    alAlternarEdicion: () -> Unit,
    alCentrar: () -> Unit,
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
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.calco_ajustar),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(R.string.calco_ajustar_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = estado.calcoEnEdicion,
                        onCheckedChange = { alAlternarEdicion() },
                        enabled = estado.calcoUri != null,
                    )
                }
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
                    TextButton(onClick = alCentrar) {
                        Text(stringResource(R.string.calco_centrar))
                    }
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

/**
 * Diálogo "IA local": reúne las herramientas que corren en el equipo:
 *  - Limpiar trazos: adelgaza los trazos repasados a una línea pareja. Ofrece
 *    el modelo chico (LiteRT, si está empaquetado) o el adelgazado algorítmico
 *    (Zhang-Suen), que también es el respaldo del modelo.
 *  - Paleta del calco: saca los colores dominantes de la imagen que calcas
 *    (k-medias) para terminar de colorear; al tocar un color se activa PINTAR.
 */
@Composable
private fun DialogoIa(
    estado: DibujoEstado,
    alLimpiarTrazos: () -> Unit,
    alLimpiarTrazosIa: () -> Unit,
    alAnalizar: () -> Unit,
    alElegir: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.ia_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.ia_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.ia_limpiar_titulo),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.ia_limpiar_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (estado.limpiando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ia_limpiando))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (estado.modeloIaDisponible) {
                            Button(onClick = alLimpiarTrazosIa) {
                                Text(stringResource(R.string.ia_limpiar_modelo_boton))
                            }
                        }
                        TextButton(onClick = alLimpiarTrazos) {
                            Text(stringResource(R.string.ia_limpiar_boton))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.paleta_calco_titulo),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.paleta_calco_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    estado.calcoUri == null -> Text(
                        stringResource(R.string.paleta_calco_sin_calco),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    estado.analizandoCalco -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.paleta_calco_analizando))
                    }
                    estado.coloresCalco.isEmpty() -> TextButton(onClick = alAnalizar) {
                        Text(stringResource(R.string.paleta_calco_analizar))
                    }
                    else -> {
                        Text(
                            stringResource(R.string.paleta_calco_toca),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            estado.coloresCalco.forEach { argb ->
                                CabezaColor(
                                    color = com.dibujo.core.Color(
                                        com.dibujo.core.Colores.PALETA
                                            .firstOrNull { it.argb == argb }?.nombre
                                            ?: "#%06X".format(argb and 0xFFFFFF),
                                        argb,
                                    ),
                                    seleccionado = estado.lapiz.color == argb,
                                    onElegir = { alElegir(argb) },
                                )
                            }
                        }
                    }
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
    seleccionado: Boolean = false,
) {
    IconButton(
        onClick = alPulsar,
        enabled = habilitado,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (seleccionado) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (seleccionado) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
    // Cada herramienta lleva su nombre debajo del icono: sin etiqueta, el
    // borrador y sobre todo "Doble" (antes un icono de "copiar") no se
    // entendían. Cada botón ocupa el mismo ancho (weight) y el seleccionado se
    // resalta con un fondo tonal, así la fila se lee como un segmentado.
    val pares = listOf(
        Triple(Herramienta.LAPIZ, R.string.herramienta_lapiz, Icons.Rounded.Draw),
        Triple(Herramienta.PORTAMINA, R.string.herramienta_portamina, Icons.Rounded.Mode),
        Triple(Herramienta.CARBONCILLO, R.string.herramienta_carboncillo, Icons.Rounded.Brush),
        Triple(Herramienta.BORRADOR, R.string.herramienta_borrador, IconoBorrador),
        Triple(Herramienta.PINTAR, R.string.herramienta_pintar, Icons.Rounded.FormatColorFill),
        Triple(Herramienta.DOBLE, R.string.herramienta_doble, IconoTrazoDoble),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        pares.forEach { (herramienta, etiquetaRes, icono) ->
            val etiqueta = stringResource(etiquetaRes)
            val seleccionado = estado.lapiz.herramienta == herramienta
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (seleccionado) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(onClick = { vm.elegirHerramienta(herramienta) })
                    .semantics { contentDescription = etiqueta }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (seleccionado) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (seleccionado) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
