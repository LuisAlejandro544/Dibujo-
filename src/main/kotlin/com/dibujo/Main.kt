package com.dibujo

import java.io.File

typealias Grid = MutableList<MutableList<Char>>

class Lienzo(val ancho: Int, val alto: Int, val vacio: Char = '.') {
    val celdas: Grid = List(alto) { MutableList(ancho) { vacio } }.toMutableList()

    fun pintar(x: Int, y: Int, color: Char) {
        if (x in 0 until ancho && y in 0 until alto) {
            celdas[y][x] = color
        }
    }

    fun borrar(x: Int, y: Int) = pintar(x, y, vacio)

    fun rellenar(x: Int, y: Int, color: Char) {
        val orig = celdas.getOrNull(y)?.getOrNull(x) ?: return
        if (orig == color) return
        val pila = ArrayDeque<Pair<Int, Int>>()
        pila.add(x to y)
        while (pila.isNotEmpty()) {
            val (cx, cy) = pila.removeLast()
            if (cx !in 0 until ancho || cy !in 0 until alto) continue
            if (celdas[cy][cx] != orig) continue
            celdas[cy][cx] = color
            pila.add(cx + 1 to cy)
            pila.add(cx - 1 to cy)
            pila.add(cx to cy + 1)
            pila.add(cx to cy - 1)
        }
    }

