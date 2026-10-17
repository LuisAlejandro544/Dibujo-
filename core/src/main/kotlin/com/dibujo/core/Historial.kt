package com.dibujo.core

/**
 * Historial de la hoja con deshacer/rehacer por snapshots.
 * Límite de pasos configurable (por defecto 30: con hoja del tamaño de
 * pantalla cada snapshot son ~6-10 MB, y el C++ replica el mismo límite).
 */
class Historial(private val maximo: Int = 30) {
    private val pasado = ArrayDeque<IntArray>()
    private val futuro = ArrayDeque<IntArray>()

    fun guardar(l: Lienzo) {
        pasado.addLast(l.pixeles.copyOf())
        while (pasado.size > maximo) pasado.removeFirst()
        futuro.clear()
    }

    fun puedeDeshacer(): Boolean = pasado.isNotEmpty()
    fun puedeRehacer(): Boolean = futuro.isNotEmpty()

    fun deshacer(l: Lienzo): Boolean {
        val estado = pasado.removeLastOrNull() ?: return false
        futuro.addLast(l.pixeles.copyOf())
        System.arraycopy(estado, 0, l.pixeles, 0, estado.size)
        return true
    }

    fun rehacer(l: Lienzo): Boolean {
        val estado = futuro.removeLastOrNull() ?: return false
        pasado.addLast(l.pixeles.copyOf())
        System.arraycopy(estado, 0, l.pixeles, 0, estado.size)
        return true
    }
}
