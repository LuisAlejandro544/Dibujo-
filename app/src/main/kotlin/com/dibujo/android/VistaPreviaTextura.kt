package com.dibujo.android

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.dibujo.core.Textura
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Trazo de muestra de una textura: una línea horizontal suave (con una leve
 * ondulación) rasterizada con [Textura.aplicar], la misma modulación de
 * cobertura que usa el motor. Así grano, rugosa, trama y salpicada se
 * distinguen de un vistazo, tanto en el lienzo como en configuración.
 */
@Composable
internal fun VistaPreviaTextura(
    textura: Textura,
    modifier: Modifier = Modifier,
) {
    val imagen = remember(textura) {
        val ancho = 264
        val alto = 68
        val pixeles = IntArray(ancho * alto)
        val fondo = 0xFFFFFFFF.toInt()
        val tinta = 0xFF1D1B20.toInt()
        val radio = 14.0
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                val ondulacion = sin((x / ancho.toFloat()) * Math.PI * 2.0) * 5.0
                val distancia = abs((y + 0.5) - (alto / 2.0 + ondulacion))
                val cobertura = (0.5 + radio - distancia).coerceIn(0.0, 1.0)
                val alfa = textura.aplicar((cobertura * 255.0).roundToInt(), x, y)
                pixeles[y * ancho + x] = mezclarArgb(tinta, fondo, alfa)
            }
        }
        Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Canvas(modifier) {
        drawImage(
            image = imagen,
            dstSize = IntSize(
                size.width.roundToInt().coerceAtLeast(1),
                size.height.roundToInt().coerceAtLeast(1),
            ),
            filterQuality = FilterQuality.None,
        )
    }
}

/** Mezcla [origen] sobre [destino] con cobertura [alfa] (0..255). */
private fun mezclarArgb(origen: Int, destino: Int, alfa: Int): Int {
    val inv = 255 - alfa
    val r = (((origen ushr 16) and 0xFF) * alfa + ((destino ushr 16) and 0xFF) * inv + 127) / 255
    val g = (((origen ushr 8) and 0xFF) * alfa + ((destino ushr 8) and 0xFF) * inv + 127) / 255
    val b = ((origen and 0xFF) * alfa + (destino and 0xFF) * inv + 127) / 255
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
