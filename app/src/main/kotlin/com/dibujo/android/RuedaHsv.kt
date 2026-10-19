package com.dibujo.android

import android.graphics.Color as ColorAndroid
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Diálogo de color con **rueda HSV**, vista previa en vivo, muestra en hex y
 * colores recientes. El tono (H) sale del ángulo de la rueda y la saturación
 * (S) del radio; el brillo (V) va en un deslizador. Mientras se arrastra, el
 * color se aplica al lápiz como vista previa ([alPrevisualizar]); recién al
 * tocar "Listo" se confirma y queda en los recientes ([alConfirmar]).
 */
@Composable
fun DialogoColor(
    colorInicial: Int,
    recientes: List<Int>,
    alPrevisualizar: (Int) -> Unit,
    alConfirmar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    // El diálogo se compone de cero cada vez que se abre, así que el estado se
    // inicializa una sola vez con el color actual (no se re-inicializa al
    // previsualizar, que también cambia el color del lápiz).
    val hsv = remember {
        FloatArray(3).also { ColorAndroid.colorToHSV(colorInicial, it) }
    }
    var tono by remember { mutableStateOf(hsv[0]) }
    var saturacion by remember { mutableStateOf(hsv[1]) }
    var valor by remember { mutableStateOf(hsv[2]) }
    val colorActual = remember(tono, saturacion, valor) {
        ColorAndroid.HSVToColor(floatArrayOf(tono, saturacion, valor))
    }
    val cdRueda = stringResource(R.string.cd_rueda_color)
    // Vista previa en vivo sobre el lápiz sin registrar el color todavía.
    LaunchedEffect(colorActual) { alPrevisualizar(colorActual) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.color_titulo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.color_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                RuedaHsv(
                    tono = tono,
                    saturacion = saturacion,
                    alCambiar = { t, s ->
                        tono = t
                        saturacion = s
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .semantics { contentDescription = cdRueda },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.color_brillo, (valor * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = valor,
                    onValueChange = { valor = it },
                    valueRange = 0f..1f,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(colorActual))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "#%06X".format(colorActual and 0xFFFFFF),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (recientes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.color_recientes),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recientes.take(8).forEach { argb ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable {
                                        ColorAndroid.colorToHSV(argb, hsv)
                                        tono = hsv[0]
                                        saturacion = hsv[1]
                                        valor = hsv[2]
                                    },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { alConfirmar(colorActual) }) {
                Text(stringResource(R.string.color_listo))
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
 * Rueda HSV: todos los tonos en el anillo (ángulo) y la saturación por radio
 * (centro = blanco). La rueda se dibuja siempre a brillo pleno: el brillo (V)
 * sólo afecta al color elegido (la muestra y el hex), así nunca se ve negra y
 * siempre se puede ver de dónde sacar el tono y la saturación. Un toque o
 * arrastre sobre la rueda fija [tono] y [saturacion].
 */
@Composable
private fun RuedaHsv(
    tono: Float,
    saturacion: Float,
    alCambiar: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .pointerInput(alCambiar) {
                detectTapGestures { punto ->
                    val (t, s) = puntoARueda(punto, size.width, size.height)
                    alCambiar(t, s)
                }
            }
            .pointerInput(alCambiar) {
                detectDragGestures { cambio, _ ->
                    val (t, s) = puntoARueda(cambio.position, size.width, size.height)
                    alCambiar(t, s)
                    cambio.consume()
                }
            },
    ) {
        val radio = size.minDimension / 2f
        val centro = Offset(size.width / 2f, size.height / 2f)
        if (radio <= 1f) return@Canvas
        // Anillo de tonos: muchos gajos finos con el color de cada ángulo.
        val gajos = 180
        val paso = 360f / gajos
        for (i in 0 until gajos) {
            drawArc(
                color = Color.hsv(i * paso, 1f, 1f),
                startAngle = i * paso,
                sweepAngle = paso + 1f,
                useCenter = true,
                topLeft = Offset(centro.x - radio, centro.y - radio),
                size = Size(radio * 2f, radio * 2f),
            )
        }
        // Saturación: blanco en el centro que se desvanece hacia el borde.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = centro,
                radius = radio,
            ),
            radius = radio,
            center = centro,
        )
        // No se oscurece la rueda según el brillo: aplicar (1 − valor) de negro
        // la dejaba casi negra con colores oscuros (p. ej. #192021) y no se
        // podía elegir el tono. El brillo se ve en la muestra y el hex de abajo.
        // Marcador del color elegido.
        val angulo = Math.toRadians(tono.toDouble())
        val marcador = Offset(
            centro.x + (cos(angulo) * saturacion * radio).toFloat(),
            centro.y + (sin(angulo) * saturacion * radio).toFloat(),
        )
        drawCircle(Color.White, radius = 11f, center = marcador, style = Stroke(width = 3f))
        drawCircle(Color.Black, radius = 11f, center = marcador, style = Stroke(width = 1f))
    }
}

/** Traduce un punto de la rueda a (tono, saturación) según ángulo y radio. */
private fun puntoARueda(punto: Offset, ancho: Int, alto: Int): Pair<Float, Float> {
    val radio = minOf(ancho, alto) / 2f
    if (radio <= 1f) return 0f to 0f
    val cx = ancho / 2f
    val cy = alto / 2f
    val dx = punto.x - cx
    val dy = punto.y - cy
    val angulo = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
    val sat = (hypot(dx.toDouble(), dy.toDouble()).toFloat() / radio).coerceIn(0f, 1f)
    return angulo to sat
}
