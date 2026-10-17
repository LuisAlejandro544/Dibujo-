package com.dibujo.android.debug

import android.content.Context
import com.dibujo.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BYTES_POR_KB = 1024.0

private val FORMATO_HORA = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
private val FORMATO_RELATIVO = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

fun formatearBytes(contexto: Context, bytes: Long): String {
    val mb = bytes / BYTES_POR_KB / BYTES_POR_KB
    return if (mb >= 1.0) {
        contexto.getString(R.string.debug_formato_mb, mb)
    } else {
        contexto.getString(R.string.debug_formato_kb, (bytes / BYTES_POR_KB).toInt())
    }
}

fun formatearKb(contexto: Context, kb: Int): String =
    contexto.getString(R.string.debug_formato_mb, kb / BYTES_POR_KB)

fun formatearDuracion(contexto: Context, ms: Long): String {
    val segundos = ms / 1000
    val minutos = segundos / 60
    val horas = minutos / 60
    return when {
        horas > 0 -> contexto.getString(R.string.debug_duracion_horas, horas, minutos % 60)
        minutos > 0 -> contexto.getString(R.string.debug_duracion_minutos, minutos, segundos % 60)
        else -> contexto.getString(R.string.debug_duracion_segundos, segundos)
    }
}

fun formatearHora(ms: Long): String = FORMATO_HORA.format(Date(ms))

fun formatearRelativo(contexto: Context, ms: Long): String {
    val segundos = ((System.currentTimeMillis() - ms) / 1000).coerceAtLeast(0)
    return contexto.getString(R.string.debug_hace_segundos, segundos)
}
