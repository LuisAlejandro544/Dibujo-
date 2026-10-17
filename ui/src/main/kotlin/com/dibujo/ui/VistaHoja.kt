package com.dibujo.ui

import com.dibujo.core.Camara
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo
import com.dibujo.core.MotorDibujo

/**
 * Renderiza la hoja a texto (con o sin colores ANSI) y la barra de estado.
 */
class VistaHoja(
    private val vw: Int,
    private val vh: Int,
    private val conColor: Boolean = true,
) {
    private val buffer = IntArray(vw * vh)

    /**
     * @param cursor posición del cursor en coordenadas de hoja (null si no se muestra)
     * @param motor motor de dibujo activo (para cámara y render)
     */
    fun hoja(
        motor: MotorDibujo,
        cursor: Par?,
        lapiz: Lapiz,
        modo: Modo,
        plumaActiva: Boolean,
        mensaje: String?,
    ): String {
        motor.renderizar(buffer, vw, vh)
        val cam = motor.camara()
        val sb = StringBuilder()

        // Lienzo con cursor superpuesto.
        val posCursor = cursor?.let { hojaAPantalla(it, cam) }
        for (y in 0 until vh) {
            for (x in 0 until vw) {
                val esCursor = posCursor != null && x == posCursor.x && y == posCursor.y
                val color = buffer[y * vw + x]
                if (esCursor) {
                    sb.append("O")
                } else {
                    sb.append(celda(color))
                }
            }
            sb.append('\n')
        }

        // Barra de estado.
        val punta = if (lapiz.punta == com.dibujo.core.Punta.REDONDA) "redonda" else "cuadrada"
        val estado = buildString {
            append("modo=").append(if (modo == Modo.EDICION) "EDICION" else "MOVER")
            append(" | lápiz=").append(nombreHerramienta(lapiz))
            append(" g").append(lapiz.grosor).append(" ").append(punta)
            append(" | color=").append(nombreColor(lapiz.color))
            append(" | zoom=").append("%.2f".format(cam.zoom))
            append(" | motor=").append(motor.nombre)
            if (plumaActiva) append(" | PLUMA ABAJO")
        }
        sb.append(estado).append('\n')
        if (!mensaje.isNullOrBlank()) sb.append(mensaje).append('\n')
        return sb.toString()
    }

    private fun celda(color: Int): String {
        if (!conColor) return if (color == 0xFFFFFFFF.toInt() || color == 0) "." else "#"
        return "\u001B[48;2;${rojo(color)};${verde(color)};${azul(color)}m \u001B[0m"
    }

    /** Convierte coordenadas de hoja a pantalla según la cámara (con rotación). */
    fun hojaAPantalla(p: Par, cam: Camara): Par {
        val zx = p.x * cam.zoom
        val zy = p.y * cam.zoom
        val c = kotlin.math.cos(cam.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam.rotacion.toDouble()).toFloat()
        val x = c * zx - s * zy - cam.panX
        val y = s * zx + c * zy - cam.panY
        return Par(x.toInt(), y.toInt())
    }

    private fun rojo(c: Int) = (c shr 16) and 0xFF
    private fun verde(c: Int) = (c shr 8) and 0xFF
    private fun azul(c: Int) = c and 0xFF

    private fun nombreColor(argb: Int): String =
        com.dibujo.core.Colores.PALETA.firstOrNull { it.argb == argb }?.nombre ?: "#%06X".format(argb and 0xFFFFFF)

    private fun nombreHerramienta(l: Lapiz): String = when (l.herramienta) {
        com.dibujo.core.Herramienta.LAPIZ -> "lápiz"
        com.dibujo.core.Herramienta.PORTAMINA -> "portamina"
        com.dibujo.core.Herramienta.CARBONCILLO -> "carboncillo"
        com.dibujo.core.Herramienta.BORRADOR -> "borrador"
        com.dibujo.core.Herramienta.PINTAR -> "pintar"
        com.dibujo.core.Herramienta.DOBLE -> "doble"
    }
}

/** Punto entero simple (evita depender de pares genéricos). */
data class Par(val x: Int, val y: Int)
