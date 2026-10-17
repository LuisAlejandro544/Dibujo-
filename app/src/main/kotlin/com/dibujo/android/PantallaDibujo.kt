package com.dibujo.android

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Mode
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dibujo.core.Colores
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PantallaDibujo()
            }
        }
    }
}

/** Pantalla principal: barra superior (acciones) + hoja táctil + herramientas. */
@Composable
fun PantallaDibujo(vm: DibujoViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()

    Scaffold(
        topBar = { BarraSuperior(estado, vm) },
        bottomBar = { BarraHerramientas(estado, vm) },
    ) { margenes ->
        Hoja(estado, vm, Modifier.padding(margenes).fillMaxSize())
    }
}

/** Barra superior: título + estado del motor + deshacer/rehacer/centrar/limpiar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraSuperior(estado: DibujoEstado, vm: DibujoViewModel) {
    TopAppBar(
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
                alPulsar = { vm.deshacer() },
            )
            BotonIcono(
                icono = Icons.AutoMirrored.Rounded.Redo,
                descripcion = stringResource(R.string.cd_rehacer),
                habilitado = estado.puedeRehacer,
                alPulsar = { vm.rehacer() },
            )
            BotonIcono(
                icono = Icons.Rounded.CenterFocusWeak,
                descripcion = stringResource(R.string.cd_centrar),
                alPulsar = { vm.centrar() },
            )
            BotonIcono(
                icono = Icons.Rounded.Delete,
                descripcion = stringResource(R.string.cd_limpiar),
                alPulsar = { vm.limpiar() },
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun Hoja(estado: DibujoEstado, vm: DibujoViewModel, modifier: Modifier = Modifier) {
    var dimension by remember { mutableStateOf(IntSize.Zero) }

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

    val trazo = estado.trazoEnCurso
    val camara = estado.camara
    val lapiz = estado.lapiz

    // El borrador se previsualiza como lo rasteriza el motor: pinta con el
    // fondo de la hoja y usa su grosor efectivo (grosor + 4), así la marca
    // se ve "borrada" al instante mientras arrastrás el dedo.
    val borran = lapiz.herramienta == Herramienta.BORRADOR
    val colorTrazo = if (borran) Color(Colores.BLANCO) else Color(lapiz.color)
    val grosorTrazo = lapiz.grosorEfectivo()

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { tamano ->
                if (tamano != dimension) dimension = tamano
                vm.inicializar(tamano.width, tamano.height)
            }
            // EDICION: un dedo dibuja; MOVER: pan y pinch de la cámara.
            .pointerInput(estado.modo) {
                if (estado.modo == Modo.MOVER) {
                    detectTransformGestures { foco, pan, zoom, _ ->
                        if (zoom != 1f) vm.zoomear(zoom, foco.x, foco.y)
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
            },
    ) {
        val bmp = bitmap ?: return@Canvas
        val vw = size.width.toInt().coerceAtLeast(1)
        val vh = size.height.toInt().coerceAtLeast(1)
        vm.renderizar(buffer, vw, vh)
        bmp.setPixels(buffer, 0, vw, 0, 0, vw, vh)
        drawImage(bmp.asImageBitmap())

        // Vista previa del trazo en curso (el motor la rasteriza al soltar).
        val puntos = trazo
        if (puntos != null && puntos.size >= 2) {
            val path = Path()
            path.moveTo(
                puntos[0] * camara.zoom - camara.panX,
                puntos[1] * camara.zoom - camara.panY,
            )
            var i = 2
            while (i + 1 < puntos.size) {
                path.lineTo(
                    puntos[i] * camara.zoom - camara.panX,
                    puntos[i + 1] * camara.zoom - camara.panY,
                )
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

/** Barra inferior: herramientas + modo + grosor (fila 1) y paleta (fila 2). */
@Composable
private fun BarraHerramientas(estado: DibujoEstado, vm: DibujoViewModel) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            // Fila 1: modo, herramientas y grosor.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

                Herramientas(estado, vm)

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

            // Fila 2: paleta de colores.
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
    val pares = listOf(
        Triple(Herramienta.LAPIZ, R.string.herramienta_lapiz, Icons.Rounded.Draw),
        Triple(Herramienta.PORTAMINA, R.string.herramienta_portamina, Icons.Rounded.Mode),
        Triple(Herramienta.CARBONCILLO, R.string.herramienta_carboncillo, Icons.Rounded.Brush),
        Triple(Herramienta.BORRADOR, R.string.herramienta_borrador, Icons.Rounded.CleaningServices),
    )
    pares.forEach { (herramienta, etiquetaRes, icono) ->
        FiltroChip(
            seleccionado = estado.lapiz.herramienta == herramienta,
            etiqueta = stringResource(etiquetaRes),
            icono = icono,
            descripcion = stringResource(etiquetaRes),
            alPulsar = { vm.elegirHerramienta(herramienta) },
        )
    }
}
