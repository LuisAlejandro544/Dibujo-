package com.dibujo.core

/**
 * Historial de la hoja con deshacer/rehacer por instantáneas **comprimidas**.
 *
 * Límite de pasos configurable (por defecto 30: con hoja del tamaño de
 * pantalla cada paso son ~6-10 MB, y el C++ replica el mismo límite).
 *
 * Las instantáneas se guardan con RLE (ver [InstantaneaHoja]): en una hoja de
 * dibujo la mayor parte es fondo del mismo color, así que cada paso ocupa
 * decenas de KB en vez de megabytes. La compresión es sin pérdida: deshacer y
 * rehacer devuelven la hoja idéntica.
 */
class Historial(maximo: Int = 30) {
    private val pasado = ArrayDeque<InstantaneaHoja>()
    private val futuro = ArrayDeque<InstantaneaHoja>()
    private var maximo = maximo.coerceAtLeast(1)

    /**
     * Cambia el tope de pasos y descarta los más viejos si el nuevo es menor.
     * La configuración vive en la app; el C++ replica el mismo tope.
     */
    fun fijarMaximo(nuevo: Int) {
        maximo = nuevo.coerceAtLeast(1)
        while (pasado.size > maximo) pasado.removeFirst()
    }

    fun guardar(l: Lienzo) {
        pasado.addLast(InstantaneaHoja.capturar(l.pixeles))
        while (pasado.size > maximo) pasado.removeFirst()
        futuro.clear()
    }

    fun puedeDeshacer(): Boolean = pasado.isNotEmpty()
    fun puedeRehacer(): Boolean = futuro.isNotEmpty()

    /**
     * Olvida todos los pasos. Se usa cuando cambia el tamaño de la hoja: los
     * snapshots viejos tienen otras dimensiones y no se pueden restaurar.
     */
    fun limpiar() {
        pasado.clear()
        futuro.clear()
    }

    fun deshacer(l: Lienzo): Boolean {
        val estado = pasado.removeLastOrNull() ?: return false
        futuro.addLast(InstantaneaHoja.capturar(l.pixeles))
        estado.restaurar(l.pixeles)
        return true
    }

    fun rehacer(l: Lienzo): Boolean {
        val estado = futuro.removeLastOrNull() ?: return false
        pasado.addLast(InstantaneaHoja.capturar(l.pixeles))
        estado.restaurar(l.pixeles)
        return true
    }
}
