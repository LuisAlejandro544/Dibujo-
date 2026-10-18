package com.dibujo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.dibujo.core.Colores

/** Lado máximo al que se reduce la imagen de calco al decodificarla. */
const val MAX_CALCO = 2048

/**
 * Decodifica la imagen elegida reduciéndola si es muy grande ([MAX_CALCO] por
 * lado). Devuelve null si no se puede leer. El muestreo se calcula en potencias
 * de dos (`inSampleSize`) para no agotar la memoria con fotos enormes.
 */
fun cargarBitmap(contexto: Context, uri: Uri): Bitmap? = runCatching {
    val resolver = contexto.contentResolver
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var escala = 1
    while (limites.outWidth / escala > MAX_CALCO || limites.outHeight / escala > MAX_CALCO) {
        escala *= 2
    }
    val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
}.getOrNull()

/**
 * Recorta [pixeles] (ancho × alto ARGB) al recuadro que contiene lo dibujado,
 * descartando los márgenes que son sólo [fondo]. Devuelve (ancho, alto, píxeles)
 * recortados, o `null` si no hay nada distinto del fondo (hoja vacía).
 *
 * Es la operación que hace "inteligente" al guardar/compartir y al recortar el
 * lienzo: elimina lo que no se dibujó sin tocar ningún trazo (el recorte nunca
 * corta tinta, porque el recuadro es justamente el de la tinta). La usan el
 * recorte de lienzo de `DibujoViewModel` y la exportación de `PantallaDibujo`.
 */
fun recortarAlContenido(
    pixeles: IntArray,
    ancho: Int,
    alto: Int,
    fondo: Int = Colores.BLANCO,
): Triple<Int, Int, IntArray>? {
    if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return null
    var minX = ancho
    var minY = alto
    var maxX = -1
    var maxY = -1
    for (y in 0 until alto) {
        val base = y * ancho
        for (x in 0 until ancho) {
            if (pixeles[base + x] == fondo) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < 0 || maxY < 0) return null
    val nuevoAncho = maxX - minX + 1
    val nuevoAlto = maxY - minY + 1
    val destino = IntArray(nuevoAncho * nuevoAlto)
    for (y in 0 until nuevoAlto) {
        System.arraycopy(
            pixeles, (minY + y) * ancho + minX,
            destino, y * nuevoAncho, nuevoAncho,
        )
    }
    return Triple(nuevoAncho, nuevoAlto, destino)
}