    fun linea(x0: Int, y0: Int, x1: Int, y1: Int, color: Char) {
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var x = x0
        var y = y0
        while (true) {
            pintar(x, y, color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    fun rectangulo(x0: Int, y0: Int, x1: Int, y1: Int, color: Char, relleno: Boolean = false) {
        val xA = minOf(x0, x1)
        val xB = maxOf(x0, x1)
        val yA = minOf(y0, y1)
        val yB = maxOf(y0, y1)
        for (y in yA..yB) {
            for (x in xA..xB) {
                val borde = x == xA || x == xB || y == yA || y == yB
                if (relleno || borde) pintar(x, y, color)
            }
        }
    }

    fun limpiar() {
        for (y in 0 until alto) for (x in 0 until ancho) celdas[y][x] = vacio
    }

    fun render(): String = celdas.joinToString("\n") { fila -> fila.joinToString("") }
}

class Editor(private val lienzo: Lienzo) {
    private val historial = ArrayDeque<Lienzo>()
    private val rehacer = ArrayDeque<Lienzo>()

    private fun clonar(): Lienzo {
        val c = Lienzo(lienzo.ancho, lienzo.alto, lienzo.vacio)
        for (y in 0 until lienzo.alto) {
            for (x in 0 until lienzo.ancho) {
                c.celdas[y][x] = lienzo.celdas[y][x]
            }
        }
        return c
    }

    fun guardarEstado() {
        historial.addLast(clonar())
        if (historial.size > 100) historial.removeFirst()
        rehacer.clear()
    }

    fun deshacer(): Boolean {
        val previo = historial.removeLastOrNull() ?: return false
        rehacer.addLast(clonar())
        copiarDesde(previo)
        return true
    }

    fun rehacerCambio(): Boolean {
        val siguiente = rehacer.removeLastOrNull() ?: return false
        historial.addLast(clonar())
        copiarDesde(siguiente)
        return true
    }

    private fun copiarDesde(origen: Lienzo) {
        for (y in 0 until lienzo.alto) {
            for (x in 0 until lienzo.ancho) {
                lienzo.celdas[y][x] = origen.celdas[y][x]
            }
        }
    }

    fun tieneHistorial(): Boolean = historial.isNotEmpty()
    fun tieneRehacer(): Boolean = rehacer.isNotEmpty()
}

class App {
    private val lienzo = Lienzo(40, 20)
    private val editor = Editor(lienzo)
    private val palette = charArrayOf('B', 'R', 'G', 'Y', 'W', '*')
    private var pincel = 'B'
    private var x = 0
    private var y = 0
    private var corriendo = true

    private fun comandos(): String = """
        Comandos:
          p x y        pintar pixel en (x,y)
          l x0 y0 x1 y1   linea
          r x0 y0 x1 y1   rectangulo
          rf x0 y0 x1 y1  rectangulo relleno
          f x y        rellenar (flood fill)
          e x y        borrar pixel
          c            limpiar lienzo
          b X          elegir pincel (${palette.joinToString("")})
          u / v        deshacer / rehacer
          s archivo    guardar en archivo
          o archivo    abrir archivo
          q            salir
        (usa "d" para dibujar el lienzo)
    """.trimIndent()

    fun ejecutar(entrada: Iterator<String>, salida: Appendable) {
        while (corriendo) {
            salida.append(mostrar())
            if (!entrada.hasNext()) break
            val linea = entrada.next()
            procesar(linea, salida)
        }
    }

    private fun mostrar(): String = buildString {
        appendLine("=== Dibujo ===  pincel: ${pincel}  cursor: ($x,$y)")
        append(lienzo.render())
        appendLine()
        appendLine(">")
    }

    private fun procesar(linea: String, salida: Appendable) {
        val partes = linea.trim().split(Regex("\\s+"))
        if (partes.firstOrNull().isNullOrEmpty()) return
        val cmd = partes[0]
        val nums = partes.drop(1).mapNotNull { it.toIntOrNull() }

        fun fail(msg: String) = salida.appendLine(msg)

        when (cmd) {
            "d" -> salida.append(lienzo.render() + "\n")
            "p" -> if (nums.size >= 2) { editor.guardarEstado(); x = nums[0]; y = nums[1]; lienzo.pintar(x, y, pincel) } else fail("uso: p x y")
            "l" -> if (nums.size >= 4) { editor.guardarEstado(); lienzo.linea(nums[0], nums[1], nums[2], nums[3], pincel) } else fail("uso: l x0 y0 x1 y1")
            "r" -> if (nums.size >= 4) { editor.guardarEstado(); lienzo.rectangulo(nums[0], nums[1], nums[2], nums[3], pincel) } else fail("uso: r x0 y0 x1 y1")
            "rf" -> if (nums.size >= 4) { editor.guardarEstado(); lienzo.rectangulo(nums[0], nums[1], nums[2], nums[3], pincel, true) } else fail("uso: rf x0 y0 x1 y1")
            "f" -> if (nums.size >= 2) { editor.guardarEstado(); x = nums[0]; y = nums[1]; lienzo.rellenar(x, y, pincel) } else fail("uso: f x y")
            "e" -> if (nums.size >= 2) { editor.guardarEstado(); x = nums[0]; y = nums[1]; lienzo.borrar(x, y) } else fail("uso: e x y")
            "c" -> { editor.guardarEstado(); lienzo.limpiar() }
            "b" -> {
                val ini = partes.getOrNull(1)?.uppercase()?.firstOrNull()
                if (ini != null && palette.contains(ini)) pincel = ini else fail("pincel invalido")
            }
            "u" -> fail(if (editor.deshacer()) "ok deshacer" else "no hay que deshacer")
            "v" -> fail(if (editor.rehacerCambio()) "ok rehacer" else "no hay que rehacer")
            "s" -> guardar(partes.getOrNull(1), salida)
            "o" -> abrir(partes.getOrNull(1), salida)
            "h", "?" -> salida.append(comandos() + "\n")
            "q" -> corriendo = false
            else -> fail("comando no reconocido: $cmd (h para ayuda)")
        }
    }

    private fun guardar(nombre: String?, salida: Appendable) {
        if (nombre == null) { salida.appendLine("uso: s archivo"); return }
        File(nombre).writeText(lienzo.render())
        salida.appendLine("guardado en $nombre")
    }

    private fun abrir(nombre: String?, salida: Appendable) {
        val archivo = nombre ?: run { salida.appendLine("uso: o archivo"); return }
        val archivoF = File(archivo)
        if (!archivoF.exists()) { salida.appendLine("archivo no encontrado: $archivo"); return }
        val filas = archivoF.readLines().filter { it.isNotEmpty() }
        editor.guardarEstado()
        for (yy in 0 until minOf(lienzo.alto, filas.size)) {
            val fila = filas[yy]
            for (xx in 0 until minOf(lienzo.ancho, fila.length)) {
                lienzo.celdas[yy][xx] = if (fila[xx] == lienzo.vacio) lienzo.vacio else fila[xx]
            }
        }
        salida.appendLine("abierto $archivo")
    }
}

fun main(args: Array<String>) {
    val app = App()
    if (args.isEmpty()) {
        app.ejecutar(generateSequence { readlnOrNull() }.iterator(), System.out)
    } else {
        app.ejecutar(listOf("h").iterator(), System.out)
    }
}
