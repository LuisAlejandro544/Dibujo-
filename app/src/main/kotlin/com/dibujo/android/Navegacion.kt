package com.dibujo.android

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pantallas de la app. La navegación es un estado simple, sin dependencias. */
private enum class Destino {
    MENU,
    DIBUJO,
    CARGAR,
    OPCIONES,
    OPCIONES_MOTOR,
    OPCIONES_DIBUJO,
    OPCIONES_RETROALIMENTACION,
    OPCIONES_HISTORIAL,
}

/**
 * Raíz de la app: decide qué pantalla mostrar. Las pantallas son archivos
 * aparte (`PantallaMenu`, `PantallaDibujo`, `PantallaCargar`,
 * `PantallaOpciones`) y comparten el mismo [DibujoViewModel] (que por vivir en
 * la Activity conserva la hoja en memoria al ir y volver del menú).
 */
@Composable
fun AppDibujo(vm: DibujoViewModel = viewModel()) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var destino by rememberSaveable { mutableStateOf(Destino.MENU) }
    // Desde dónde se abrió Opciones, para volver al lugar correcto.
    var origenOpciones by rememberSaveable { mutableStateOf(Destino.MENU) }
    var cargando by remember { mutableStateOf(false) }
    // Hay un dibujo en curso (en memoria o autoguardado): "Nuevo dibujo" pide
    // confirmación antes de descartarlo. Se relee al volver al menú.
    var hayBorrador by remember { mutableStateOf(false) }

    val textoCargarError = stringResource(R.string.cargar_error)

    LaunchedEffect(destino) {
        if (destino == Destino.MENU) {
            hayBorrador = vm.estado.value.modificado ||
                withContext(Dispatchers.IO) { AlmacenDibujos.hayActual(contexto) }
        }
    }

    // Abre un dibujo guardado por id y pasa al lienzo. Lee los píxeles fuera
    // del hilo principal (una hoja grande pesa varios MB).
    fun abrirGuardado(id: String) {
        if (cargando) return
        cargando = true
        alcance.launch {
            val contenido = withContext(Dispatchers.IO) { AlmacenDibujos.cargar(contexto, id) }
            cargando = false
            if (contenido == null) {
                Toast.makeText(contexto, textoCargarError, Toast.LENGTH_SHORT).show()
            } else {
                vm.abrirDibujo(
                    contenido.ancho,
                    contenido.alto,
                    contenido.pixeles,
                    contenido.textos,
                    contenido.vectores,
                )
                destino = Destino.DIBUJO
            }
        }
    }

    when (destino) {
        Destino.MENU -> PantallaMenu(
            alNuevo = {
                // Descarta el borrador y abre una hoja en blanco.
                vm.nuevoDibujo()
                destino = Destino.DIBUJO
            },
            alContinuar = {
                if (vm.motorListo()) {
                    destino = Destino.DIBUJO
                } else if (!cargando) {
                    // Reanuda el borrador autoguardado, si existe; si no, abre
                    // un lienzo en blanco.
                    cargando = true
                    alcance.launch {
                        val contenido = withContext(Dispatchers.IO) {
                            AlmacenDibujos.cargarActual(contexto)
                        }
                        cargando = false
                        if (contenido != null) {
                            vm.abrirDibujo(
                                contenido.ancho,
                                contenido.alto,
                                contenido.pixeles,
                                contenido.textos,
                                contenido.vectores,
                            )
                        }
                        destino = Destino.DIBUJO
                    }
                }
            },
            alCargar = { destino = Destino.CARGAR },
            alOpciones = {
                origenOpciones = Destino.MENU
                destino = Destino.OPCIONES
            },
            hayBorrador = hayBorrador,
        )

        Destino.DIBUJO -> PantallaDibujo(
            vm = vm,
            alVolver = {
                // Autoguarda antes de salir: así "Continuar dibujando" retoma
                // esto exactamente.
                vm.autoguardarActual()
                destino = Destino.MENU
            },
            alAbrirOpciones = {
                origenOpciones = Destino.DIBUJO
                destino = Destino.OPCIONES
            },
        )

        Destino.CARGAR -> PantallaCargar(
            alVolver = { destino = Destino.MENU },
            alAbrir = { dibujo -> abrirGuardado(dibujo.id) },
        )

        Destino.OPCIONES -> PantallaOpciones(
            alAbrirMotor = { destino = Destino.OPCIONES_MOTOR },
            alAbrirDibujo = { destino = Destino.OPCIONES_DIBUJO },
            alAbrirRetroalimentacion = { destino = Destino.OPCIONES_RETROALIMENTACION },
            alAbrirHistorial = { destino = Destino.OPCIONES_HISTORIAL },
            alVolver = { destino = origenOpciones },
        )

        Destino.OPCIONES_MOTOR -> PantallaOpcionesMotor(
            estado = vm.estado.collectAsState().value,
            alElegirBackend = vm::elegirBackend,
            alVolver = { destino = Destino.OPCIONES },
        )

        Destino.OPCIONES_DIBUJO -> PantallaOpcionesDibujo(
            estado = vm.estado.collectAsState().value,
            alElegirHerramienta = vm::elegirHerramientaInicial,
            alElegirGrosor = vm::elegirGrosorInicial,
            alElegirTextura = vm::elegirTexturaInicial,
            alVolver = { destino = Destino.OPCIONES },
        )

        Destino.OPCIONES_RETROALIMENTACION -> PantallaOpcionesRetroalimentacion(
            estado = vm.estado.collectAsState().value,
            alAlternarVibracion = vm::alternarVibracion,
            alAlternarSonidos = vm::alternarSonidos,
            alVolver = { destino = Destino.OPCIONES },
        )

        Destino.OPCIONES_HISTORIAL -> PantallaOpcionesHistorial(
            estado = vm.estado.collectAsState().value,
            alElegirLimite = vm::elegirLimiteHistorial,
            alVolver = { destino = Destino.OPCIONES },
        )
    }

    if (cargando) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
