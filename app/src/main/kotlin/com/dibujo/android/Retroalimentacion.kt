package com.dibujo.android

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Eventos que pueden dar vibración y/o un sonido breve. */
enum class EventoRetroalimentacion { TRAZO, DESHACER, REHACER }

/**
 * Retroalimentación táctil y sonora de la app. La configuración (vibración y
 * sonidos) vive en el `DibujoViewModel`; acá sólo se emite el aviso cuando el
 * usuario dibuja, deshace o rehace. Sin permiso VIBRATE la vibración se ignora
 * silenciosamente y los equipos sin vibrar no hacen nada.
 */
class Retroalimentacion(contexto: Context) : AutoCloseable {

    private val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        contexto.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        contexto.getSystemService(Vibrator::class.java)
    }

    /** Se crea recién al primer sonido para no reservar audio al arrancar. */
    private val tono = lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUMEN_SONIDO) }.getOrNull()
    }

    fun emitir(evento: EventoRetroalimentacion, vibrar: Boolean, sonar: Boolean) {
        if (vibrar) vibrar(evento)
        if (sonar) sonar(evento)
    }

    private fun vibrar(evento: EventoRetroalimentacion) {
        val v = vibrador ?: return
        if (!v.hasVibrator()) return
        val duracion = when (evento) {
            EventoRetroalimentacion.TRAZO -> 8L
            EventoRetroalimentacion.DESHACER,
            EventoRetroalimentacion.REHACER,
            -> 14L
        }
        val amplitud = if (evento == EventoRetroalimentacion.TRAZO) 70 else 110
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(duracion, amplitud))
        }
    }

    private fun sonar(evento: EventoRetroalimentacion) {
        val generador = tono.value ?: return
        val (tipo, duracion) = when (evento) {
            EventoRetroalimentacion.TRAZO -> ToneGenerator.TONE_PROP_BEEP to 18
            EventoRetroalimentacion.DESHACER -> ToneGenerator.TONE_PROP_BEEP2 to 24
            EventoRetroalimentacion.REHACER -> ToneGenerator.TONE_PROP_ACK to 24
        }
        runCatching { generador.startTone(tipo, duracion) }
    }

    override fun close() {
        if (tono.isInitialized()) runCatching { tono.value?.release() }
    }

    private companion object {
        /** Volumen bajo: son avisos de interacción, no multimedia. */
        const val VOLUMEN_SONIDO = 30
    }
}
