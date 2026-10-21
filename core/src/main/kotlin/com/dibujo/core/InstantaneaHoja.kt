package com.dibujo.core

/**
 * Instantánea de la hoja para el historial, **comprimida**.
 *
 * Antes cada paso del historial guardaba una copia completa de la hoja (4
 * bytes por píxel): con una hoja de 1080×2000 eso son ~8,6 MB por paso y 30
 * pasos son ~260 MB; con una hoja de 4096×4096 son 64 MB por paso y el total
 * pasa de 2 GB (la app se quedaba sin memoria antes de poder deshacer).
 *
 * Ahora la instantánea se guarda con **RLE**: rachas de píxeles iguales, en
 * pares (cantidad, color). En una hoja de dibujo —mucho fondo del mismo color—
 * la reducción es enorme (de ~8,6 MB a decenas de KB) y el resultado es
 * **exactamente el mismo**: no se pierde ni un píxel. Cuando el RLE no
 * conviene (una imagen ruidosa) se guarda la copia cruda, así el historial
 * nunca ocupa más memoria que antes.
 *
 * La compresión es **sin pérdida**: deshacer y rehacer devuelven la hoja tal
 * cual estaba. El C++ replica este mismo formato (paridad en `MotorTest`).
 */
class InstantaneaHoja private constructor(
    /** Datos crudos, o RLE (cantidad, color, cantidad, color, ...) si comprime. */
    private val datos: IntArray,
    /** `true` si [datos] es un RLE; `false` si es la copia cruda de la hoja. */
    private val comprimido: Boolean,
    /** Cantidad de píxeles que representa la instantánea. */
    val pixeles: Int,
) {

    /** Memoria aproximada que ocupa la instantánea (en bytes). */
    val bytes: Int get() = datos.size * 4

    /**
     * Restaura la instantánea sobre [destino] (buffer ARGB). Si el destino es
     * más chico que la instantánea se copia lo que entre; si es más grande,
     * el resto queda como estaba (no debería pasar: al cambiar el tamaño de la
     * hoja el historial se limpia).
     */
    fun restaurar(destino: IntArray) {
        if (!comprimido) {
            val n = minOf(datos.size, destino.size)
            System.arraycopy(datos, 0, destino, 0, n)
            return
        }
        var i = 0
        var posicion = 0
        while (i + 1 < datos.size && posicion < destino.size) {
            val cuenta = datos[i]
            val color = datos[i + 1]
            i += 2
            var k = 0
            while (k < cuenta && posicion < destino.size) {
                destino[posicion++] = color
                k++
            }
        }
    }

    companion object {
        /**
         * Captura la hoja en una instantánea comprimida. [pixeles] es el
         * buffer ARGB de la hoja (no se copia si el RLE no conviene).
         */
        fun capturar(pixeles: IntArray): InstantaneaHoja {
            val n = pixeles.size
            if (n == 0) return InstantaneaHoja(IntArray(0), false, 0)
            // Primera pasada: cuántas rachas de píxeles iguales hay.
            var rachas = 0
            for (i in 0 until n) {
                if (i == 0 || pixeles[i] != pixeles[i - 1]) rachas++
            }
            // El RLE ocupa 2 enteros por racha: sólo conviene si no supera el
            // tamaño crudo (si no, se guarda la copia tal cual).
            if (rachas * 2 <= n) {
                val datos = IntArray(rachas * 2)
                var escritura = 0
                var inicio = 0
                while (inicio < n) {
                    var fin = inicio + 1
                    while (fin < n && pixeles[fin] == pixeles[inicio]) fin++
                    datos[escritura++] = fin - inicio
                    datos[escritura++] = pixeles[inicio]
                    inicio = fin
                }
                return InstantaneaHoja(datos, true, n)
            }
            return InstantaneaHoja(pixeles.copyOf(), false, n)
        }
    }
}
