package com.dibujo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as RutaAndroid
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

/**
 * Trazo vectorial vivo: una polilínea suavizada en coordenadas de hoja que se
 * dibuja como capa Compose (no se hornea en los píxeles) y se puede mover,
 * escalar y borrar en cualquier momento, igual que [TextoEnHoja].
 *
 * Los puntos van en pares (x, y) dentro de [puntos]. Al escalar el objeto se
 * transforman los puntos y el grosor juntos, así el trazo mantiene su aspecto.
 */
data class TrazoVectorial(
    val id: Long,
    val puntos: List<Float>,
    val color: Int,
    val grosor: Float,
) {
    init {
        require(puntos.size >= 2 && puntos.size % 2 == 0) {
            "puntos debe tener pares (x, y)"
        }
        require(grosor > 0f) { "grosor debe ser positivo" }
    }

    /** Cantidad de vértices (cada uno son dos floats). */
    val cantidadPuntos: Int get() = puntos.size / 2

    /** Caja (x0, y0, x1, y1) que ocupa el trazo, sin contar el grosor. */
    fun limites(): FloatArray {
        var x0 = Float.POSITIVE_INFINITY
        var y0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        var y1 = Float.NEGATIVE_INFINITY
        var i = 0
        while (i + 1 < puntos.size) {
            val x = puntos[i]
            val y = puntos[i + 1]
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
            i += 2
        }
        return floatArrayOf(x0, y0, x1, y1)
    }
}

/**
 * Geometría y rasterizado de los trazos vectoriales.
 *
 * El suavizado es Catmull-Rom convertido a Bézier cúbicas: el trazo pasa por
 * todos los vértices y no hace falta guardar puntos de control. Al soltar se
 * simplifica con Ramer-Douglas-Peucker, así un trazo largo queda liviano sin
 * perder la forma. Todo vive en `:app` (no toca JNI ni la paridad del motor).
 */
object VectorHoja {

    /** Tolerancia de simplificación, en px de hoja. */
    private const val TOLERANCIA_MINIMA = 0.35f

    /** Path de Compose con el trazo suavizado; `null` si es un solo punto. */
    fun caminoCompose(puntos: List<Float>, escala: Float = 1f): Path? {
        if (puntos.size < 4 || escala <= 0f) return null
        val ruta = Path()
        ruta.moveTo(puntos[0] * escala, puntos[1] * escala)
        recorrerCurvas(puntos, escala) { _, _, c1x, c1y, c2x, c2y, x1, y1 ->
            ruta.cubicTo(c1x, c1y, c2x, c2y, x1, y1)
        }
        return ruta
    }

    /** Path de Android con el mismo suavizado (para hornear al exportar). */
    private fun caminoAndroid(puntos: List<Float>, escala: Float): RutaAndroid? {
        if (puntos.size < 4 || escala <= 0f) return null
        val ruta = RutaAndroid()
        ruta.moveTo(puntos[0] * escala, puntos[1] * escala)
        recorrerCurvas(puntos, escala) { _, _, c1x, c1y, c2x, c2y, x1, y1 ->
            ruta.cubicTo(c1x, c1y, c2x, c2y, x1, y1)
        }
        return ruta
    }

    /**
     * Hornea [vectores] sobre el buffer ARGB de la hoja. [escala] se usa para
     * las miniaturas de la biblioteca (reduce puntos y grosor a la vez).
     */
    fun dibujarEnPixeles(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        vectores: List<TrazoVectorial>,
        escala: Float = 1f,
    ) {
        if (vectores.isEmpty() || ancho <= 0 || alto <= 0 || escala <= 0f) return
        if (pixeles.size < ancho * alto) return
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        val canvas = Canvas(bitmap)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        vectores.forEach { trazo ->
            pincel.color = trazo.color
            pincel.strokeWidth = (trazo.grosor * escala).coerceAtLeast(0.5f)
            if (trazo.cantidadPuntos == 1) {
                pincel.style = Paint.Style.FILL
                canvas.drawCircle(
                    trazo.puntos[0] * escala,
                    trazo.puntos[1] * escala,
                    pincel.strokeWidth / 2f,
                    pincel,
                )
                pincel.style = Paint.Style.STROKE
            } else {
                val ruta = caminoAndroid(trazo.puntos, escala) ?: return@forEach
                canvas.drawPath(ruta, pincel)
            }
        }
        bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        bitmap.recycle()
    }

