package com.dibujo.android

import android.content.Context
import com.dibujo.core.Colores
import com.dibujo.core.Lienzo
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlin.math.abs

/**
 * Limpiador de trazos con un modelo chico (LiteRT) entrenado en el repo.
 *
 * Reemplaza al adelgazado Zhang-Suen por una red que limpia los repases y
 * devuelve un esqueleto de 1 px parecido al ideal. El modelo (64×64 de entrada)
 * se corre por parches de 64×64 con paso 48 (solape de 16 px) y se recompone la
 * hoja completa. Cada píxel limpio toma el color de la tinta original más
 * cercana, así se conserva el color del trazo del usuario.
 *
 * Si el modelo no está empaquetado o algo falla, [limpiar] devuelve `null` y el
 * ViewModel cae al algoritmo determinista (`MotorDibujo.limpiarTrazos`).
 * Ver `tools/entrenar_limpiar_trazos.py` para reproducir el modelo.
 */
object LimpiadorTrazosIa {

    /** Lado del parche que espera el modelo (TFLite `[1, 64, 64, 1]`). */
    const val LADO = 64

    /** Paso entre parches: < LADO deja solape para no cortar los trazos. */
    private const val PASO = 48
    private const val UMBRAL = 0.5f

    /** Radio (px) en que se busca el color del trazo para un píxel del esqueleto. */
    private const val RADIO_COLOR = 6

    private const val RUTA_MODELO = "modelos/limpiar_trazos.tflite"

    @Volatile private var modelo: CompiledModel? = null

    @Volatile private var fallo = false

    /** `true` si el modelo está empaquetado y no falló una carga previa. */
    fun disponible(context: Context): Boolean =
        !fallo && (modelo != null || existeModelo(context))

    private fun existeModelo(context: Context): Boolean = try {
        context.assets.open(RUTA_MODELO).close()
        true
    } catch (_: Exception) {
        false
    }

    private fun cargar(context: Context): CompiledModel? {
        modelo?.let { return it }
        if (fallo) return null
        return try {
            CompiledModel.create(
                context.assets,
                RUTA_MODELO,
                CompiledModel.Options(Accelerator.CPU),
            ).also { modelo = it }
        } catch (_: Exception) {
            fallo = true
            null
        }
    }

    /**
     * Limpia [pixeles] (ancho × alto ARGB) y devuelve la hoja nueva del mismo
     * tamaño, o `null` si el modelo no está disponible o falla (para que el
     * llamador use el algoritmo). [fondo] es el color del papel.
     */
    fun limpiar(
        context: Context,
        ancho: Int,
        alto: Int,
        fondo: Int = Colores.BLANCO,
        pixeles: IntArray,
    ): IntArray? {
        if (ancho <= 0 || alto <= 0 || pixeles.size != ancho * alto) return null
        val m = cargar(context) ?: return null
        val acumulado = FloatArray(ancho * alto)
        val entrada = FloatArray(LADO * LADO)
        return try {
            val entradas = m.createInputBuffers()
            val salidas = m.createOutputBuffers()
            var ty = 0
            while (ty < alto) {
                var tx = 0
                while (tx < ancho) {
                    cargarParche(pixeles, ancho, alto, fondo, tx, ty, entrada)
                    entradas[0].writeFloat(entrada)
                    m.run(entradas, salidas)
                    volcarParche(acumulado, ancho, alto, tx, ty, salidas[0].readFloat())
                    tx += PASO
                }
                ty += PASO
            }
            redibujar(ancho, alto, fondo, pixeles, acumulado)
        } catch (_: Exception) {
            fallo = true
            null
        }
    }

    /** Rellena [destino] (64×64) con la tinta del parche; fuera de hoja = 0. */
    private fun cargarParche(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        fondo: Int,
        tx: Int,
        ty: Int,
        destino: FloatArray,
    ) {
        var k = 0
        for (y in 0 until LADO) {
            val fy = ty + y
            for (x in 0 until LADO) {
                val fx = tx + x
                destino[k++] =
                    if (fx < ancho && fy < alto && pixeles[fy * ancho + fx] != fondo) 1f else 0f
            }
        }
    }

    /** Acumula la salida del parche tomando el máximo (unión sin costuras). */
    private fun volcarParche(
        acumulado: FloatArray,
        ancho: Int,
        alto: Int,
        tx: Int,
        ty: Int,
        salida: FloatArray,
    ) {
        var k = 0
        for (y in 0 until LADO) {
            val fy = ty + y
            for (x in 0 until LADO) {
                val fx = tx + x
                if (fx < ancho && fy < alto) {
                    val i = fy * ancho + fx
                    if (salida[k] > acumulado[i]) acumulado[i] = salida[k]
                }
                k++
            }
        }
    }

    /**
     * Convierte la máscara acumulada (> [UMBRAL]) en la hoja limpia: redibuja un
     * disco fino con el color de la tinta original más cercana. Los píxeles del
     * esqueleto que caen fuera de la tinta original toman el color del trazo más
     * próximo para no perder el color.
     */
    private fun redibujar(
        ancho: Int,
        alto: Int,
        fondo: Int,
        pixeles: IntArray,
        acumulado: FloatArray,
    ): IntArray {
        val salida = Lienzo(ancho, alto, fondo)
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                if (acumulado[y * ancho + x] <= UMBRAL) continue
                val color = colorCercano(pixeles, ancho, alto, fondo, x, y) ?: continue
                salida.disco(x.toFloat(), y.toFloat(), Lienzo.RADIO_LIMPIEZA, color)
            }
        }
        return salida.pixeles
    }

    /** Color de la tinta original en (x, y) o el del anillo más próximo. */
    private fun colorCercano(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        fondo: Int,
        x: Int,
        y: Int,
    ): Int? {
        val propio = pixeles[y * ancho + x]
        if (propio != fondo) return propio
        for (r in 1..RADIO_COLOR) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (maxOf(abs(dx), abs(dy)) != r) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= ancho || ny >= alto) continue
                    val c = pixeles[ny * ancho + nx]
                    if (c != fondo) return c
                }
            }
        }
        return null
    }
}
