package com.dibujo.ui

import com.dibujo.core.Colores
import com.dibujo.core.ComandoLinea
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo
import com.dibujo.core.MotorDibujo
import com.dibujo.core.Punta

/**
 * Controlador principal: bucle de la app, comandos y modos.
 *
 * - Modo EDICION: w/a/s/d mueven el cursor, `p` pinta un punto y `.`
 *   baja/solta la "pluma" para trazar continuo mientras se mueve.
 * - Modo MOVER: w/a/s/d panean la vista libremente, +/- hacen zoom.
 */
class ControladorConsola(
    val motor: MotorDibujo,
    private val vw: Int,
    private val vh: Int,
    private val conColor: Boolean = true,
) {
    /** Qué debe hacer el bucle tras procesar una línea. */
    enum class Accion { REDIBUJAR, MENU_COLORES, MENU_LAPICES, SALIR }

    var modo = Modo.EDICION
        private set

    var lapiz = Lapiz()
        private set

    // Cursor en coordenadas de hoja.
    var cursor: Par = Par(0, 0)
        private set

    private var plumaActiva = false
    private var mensaje: String? = null

    private val vista = VistaHoja(vw, vh, conColor)

    /** Procesa una línea. Devuelve la acción para el bucle. */
    fun procesar(linea: String): Accion {
        mensaje = null
        val cmd = ComandoLinea(linea)
        if (cmd.esVacia) return Accion.REDIBUJAR

        // Números solos: color directo (menu cómodo).
        val comoNumero = linea.trim().toIntOrNull()
        if (comoNumero != null && comoNumero in 1..Colores.PALETA.size) {
            lapiz = lapiz.copy(color = Colores.porIndice(comoNumero - 1).argb)
            return Accion.REDIBUJAR.conMensaje("color: ${Colores.porIndice(comoNumero - 1).nombre}")
        }

        val pasos = cmd.numero ?: 1
        return when (cmd.letra) {
            "q" -> Accion.SALIR
            "m" -> alternarModo()
            "h", "?" -> Accion.REDIBUJAR.conMensaje(AYUDA)
            in MOVIMIENTO -> mover(cmd.letra, pasos)
            "p" -> pintarPunto()
            "." -> alternarPluma()
            "l" -> Accion.MENU_LAPICES
            "c" -> Accion.MENU_COLORES
            "g" -> cambiarGrosor()
            "e" -> alternarBorrador()
            "t" -> alternarPunta()
            "z" -> if (motor.deshacer()) REDIBUJAR() else err("nada que deshacer")
            "y" -> if (motor.rehacer()) REDIBUJAR() else err("nada que rehacer")
            "x" -> limpiar()
            "+", "=" -> { motor.zoom(1.25f); REDIBUJAR() }
            "-" -> { motor.zoom(0.8f); REDIBUJAR() }
            "r" -> { motor.rotar(PASO_GIRO); REDIBUJAR().conMensaje("giro: +15°") }
            "," -> { motor.rotar(-PASO_GIRO); REDIBUJAR().conMensaje("giro: -15°") }
            "f" -> centrifugar()
            else -> err("no entiendo: '$cmd' (h para ayuda)")
        }
    }

    /** Bucle de la app: dibuja pantalla y procesa cada línea. */
    fun ejecutar(entrada: Iterator<String>, salida: Appendable) {
        while (true) {
            salida.append(pantalla())
            if (!entrada.hasNext()) break
            var accion = procesar(entrada.next())
            if (accion == Accion.SALIR) {
                salida.append("adiós\n")
                break
            }
            when (accion) {
                Accion.MENU_COLORES -> accion = menuColores(entrada, salida)
                Accion.MENU_LAPICES -> accion = menuLapices(entrada, salida)
                else -> {}
            }
            if (accion == Accion.SALIR) {
                salida.append("adiós\n")
                break
            }
        }
    }

    fun menuColores(entrada: Iterator<String>, salida: Appendable): Accion {
        val elegido = Menu.colores(entrada, salida, lapiz.color)
        if (elegido != null) {
            if (lapiz.herramienta == com.dibujo.core.Herramienta.BORRADOR) {
                lapiz = lapiz.copy(color = elegido, herramienta = com.dibujo.core.Herramienta.LAPIZ)
            } else {
                lapiz = lapiz.copy(color = elegido)
            }
        }
        return Accion.REDIBUJAR
    }

    fun menuLapices(entrada: Iterator<String>, salida: Appendable): Accion {
        val nuevo = Menu.lapices(entrada, salida, lapiz)
        if (nuevo != null) {
            lapiz = nuevo
            motor.configurarLapiz(lapiz)
        }
        return Accion.REDIBUJAR
    }

    /** Vista completa (hoja + estado). */
    fun pantalla(): String = vista.hoja(motor, cursor, lapiz, modo, plumaActiva, mensaje)

    // --- Acciones ---

    private fun mover(letra: String, pasos: Int): Accion {
        val (dx, dy) = when (letra) {
            "w" -> 0 to -1
            "s" -> 0 to 1
            "a" -> -1 to 0
            "d" -> 1 to 0
            else -> 0 to 0
        }
        if (modo == Modo.MOVER) {
            val pasoPx = 20.0f
            motor.pan(dx * pasoPx * pasos, dy * pasoPx * pasos)
            return REDIBUJAR()
        }
        val nx = (cursor.x + dx * pasos).coerceIn(0, motor.ancho - 1)
        val ny = (cursor.y + dy * pasos).coerceIn(0, motor.alto - 1)
        cursor = Par(nx, ny)
        if (plumaActiva) motor.continuarTrazo(nx.toFloat(), ny.toFloat())
        return REDIBUJAR()
    }

    private fun pintarPunto(): Accion {
        if (modo != Modo.EDICION) {
            return err("estás en modo MOVER: cambia a EDICION con 'm'")
        }
        motor.configurarLapiz(lapiz)
        motor.iniciarTrazo(cursor.x.toFloat(), cursor.y.toFloat())
        motor.continuarTrazo(cursor.x.toFloat(), cursor.y.toFloat())
        motor.terminarTrazo()
        return REDIBUJAR()
    }

    private fun alternarPluma(): Accion {
        if (modo != Modo.EDICION) {
            return err("estás en modo MOVER: cambia a EDICION con 'm'")
        }
        if (!plumaActiva) {
            motor.configurarLapiz(lapiz)
            motor.iniciarTrazo(cursor.x.toFloat(), cursor.y.toFloat())
            plumaActiva = true
            return REDIBUJAR().conMensaje("pluma abajo: mueve el cursor, '.' para soltar")
        }
        plumaActiva = false
        return if (motor.terminarTrazo()) REDIBUJAR() else err("trazo vacío, nada soltado")
    }

    private fun cambiarGrosor(): Accion {
        val nuevo = (lapiz.grosor + 1).coerceIn(1, 64)
        lapiz = lapiz.copy(grosor = nuevo)
        return REDIBUJAR().conMensaje("grosor: $nuevo")
    }

    private fun alternarBorrador(): Accion {
        lapiz = lapiz.copy(
            herramienta = if (lapiz.herramienta == com.dibujo.core.Herramienta.LAPIZ) {
                com.dibujo.core.Herramienta.BORRADOR
            } else {
                com.dibujo.core.Herramienta.LAPIZ
            },
        )
        return REDIBUJAR().conMensaje("herramienta: ${lapiz.herramienta}")
    }

    private fun alternarPunta(): Accion {
        lapiz = lapiz.copy(
            punta = if (lapiz.punta == Punta.REDONDA) Punta.CUADRADA else Punta.REDONDA,
        )
        return REDIBUJAR().conMensaje("punta: ${lapiz.punta}")
    }

    private fun limpiar(): Accion {
        motor.limpiarHoja()
        return REDIBUJAR()
    }

    private fun centrifugar(): Accion {
        motor.centrarVista()
        return REDIBUJAR().conMensaje("vista centrada")
    }

    private fun alternarModo(): Accion {
        if (plumaActiva) {
            motor.cancelarTrazo()
            plumaActiva = false
        }
        modo = if (modo == Modo.EDICION) Modo.MOVER else Modo.EDICION
        return REDIBUJAR().conMensaje("modo: $modo")
    }

    private fun err(m: String): Accion {
        mensaje = m
        return Accion.REDIBUJAR
    }

    private fun REDIBUJAR(): Accion = Accion.REDIBUJAR

    private fun Accion.conMensaje(m: String): Accion {
        mensaje = m
        return this
    }

    companion object {
        private val MOVIMIENTO = setOf("w", "a", "s", "d")

        /** Giro por tecla en radianes (15°). */
        private const val PASO_GIRO = 0.2617994f

        val AYUDA = """
            Comandos:
              w/a/s/d [n]   mover cursor (EDICION) o panear vista (MOVER)
              1..9          elegir color directo
              p             pintar punto en el cursor (EDICION)
              .             bajar/soltar la pluma (trazo continuo, EDICION)
              l             menú de lápices
              c             menú de colores
              g             subir grosor
              e             alternar lápiz/borrador
              t             alternar punta redonda/cuadrada
              m             alternar modo EDICION <-> MOVER
              + / -         zoom
              r / ,         girar la hoja +15° / -15°
              f             centrar vista
              z / y         deshacer / rehacer
              x             limpiar hoja
              h o ?         esta ayuda
              q             salir
        """.trimIndent()
    }
}
