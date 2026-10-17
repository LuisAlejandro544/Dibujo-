package com.dibujo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

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