    /**
     * Simplifica una polilínea (pares x, y) con Ramer-Douglas-Peucker. La
     * tolerancia está en px de hoja; al agrandar la hoja conviene una tolerancia
     * proporcionalmente menor para no perder detalle.
     */
    fun simplificar(puntos: List<Float>, tolerancia: Float): List<Float> {
        val vertices = puntos.size / 2
        if (vertices <= 2) return puntos
        val limite = max(tolerancia, TOLERANCIA_MINIMA)
        val limite2 = limite * limite
        val conservar = BooleanArray(vertices)
        conservar[0] = true
        conservar[vertices - 1] = true
        val pendientes = ArrayDeque<IntArray>()
        pendientes.addLast(intArrayOf(0, vertices - 1))
        while (pendientes.isNotEmpty()) {
            val rango = pendientes.removeLast()
            val inicio = rango[0]
            val fin = rango[1]
            val ax = puntos[inicio * 2]
            val ay = puntos[inicio * 2 + 1]
            val bx = puntos[fin * 2]
            val by = puntos[fin * 2 + 1]
            var lejano = -1
            var mayor = limite2
            for (i in inicio + 1 until fin) {
                val distancia = distanciaCuadrado(
                    puntos[i * 2],
                    puntos[i * 2 + 1],
                    ax,
                    ay,
                    bx,
                    by,
                )
                if (distancia > mayor) {
                    mayor = distancia
                    lejano = i
                }
            }
            if (lejano > 0) {
                conservar[lejano] = true
                pendientes.addLast(intArrayOf(inicio, lejano))
                pendientes.addLast(intArrayOf(lejano, fin))
            }
        }
        val salida = ArrayList<Float>(vertices * 2)
        for (i in 0 until vertices) {
            if (conservar[i]) {
                salida.add(puntos[i * 2])
                salida.add(puntos[i * 2 + 1])
            }
        }
        return salida
    }

    /** Distancia al cuadrado del punto ([px], [py]) al trazo (mínima al segmento). */
    fun distanciaCuadradaAlTrazo(
        trazo: TrazoVectorial,
        px: Float,
        py: Float,
    ): Float {
        if (trazo.cantidadPuntos == 1) {
            val dx = px - trazo.puntos[0]
            val dy = py - trazo.puntos[1]
            return dx * dx + dy * dy
        }
        var menor = Float.POSITIVE_INFINITY
        var i = 0
        while (i + 3 < trazo.puntos.size) {
            val distancia = distanciaCuadrado(
                px,
                py,
                trazo.puntos[i],
                trazo.puntos[i + 1],
                trazo.puntos[i + 2],
                trazo.puntos[i + 3],
            )
            if (distancia < menor) menor = distancia
            i += 2
        }
        return menor
    }

    /** Distancia al cuadrado de un punto al segmento AB. */
    private fun distanciaCuadrado(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        if (largo2 <= 1e-9f) {
            val ex = px - ax
            val ey = py - ay
            return ex * ex + ey * ey
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / largo2).coerceIn(0f, 1f)
        val ex = px - (ax + t * dx)
        val ey = py - (ay + t * dy)
        return ex * ex + ey * ey
    }

    /**
     * Recorre los segmentos con los puntos de control de Catmull-Rom. El primer
     * y el último vértice repiten su vecino para que la curva no se abra.
     */
    private inline fun recorrerCurvas(
        puntos: List<Float>,
        escala: Float,
        accion: (
            x0: Float,
            y0: Float,
            c1x: Float,
            c1y: Float,
            c2x: Float,
            c2y: Float,
            x1: Float,
            y1: Float,
        ) -> Unit,
    ) {
        val vertices = puntos.size / 2
        for (i in 0 until vertices - 1) {
            val x0 = puntos[i * 2] * escala
            val y0 = puntos[i * 2 + 1] * escala
            val x1 = puntos[(i + 1) * 2] * escala
            val y1 = puntos[(i + 1) * 2 + 1] * escala
            val xPrev = if (i > 0) puntos[(i - 1) * 2] * escala else x0
            val yPrev = if (i > 0) puntos[(i - 1) * 2 + 1] * escala else y0
            val xNext = if (i + 2 < vertices) puntos[(i + 2) * 2] * escala else x1
            val yNext = if (i + 2 < vertices) puntos[(i + 2) * 2 + 1] * escala else y1
            accion(
                x0,
                y0,
                x0 + (x1 - xPrev) / 6f,
                y0 + (y1 - yPrev) / 6f,
                x1 - (xNext - x0) / 6f,
                y1 - (yNext - y0) / 6f,
                x1,
                y1,
            )
        }
    }
}

/**
 * Dibuja [trazo] en el lienzo Compose. [zoom] sólo se usa para que el marco de
 * selección conserve 2 px de pantalla en cualquier acercamiento.
 */
fun DrawScope.dibujarTrazoVectorial(
    trazo: TrazoVectorial,
    seleccionado: Boolean,
    zoom: Float,
) {
    val color = Color(trazo.color)
    val ancho = trazo.grosor.coerceAtLeast(0.5f)
    if (trazo.cantidadPuntos == 1) {
        drawCircle(
            color = color,
            radius = ancho / 2f,
            center = Offset(trazo.puntos[0], trazo.puntos[1]),
        )
    } else {
        val ruta = VectorHoja.caminoCompose(trazo.puntos)
        if (ruta != null) {
            drawPath(
                path = ruta,
                color = color,
                style = Stroke(
                    width = ancho,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
    if (seleccionado) {
        val caja = trazo.limites()
        val zoomSeguro = zoom.coerceAtLeast(0.0001f)
        val margen = ancho / 2f + 2f / zoomSeguro
        drawRect(
            color = Color(0xFF1E88E5),
            topLeft = Offset(caja[0] - margen, caja[1] - margen),
            size = Size(
                (caja[2] - caja[0] + margen * 2f).coerceAtLeast(1f),
                (caja[3] - caja[1] + margen * 2f).coerceAtLeast(1f),
            ),
            style = Stroke(width = 2f / zoomSeguro),
        )
    }
}
