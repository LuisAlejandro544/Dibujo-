package com.dibujo.core

/**
 * Historial de la hoja con deshacer/rehacer por snapshots.
 * Límite de pasos configurable (por defecto 30: con hoja del tamaño de
 * pantalla cada snapshot son ~6-10 MB, y el C++ replica el mismo límite).
 */
class Historial(maximo: Int = 30) {
    private val pasado = ArrayDeque<IntArray>()
    private val futuro = ArrayDeque<IntArray>()
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
        pasado.addLast(l.pixeles.copyOf())
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
