package com.dibujo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import com.dibujo.core.Colores
import com.dibujo.core.FiltroImagen
import com.dibujo.core.FiltrosImagen

/** Lado máximo al que se reduce la imagen de calco al decodificarla. */
const val MAX_CALCO = 2048

/**
 * Presupuesto de píxeles de la región del calco que se relee del archivo
 * original a resolución completa. Como la región es, como mucho, la parte de
 * la imagen que se está viendo, el recorte queda cerca del tamaño de la
 * pantalla (unos 10 MB) y no del archivo entero.
 */
const val MAX_PIXELES_REGION_CALCO = 4_000_000

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
 * Tamaño real de la imagen del calco en píxeles, sin reducir. La versión que
 * se carga para la vista general está recortada a [MAX_CALCO]; con este dato
 * se sabe cuánto detalle hay de más y si conviene releer la zona que se está
 * viendo del archivo original (que se ve nítida donde la otra se ve pixelada).
 */
fun tamanoOriginalImagen(contexto: Context, uri: Uri): Pair<Int, Int>? = runCatching {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contexto.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, limites)
    }
    if (limites.outWidth <= 0 || limites.outHeight <= 0) null
    else limites.outWidth to limites.outHeight
}.getOrNull()

/**
 * Decodifica **sólo** la región [region] (en píxeles de la imagen original) a
 * resolución completa.
 *
 * Es lo que evita que el calco se vea pixelado al hacer mucho zoom: en vez de
 * ampliar la versión reducida (que ya no tiene detalle), se vuelve a leer del
 * archivo original únicamente la parte que se está viendo. El recorte se
 * decodea con un muestreo para no pasar del presupuesto de
 * [MAX_PIXELES_REGION_CALCO].
 */
fun decodificarRegionImagen(
    contexto: Context,
    uri: Uri,
    region: android.graphics.Rect,
    maxPixeles: Int = MAX_PIXELES_REGION_CALCO,
): Bitmap? = runCatching {
    if (region.width() <= 0 || region.height() <= 0) return@runCatching null
    val descriptor = contexto.contentResolver.openFileDescriptor(uri, "r")
        ?: return@runCatching null
    descriptor.use {
        val decoder = BitmapRegionDecoder.newInstance(it.fileDescriptor, false)
        try {
            var muestra = 1
            while (
                (region.width().toLong() / muestra) * (region.height().toLong() / muestra) > maxPixeles
            ) {
                muestra *= 2
            }
            decoder.decodeRegion(region, BitmapFactory.Options().apply { inSampleSize = muestra })
        } finally {
            decoder.recycle()
        }
    }
}.getOrNull()

/**
 * Aplica un [FiltroImagen] a un bitmap y devuelve otro con el resultado (el
 * original queda intacto). El filtro vive en `:core` (`FiltrosImagen`), que
 * trabaja sobre buffers ARGB: acá sólo se traduce de y hacia el bitmap.
 */
fun filtrarBitmap(bitmap: Bitmap, filtro: FiltroImagen, ajuste: Int): Bitmap {
    if (filtro == FiltroImagen.NINGUNO) return bitmap
    val ancho = bitmap.width
    val alto = bitmap.height
    if (ancho <= 0 || alto <= 0) return bitmap
    val pixeles = IntArray(ancho * alto)
    bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
    val filtrados = FiltrosImagen.aplicar(pixeles, ancho, alto, filtro, ajuste)
    val salida = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    salida.setPixels(filtrados, 0, ancho, 0, 0, ancho, alto)
    return salida
}

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
