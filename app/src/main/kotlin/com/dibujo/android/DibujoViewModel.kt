package com.dibujo.android

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dibujo.core.BackendRender
import com.dibujo.core.Camara
import com.dibujo.core.Colores
import com.dibujo.core.Cuantizador
import com.dibujo.core.Herramienta
import com.dibujo.core.Lapiz
import com.dibujo.core.Modo
import com.dibujo.core.MotorDibujo
import com.dibujo.engine.Motores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de la pantalla de dibujo (inmutable, se actualiza con copy). */
data class DibujoEstado(
    val lapiz: Lapiz = Lapiz(),
    val modo: Modo = Modo.EDICION,
    val camara: Camara = Camara(0f, 0f, 1f),
    /** Trazo en curso, pares (x, y) en coordenadas de hoja; null si no hay. */
    val trazoEnCurso: List<Float>? = null,
    /** Marco de render: cambia con cada operación para invalidar el lienzo. */
    val marco: Int = 0,
    /** Hay pasos que deshacer (habilita el botón de deshacer). */
    val puedeDeshacer: Boolean = false,
    /** Hay pasos que rehacer (habilita el botón de rehacer). */
    val puedeRehacer: Boolean = false,
    val motorNombre: String = "",
    /** Ancho actual de la hoja en píxeles (cambia al expandir). */
    val hojaAncho: Int = 0,
    /** Alto actual de la hoja en píxeles (cambia al expandir). */
    val hojaAlto: Int = 0,
    /** Imagen de calco (para calcar debajo del trazo); null si no hay. */
    val calcoUri: String? = null,
    /** La imagen de calco se muestra (se puede alternar en cualquier momento). */
    val calcoVisible: Boolean = false,
    /** Opacidad de la imagen de calco (0..1). */
    val calcoAlfa: Float = 0.45f,
    /** Desplazamiento del calco respecto al centro de la hoja (px de hoja). */
    val calcoOffsetX: Float = 0f,
    val calcoOffsetY: Float = 0f,
    /** Escala del calco sobre su ajuste automático (1 = ajustado a la hoja). */
    val calcoEscala: Float = 1f,
    /** Se están ajustando la posición y el tamaño del calco con los gestos. */
    val calcoEnEdicion: Boolean = false,
    /** Backend gráfico elegido por el usuario (persistido). */
    val backendSeleccionado: BackendRender = BackendRender.AUTOMATICO,
    /** Backend que realmente corre según la selección y el soporte del equipo. */
    val backendEfectivo: BackendRender = BackendRender.OPENGL,
    /** El equipo soporta Vulkan 1.1 (habilita esa opción en configuración). */
    val vulkanSoportado: Boolean = false,
    /** Versión de OpenGL ES del equipo (informativo). */
    val openglEsVersion: String = "2.0",
    /** El motor expone un handle nativo compartible con el render GPU. */
    val gpuDisponible: Boolean = false,
    /** El render GPU está en uso (si no, se pinta por CPU con Compose). */
    val gpuActivo: Boolean = false,
    /** El render GPU se está creando/creando de nuevo: pantalla de carga. */
    val gpuCargando: Boolean = false,
    /** Descripción del renderizador GPU en uso (diagnóstico). */
    val gpuDescripcion: String = "",
    /** El intento de GPU falló: se cae al render por CPU. */
    val gpuFallo: Boolean = false,
    /** Se está limpiando los trazos repasados (operación pesada). */
    val limpiando: Boolean = false,
    /** El modelo IA de limpieza (LiteRT) está empaquetado y disponible. */
    val modeloIaDisponible: Boolean = false,
    /** Colores dominantes extraídos de la imagen de calco (paleta sugerida). */
    val coloresCalco: List<Int> = emptyList(),
    /** Se está analizando la imagen de calco para sacar su paleta. */
    val analizandoCalco: Boolean = false,
    /** Hay cambios sin guardar desde el último autoguardado o "Guardar". */
    val modificado: Boolean = false,
    /** Se está recortando o redimensionando el lienzo (operación pesada). */
    val ajustandoLienzo: Boolean = false,
    /**
     * Ajuste libre del dibujo: un dedo lo mueve y dos lo escalan, como el
     * calco reposicionable. La vista (cámara) hace de vista previa y al tocar
     * "Listo" el desplazamiento/escala se hornea en la hoja (una sola vez,
     * deshacible).
     */
    val ajustandoDibujo: Boolean = false,
    /**
     * Textos vivos de la hoja (no horneados): se mueven y escalan como el
     * calco y se pueden volver a editar en cualquier momento. Al exportar se
     * hornean en la imagen; al guardar en la app viajan en un JSON aparte.
     */
    val textos: List<TextoEnHoja> = emptyList(),
    /** Texto seleccionado (id) y si los gestos lo mueven/escalan. */
    val textoSeleccionado: Long? = null,
    val textoEnEdicion: Boolean = false,
    /**
     * Trazos de la pluma vectorial (vivos, no horneados). Se pueden mover,
     * escalar y borrar como objetos y se hornean recién al exportar o al
     * rehacer la hoja.
     */
    val vectores: List<TrazoVectorial> = emptyList(),
    /** Vector seleccionado (id) y si los gestos lo mueven/escalan. */
    val vectorSeleccionado: Long? = null,
    val vectorEnEdicion: Boolean = false,
    /** Trazo vectorial que se está dibujando ahora mismo (vista previa). */
    val trazoVectorialEnCurso: TrazoVectorial? = null,
    /** Últimos colores elegidos (paleta, cuentagotas o rueda HSV), más nuevos primero. */
    val coloresRecientes: List<Int> = emptyList(),
    /** El cuentagotas está activo: el próximo toque de la hoja toma su color. */
    val cuentagotasActivo: Boolean = false,
    /** Trazo asistido: las líneas dibujadas a mano se enderezan al soltar. */
    val asistenciaRecta: Boolean = false,
)

/**
 * ViewModel de la app de dibujo: dueño del [MotorDibujo].
 *
 * El motor vive fuera de la composición, así la hoja sobrevive rotaciones y
 * cambios de configuración. Todos los eventos táctiles llegan en píxeles de
 * pantalla y se convierten a coordenadas de hoja con la cámara del motor:
 * hoja = (pantalla + pan) / zoom, la misma transformación de
 * `MotorDibujo.renderizar`.
 */
class DibujoViewModel(app: Application) : AndroidViewModel(app) {

    private val capacidades = CapacidadesGraficas(app)
    private val aplicacion: Application = app
    private val preferencias =
        app.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    private var motor: MotorDibujo? = null
    private var vistaAncho = 0
    private var vistaAlto = 0
    private var guardandoActual = false
    /** Cámara al entrar al ajuste libre: es la referencia a hornear en "Listo". */
    private var camaraBaseAjuste: Camara? = null
    private var siguienteIdTexto = 1L
    private var siguienteIdVector = 1L
    private val seleccionInicial = leerBackend()
    private val _estado = MutableStateFlow(
        DibujoEstado(
            backendSeleccionado = seleccionInicial,
            backendEfectivo = seleccionInicial.resolver(capacidades.vulkanSoportado),
            vulkanSoportado = capacidades.vulkanSoportado,
            openglEsVersion = capacidades.openglEsVersion,
        ),
    )
    val estado: StateFlow<DibujoEstado> = _estado.asStateFlow()

    /** Crea el motor una sola vez con el tamaño físico de la pantalla (px). */
    fun inicializar(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0) return
        vistaAncho = vw
        vistaAlto = vh
        val actual = motor
        if (actual == null) {
            val m = Motores.crear(vw.coerceIn(1, MAX_LADO), vh.coerceIn(1, MAX_LADO))
            m.configurarLapiz(_estado.value.lapiz)
            m.asistenciaRecta(_estado.value.asistenciaRecta)
            motor = m
            publicarContenido(m)
        } else if (_estado.value.motorNombre.isEmpty()) {
            // El motor ya lo creó una carga hecha antes de que la pantalla de
            // dibujo se midiera (al abrir un dibujo desde el menú): se publica
            // ahora, cuando ya se conocen las dimensiones de la vista.
            publicarContenido(actual)
        }
    }

    /**
     * El motor ya está listo (creado o cargado): refleja sus datos en el
     * estado y arranca la pantalla de carga si hay render GPU. Se usa tanto al
     * crear el motor en blanco como al abrir un dibujo guardado. [modificado]
     * indica si el contenido debe considerarse pendiente de autoguardado: al
     * abrir un dibujo guardado, sí (pasa a ser el borrador en curso).
     */
    private fun publicarContenido(m: MotorDibujo, modificado: Boolean = false) {
        val hayGpu = m.handleNativo != 0L
        _estado.update {
            it.copy(
                motorNombre = m.nombre,
                hojaAncho = m.ancho,
                hojaAlto = m.alto,
                // El motor recién creado/abierto arranca con la cámara centrada:
                // se refleja en el estado para que la vista previa y botones como
                // "enderezar" no queden con la cámara del dibujo anterior.
                camara = m.camara(),
                modeloIaDisponible = LimpiadorTrazosIa.disponible(aplicacion),
                gpuDisponible = hayGpu,
                gpuCargando = hayGpu && !it.gpuFallo,
                trazoEnCurso = null,
                trazoVectorialEnCurso = null,
                puedeDeshacer = false,
                puedeRehacer = false,
                modificado = modificado,
                marco = it.marco + 1,
            )
        }
    }

    /** ¿Ya se creó el motor (hay una hoja en memoria)? */
    fun motorListo(): Boolean = motor != null

    /**
     * Abre un dibujo guardado: reemplaza la hoja por [pixeles] (de tamaño
     * [ancho] × [alto]). Siempre se recrea el motor con el tamaño guardado (el
     * render GPU se reasigna al nuevo). El historial arranca limpio, también
     * cuando el tamaño coincide con el del motor actual: la hoja abierta es el
     * nuevo punto de partida y deshacer/rehacer no deben volver al dibujo
     * anterior.
     */
    fun abrirDibujo(
        ancho: Int,
        alto: Int,
        pixeles: IntArray,
        textos: List<TextoEnHoja> = emptyList(),
        vectores: List<TrazoVectorial> = emptyList(),
    ) {
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return
        motor?.close()
        val nuevo = Motores.crear(ancho.coerceIn(1, MAX_LADO), alto.coerceIn(1, MAX_LADO))
        nuevo.configurarLapiz(_estado.value.lapiz)
        nuevo.asistenciaRecta(_estado.value.asistenciaRecta)
        nuevo.reemplazarHoja(pixeles)
        motor = nuevo
        siguienteIdTexto = (textos.maxOfOrNull { it.id } ?: 0L) + 1L
        siguienteIdVector = (vectores.maxOfOrNull { it.id } ?: 0L) + 1L
        _estado.update {
            it.copy(
                textos = textos,
                textoSeleccionado = null,
                textoEnEdicion = false,
                vectores = vectores,
                vectorSeleccionado = null,
                vectorEnEdicion = false,
                trazoVectorialEnCurso = null,
            )
        }
        publicarContenido(nuevo, modificado = true)
    }

    /**
     * Autoguarda el borrador en curso (slot "actual") si hubo cambios, para que
     * "Continuar dibujando" lo reanude. La copia se hace en el hilo que llama
     * (la hoja vive en el motor y no debe leerse tras cerrarlo); la compresión
     * PNG, que es lo pesado, va fuera del hilo principal.
     */
    fun autoguardarActual() {
        val m = motor ?: return
        val estado = _estado.value
        if (!estado.modificado || estado.hojaAncho <= 0 || estado.hojaAlto <= 0) return
        if (guardandoActual) return
        val ancho = m.ancho
        val alto = m.alto
        val pixeles = IntArray(ancho * alto)
        if (!m.copiarHoja(pixeles)) return
        val textos = estado.textos
        val vectores = estado.vectores
        guardandoActual = true
        // Se marca como guardado ya: si el usuario dibuja mientras se escribe,
        // esas marcas lo vuelven a poner en "modificado" y no se pierden.
        _estado.update { it.copy(modificado = false) }
        viewModelScope.launch {
            val guardado = withContext(Dispatchers.IO) {
                AlmacenDibujos.guardarActual(aplicacion, ancho, alto, pixeles, textos, vectores)
            }
            guardandoActual = false
            if (!guardado) _estado.update { it.copy(modificado = true) }
        }
    }

    /** Marca el dibujo como guardado (tras una copia explícita en la app). */
    fun marcarGuardado() {
        _estado.update { it.copy(modificado = false) }
    }

    /**
     * Empieza un dibujo **desde cero**: descarta el motor en curso para que la
     * próxima vez que se muestre la pantalla de dibujo se cree una hoja en
     * blanco con el tamaño por defecto (el de la vista). Así no se arrastra ni
     * el tamaño de la hoja anterior (expandida o redimensionada) ni su
     * historial de deshacer/rehacer. También borra el autoguardado, para que
     * "Continuar dibujando" no reanude el dibujo anterior. Las copias de la
     * biblioteca no se tocan.
     */
    fun nuevoDibujo() {
        motor?.close()
        motor = null
        _estado.update {
            it.copy(
                motorNombre = "",
                trazoEnCurso = null,
                puedeDeshacer = false,
                puedeRehacer = false,
                modificado = false,
                camara = Camara(0f, 0f, 1f),
                textos = emptyList(),
                textoSeleccionado = null,
                textoEnEdicion = false,
                vectores = emptyList(),
                vectorSeleccionado = null,
                vectorEnEdicion = false,
                trazoVectorialEnCurso = null,
                marco = it.marco + 1,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { AlmacenDibujos.borrarActual(aplicacion) }
    }

    /** Handle nativo del motor para el render GPU (0 si no aplica). */
    fun handleMotor(): Long = motor?.handleNativo ?: 0L

    /** El render GPU arrancó: se pinta por GPU y se registra su descripción. */
    fun gpuListo(descripcion: String) {
        _estado.update {
            it.copy(
                gpuActivo = true,
                gpuDescripcion = descripcion,
                gpuFallo = false,
                gpuCargando = false,
            )
        }
    }

    /** El render GPU falló: se cae al render por CPU con Compose. */
    fun gpuFracaso() {
        _estado.update {
            it.copy(
                gpuActivo = false,
                gpuDescripcion = "",
                gpuFallo = true,
                gpuCargando = false,
            )
        }
    }

    /**
     * Elige el backend gráfico y lo persiste. Una opción no disponible en el
     * equipo (Vulkan sin soporte) se ignora; la UI ya la muestra bloqueada.
     */
    fun elegirBackend(backend: BackendRender) {
        if (!backend.disponible(capacidades.vulkanSoportado)) return
        val efectivo = backend.resolver(capacidades.vulkanSoportado)
        val actual = _estado.value
        if (backend == actual.backendSeleccionado &&
            efectivo == actual.backendEfectivo &&
            !actual.gpuFallo
        ) {
            return
        }
        preferencias.edit().putString(CLAVE_BACKEND, backend.name).apply()
        // El renderizador se recrea si cambia el backend EFECTIVO (p. ej. pasar
        // de Automático a OpenGL sigue usando OpenGL) o si el intento anterior
        // había fallado (se reintenta la GPU). En cambio, si todo sigue igual, no
        // hay nada que recrear ni pantalla de carga que mostrar.
        val cambiaRender = efectivo != actual.backendEfectivo || actual.gpuFallo
        _estado.update {
            it.copy(
                backendSeleccionado = backend,
                backendEfectivo = efectivo,
                gpuActivo = if (cambiaRender) false else it.gpuActivo,
                gpuDescripcion = if (cambiaRender) "" else it.gpuDescripcion,
                gpuFallo = if (cambiaRender) false else it.gpuFallo,
                gpuCargando = if (cambiaRender) it.gpuDisponible else it.gpuCargando,
            )
        }
    }


    fun iniciarTrazo(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.EDICION) return
        val (hx, hy) = aHoja(m, px, py)
        m.iniciarTrazo(hx, hy)
        _estado.update { it.copy(trazoEnCurso = listOf(hx, hy), marco = it.marco + 1) }
    }

    fun continuarTrazo(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.trazoEnCurso == null) return
        val (hx, hy) = aHoja(m, px, py)
        m.continuarTrazo(hx, hy)
        _estado.update {
            it.copy(
                trazoEnCurso = it.trazoEnCurso.orEmpty() + hx + hy,
                marco = it.marco + 1,
            )
        }
    }

    fun soltarTrazo() {
        val m = motor ?: return
        m.terminarTrazo()
        _estado.update {
            it.copy(
                trazoEnCurso = null,
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    fun cancelarTrazo() {
        val m = motor ?: return
        m.cancelarTrazo()
        _estado.update { it.copy(trazoEnCurso = null, marco = it.marco + 1) }
    }

    /** Pan con el dedo: el contenido sigue al dedo (dx, dy en px de pantalla). */
    fun panear(dx: Float, dy: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER && !_estado.value.ajustandoDibujo) return
        m.pan(-dx, -dy)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /**
     * Pinch de zoom anclado al punto donde están los dedos: la hoja que está
     * bajo el foco no se mueve. La transformación incluye la rotación:
     * `hoja = R(−rot)·(pantalla + pan)/zoom` y
     * `pantalla = R(rot)·(hoja·zoom) − pan`.
     */
    fun zoomear(factor: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER && !_estado.value.ajustandoDibujo) return
        val cam = m.camara()
        val hf = pantallaAUnrotado(cam, focoX, focoY)
        m.zoom(factor)
        anclarFoco(m, hf.first, hf.second, focoX, focoY)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /**
     * Gira la hoja [delta] radianes anclado a [focoX]/[focoY]: el punto de la
     * hoja bajo los dedos queda quieto. Es el gesto de rotación de dos dedos
     * (modo MOVER), como en las apps de dibujo profesionales.
     */
    fun rotar(delta: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.MOVER) return
        val cam = m.camara()
        val hf = pantallaAUnrotado(cam, focoX, focoY)
        m.rotar(delta)
        anclarFoco(m, hf.first, hf.second, focoX, focoY)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /** Endereza la hoja (rotación 0) sin tocar el zoom, anclado al centro. */
    fun enderezar() {
        asegurarSinAjuste()
        val m = motor ?: return
        val cam = m.camara()
        if (cam.rotacion == 0f) return
        val cx = vistaAncho / 2f
        val cy = vistaAlto / 2f
        val hf = pantallaAUnrotado(cam, cx, cy)
        m.rotar(-cam.rotacion)
        anclarFoco(m, hf.first, hf.second, cx, cy)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /**
     * Elige un color de la paleta, del calco o del cuentagotas: lo aplica al
     * lápiz y lo guarda al frente de los colores recientes.
     */
    fun elegirColor(argb: Int) {
        actualizarLapiz { it.copy(color = argb) }
        registrarReciente(argb)
    }

    /**
     * Aplica un color de la rueda HSV mientras se arrastra (vista previa), sin
     * ensuciar la lista de recientes: recién [confirmarColorHsv] lo registra.
     */
    fun previsualizarColorHsv(argb: Int) = actualizarLapiz { it.copy(color = argb) }

    /** Confirma el color elegido en la rueda HSV y lo guarda en recientes. */
    fun confirmarColorHsv(argb: Int) {
        actualizarLapiz { it.copy(color = argb) }
        registrarReciente(argb)
    }

    private fun registrarReciente(argb: Int) {
        _estado.update {
            val lista = (listOf(argb) + it.coloresRecientes.filter { c -> c != argb })
                .take(MAX_RECIENTES)
            it.copy(coloresRecientes = lista)
        }
    }

    /** Activa/desactiva el cuentagotas: el próximo toque de la hoja toma su color. */
    fun alternarCuentagotas() {
        _estado.update { it.copy(cuentagotasActivo = !it.cuentagotasActivo) }
    }

    /**
     * Cuentagotas: lee el color del píxel de la hoja tocado (en coordenadas de
     * hoja), lo aplica al lápiz, lo guarda en recientes y desactiva el
     * cuentagotas (un toque = un color). Fuera de la hoja no hace nada.
     */
    fun recogerColor(px: Float, py: Float) {
        val m = motor ?: return
        val (hx, hy) = aHoja(m, px, py)
        val ix = kotlin.math.floor(hx.toDouble()).toInt()
        val iy = kotlin.math.floor(hy.toDouble()).toInt()
        if (ix !in 0 until m.ancho || iy !in 0 until m.alto) return
        val color = m.pixelHoja(ix, iy)
        actualizarLapiz { it.copy(color = color) }
        _estado.update { it.copy(cuentagotasActivo = false) }
        registrarReciente(color)
    }

    /** Activa/desactiva el trazo asistido (las líneas se enderezan al soltar). */
    fun alternarAsistenciaRecta() {
        val m = motor ?: return
        val nuevo = !_estado.value.asistenciaRecta
        m.asistenciaRecta(nuevo)
        _estado.update { it.copy(asistenciaRecta = nuevo) }
    }

    fun elegirHerramienta(herramienta: Herramienta) =
        actualizarLapiz { it.copy(herramienta = herramienta) }

    /** Elige la textura del trazo (lisa, grano, rugosa, trama o salpicada). */
    fun elegirTextura(textura: com.dibujo.core.Textura) =
        actualizarLapiz { it.copy(textura = textura) }

    /**
     * Inserta [texto] en el centro de la vista actual con la [tipografia] y el
     * tamaño [tamano] px. El texto queda como objeto vivo (no se hornea): se
     * puede mover y escalar con los gestos, y editar en cualquier momento. Al
     * elegir el texto se entra en modo edición, como con el calco.
     */
    fun insertarTexto(texto: String, tipografia: Tipografia, tamano: Float) {
        if (texto.isBlank()) return
        asegurarSinAjuste()
        val m = motor ?: return
        if (_estado.value.ajustandoLienzo) return
        // Centro de la vista en coordenadas de hoja (donde el usuario está
        // mirando). Si la vista todavía no se midió, se usa el centro de la hoja.
        val focoX = if (vistaAncho > 0) vistaAncho / 2f else m.ancho / 2f
        val focoY = if (vistaAlto > 0) vistaAlto / 2f else m.alto / 2f
        val (cx, cy) = pantallaAUnrotado(m.camara(), focoX, focoY)
        val id = siguienteIdTexto++
        val objeto = TextoEnHoja(
            id = id,
            texto = texto,
            tipografia = tipografia,
            tamano = tamano,
            color = _estado.value.lapiz.color,
            centroX = cx,
            centroY = cy,
        )
        _estado.update {
            it.copy(
                textos = it.textos + objeto,
                textoSeleccionado = id,
                textoEnEdicion = true,
                calcoEnEdicion = false,
                ajustandoDibujo = false,
                vectorSeleccionado = null,
                vectorEnEdicion = false,
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    /** Vuelve a editar el texto seleccionado sin moverlo ni cambiar su escala. */
    fun actualizarTextoSeleccionado(texto: String, tipografia: Tipografia, tamano: Float) {
        if (texto.isBlank()) return
        _estado.update { est ->
            val id = est.textoSeleccionado ?: return@update est
            val nuevos = est.textos.map { actual ->
                if (actual.id == id) {
                    actual.copy(texto = texto, tipografia = tipografia, tamano = tamano)
                } else {
                    actual
                }
            }
            est.copy(textos = nuevos, modificado = true, marco = est.marco + 1)
        }
    }

    /** Selecciona un texto existente y lo pone en edición (mover/escalar). */
    fun seleccionarTexto(id: Long) {
        if (_estado.value.textos.none { it.id == id }) return
        asegurarSinAjuste()
        _estado.update {
            it.copy(
                textoSeleccionado = id,
                textoEnEdicion = true,
                calcoEnEdicion = false,
                vectorSeleccionado = null,
                vectorEnEdicion = false,
                marco = it.marco + 1,
            )
        }
    }

    /** Borra el texto seleccionado (no toca los trazos de la hoja). */
    fun quitarTextoSeleccionado() {
        _estado.update { est ->
            val id = est.textoSeleccionado ?: return@update est
            est.copy(
                textos = est.textos.filterNot { it.id == id },
                textoSeleccionado = null,
                textoEnEdicion = false,
                modificado = true,
                marco = est.marco + 1,
            )
        }
    }

    /** Sale del ajuste del texto: queda dibujado pero ya no sigue a los gestos. */
    fun terminarEdicionTexto() {
        if (!_estado.value.textoEnEdicion) return
        _estado.update {
            it.copy(
                textoEnEdicion = false,
                textoSeleccionado = null,
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Mueve el texto seleccionado [dxPantalla]/[dyPantalla] píxeles de pantalla,
     * pasando el delta a coordenadas de hoja (rotación incluida), igual que el
     * calco: queda anclado a la hoja y no a la pantalla.
     */
    fun moverTexto(dxPantalla: Float, dyPantalla: Float) {
        val m = motor ?: return
        if (!_estado.value.textoEnEdicion) return
        val cam = m.camara()
        val c = kotlin.math.cos(cam.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam.rotacion.toDouble()).toFloat()
        val hx = (c * dxPantalla + s * dyPantalla) / cam.zoom
        val hy = (-s * dxPantalla + c * dyPantalla) / cam.zoom
        _estado.update { est ->
            val id = est.textoSeleccionado ?: return@update est
            est.copy(
                textos = est.textos.map {
                    if (it.id == id) {
                        it.copy(centroX = it.centroX + hx, centroY = it.centroY + hy)
                    } else {
                        it
                    }
                },
                modificado = true,
                marco = est.marco + 1,
            )
        }
    }

    /**
     * Escala el texto seleccionado [factor] anclado al foco de pantalla: el
     * punto de la hoja bajo los dedos queda quieto, como en el calco.
     */
    fun escalarTexto(factor: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (!_estado.value.textoEnEdicion || factor <= 0f) return
        val foco = aHoja(m, focoX, focoY)
        _estado.update { est ->
            val id = est.textoSeleccionado ?: return@update est
            est.copy(
                textos = est.textos.map { actual ->
                    if (actual.id != id) return@map actual
                    val vieja = actual.escala
                    val nueva = (vieja * factor).coerceIn(TEXTO_ESCALA_MIN, TEXTO_ESCALA_MAX)
                    val ratio = if (vieja > 0f) nueva / vieja else 1f
                    actual.copy(
                        escala = nueva,
                        centroX = foco.first + ratio * (actual.centroX - foco.first),
                        centroY = foco.second + ratio * (actual.centroY - foco.second),
                    )
                },
                modificado = true,
                marco = est.marco + 1,
            )
        }
    }

    /**
     * Empieza un trazo de la pluma vectorial en ([px], [py]) de pantalla. El
     * trazo se ve al instante como objeto vivo; al soltar se simplifica y queda
     * editable (mover/escalar/borrar).
     */
    fun iniciarVector(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.EDICION) return
        asegurarSinAjuste()
        m.cancelarTrazo()
        val (hx, hy) = aHoja(m, px, py)
        val objeto = TrazoVectorial(
            id = 0L,
            puntos = listOf(hx, hy),
            color = _estado.value.lapiz.color,
            grosor = _estado.value.lapiz.grosor.toFloat(),
        )
        _estado.update {
            it.copy(
                trazoVectorialEnCurso = objeto,
                trazoEnCurso = null,
                textoEnEdicion = false,
                textoSeleccionado = null,
                vectorEnEdicion = false,
                vectorSeleccionado = null,
                calcoEnEdicion = false,
                marco = it.marco + 1,
            )
        }
    }

    /** Agrega un vértice al trazo vectorial en curso si el dedo se movió. */
    fun continuarVector(px: Float, py: Float) {
        val m = motor ?: return
        val actual = _estado.value.trazoVectorialEnCurso ?: return
        val (hx, hy) = aHoja(m, px, py)
        val ultimo = actual.puntos.size - 2
        val dx = hx - actual.puntos[ultimo]
        val dy = hy - actual.puntos[ultimo + 1]
        val minimo = VECTOR_DISTANCIA_MIN / m.camara().zoom.coerceAtLeast(0.01f)
        if (dx * dx + dy * dy < minimo * minimo) return
        _estado.update {
            it.copy(
                trazoVectorialEnCurso = actual.copy(puntos = actual.puntos + hx + hy),
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Termina el trazo vectorial: simplifica los puntos con la tolerancia
     * equivalente a pantalla y lo agrega a los objetos vivos de la hoja.
     */
    fun soltarVector() {
        val actual = _estado.value.trazoVectorialEnCurso ?: return
        val m = motor ?: return
        val tolerancia = VECTOR_TOLERANCIA / m.camara().zoom.coerceAtLeast(0.01f)
        val objeto = actual.copy(
            id = siguienteIdVector++,
            puntos = VectorHoja.simplificar(actual.puntos, tolerancia),
        )
        _estado.update {
            it.copy(
                vectores = it.vectores + objeto,
                trazoVectorialEnCurso = null,
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    /** Descarta el trazo vectorial en curso sin agregarlo a la hoja. */
    fun cancelarVector() {
        if (_estado.value.trazoVectorialEnCurso == null) return
        _estado.update { it.copy(trazoVectorialEnCurso = null, marco = it.marco + 1) }
    }

    /** Un toque con la pluma crea un punto (un trazo de un solo vértice). */
    fun insertarPuntoVector(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.EDICION) return
        asegurarSinAjuste()
        m.cancelarTrazo()
        val (hx, hy) = aHoja(m, px, py)
        val objeto = TrazoVectorial(
            id = siguienteIdVector++,
            puntos = listOf(hx, hy),
            color = _estado.value.lapiz.color,
            grosor = _estado.value.lapiz.grosor.toFloat(),
        )
        _estado.update {
            it.copy(
                vectores = it.vectores + objeto,
                trazoVectorialEnCurso = null,
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    /** Selecciona un vector para moverlo/escalarlo con los gestos. */
    fun seleccionarVector(id: Long) {
        if (_estado.value.vectores.none { it.id == id }) return
        asegurarSinAjuste()
        _estado.update {
            it.copy(
                vectorSeleccionado = id,
                vectorEnEdicion = true,
                textoSeleccionado = null,
                textoEnEdicion = false,
                calcoEnEdicion = false,
                trazoVectorialEnCurso = null,
                marco = it.marco + 1,
            )
        }
    }

    /** Sale del ajuste del vector: queda dibujado pero ya no sigue a los gestos. */
    fun terminarEdicionVector() {
        if (!_estado.value.vectorEnEdicion) return
        _estado.update {
            it.copy(
                vectorEnEdicion = false,
                vectorSeleccionado = null,
                marco = it.marco + 1,
            )
        }
    }

    /** Borra el vector seleccionado (no toca los píxeles del dibujo). */
    fun quitarVectorSeleccionado() {
        _estado.update { est ->
            val id = est.vectorSeleccionado ?: return@update est
            est.copy(
                vectores = est.vectores.filterNot { it.id == id },
                vectorSeleccionado = null,
                vectorEnEdicion = false,
                modificado = true,
                marco = est.marco + 1,
            )
        }
    }

    /**
     * Mueve el vector seleccionado [dxPantalla]/[dyPantalla] píxeles de
     * pantalla, pasando el delta a coordenadas de hoja (rotación incluida).
     */
    fun moverVector(dxPantalla: Float, dyPantalla: Float) {
        val m = motor ?: return
        if (!_estado.value.vectorEnEdicion) return
        val cam = m.camara()
        val c = kotlin.math.cos(cam.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam.rotacion.toDouble()).toFloat()
        val hx = (c * dxPantalla + s * dyPantalla) / cam.zoom
        val hy = (-s * dxPantalla + c * dyPantalla) / cam.zoom
        _estado.update { est ->
            val id = est.vectorSeleccionado ?: return@update est
            est.copy(
                vectores = est.vectores.map { actual ->
                    if (actual.id != id) return@map actual
                    val puntos = ArrayList<Float>(actual.puntos.size)
                    var i = 0
                    while (i + 1 < actual.puntos.size) {
                        puntos.add(actual.puntos[i] + hx)
                        puntos.add(actual.puntos[i + 1] + hy)
                        i += 2
                    }
                    actual.copy(puntos = puntos)
                },
                modificado = true,
                marco = est.marco + 1,
            )
        }
    }

    /**
     * Escala el vector seleccionado [factor] anclado al foco de pantalla. Los
     * puntos y el grosor se escalan juntos, así el trazo mantiene su aspecto.
     */
    fun escalarVector(factor: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (!_estado.value.vectorEnEdicion || factor <= 0f || !factor.isFinite()) return
        val foco = aHoja(m, focoX, focoY)
        _estado.update { est ->
            val id = est.vectorSeleccionado ?: return@update est
            est.copy(
                vectores = est.vectores.map { actual ->
                    if (actual.id != id) return@map actual
                    val puntos = ArrayList<Float>(actual.puntos.size)
                    var i = 0
                    while (i + 1 < actual.puntos.size) {
                        puntos.add(foco.first + factor * (actual.puntos[i] - foco.first))
                        puntos.add(foco.second + factor * (actual.puntos[i + 1] - foco.second))
                        i += 2
                    }
                    actual.copy(
                        puntos = puntos,
                        grosor = (actual.grosor * factor)
                            .coerceIn(VECTOR_GROSOR_MIN, VECTOR_GROSOR_MAX),
                    )
                },
                modificado = true,
                marco = est.marco + 1,
            )
        }
    }

    /**
     * Id del vector tocado en ([px], [py]) de pantalla, buscando de arriba hacia
     * abajo. La tolerancia suma el grosor del trazo y unos px de pantalla.
     */
    fun vectorEnPunto(px: Float, py: Float): Long? {
        val m = motor ?: return null
        val vectores = _estado.value.vectores
        if (vectores.isEmpty()) return null
        val (hx, hy) = aHoja(m, px, py)
        val zoom = m.camara().zoom.coerceAtLeast(0.01f)
        for (vector in vectores.asReversed()) {
            val caja = vector.limites()
            val margen = vector.grosor / 2f + VECTOR_TOLERANCIA_PUNTERO / zoom
            if (hx < caja[0] - margen || hx > caja[2] + margen ||
                hy < caja[1] - margen || hy > caja[3] + margen
            ) {
                continue
            }
            if (VectorHoja.distanciaCuadradaAlTrazo(vector, hx, hy) <= margen * margen) {
                return vector.id
            }
        }
        return null
    }

    /**
     * Índice del texto tocado en ([px], [py]) de pantalla, buscando de arriba
     * hacia abajo (el último insertado queda encima). `null` si no toca ninguno.
     */
    fun textoEnPunto(px: Float, py: Float): Long? {
        val m = motor ?: return null
        val textos = _estado.value.textos
        if (textos.isEmpty()) return null
        val (hx, hy) = aHoja(m, px, py)
        for (texto in textos.asReversed()) {
            val (anchoBase, altoBase) = TextoHoja.medidas(
                texto.texto,
                texto.tipografia,
                texto.tamano,
            )
            val ancho = anchoBase * texto.escala
            val alto = altoBase * texto.escala
            if (ancho <= 0 || alto <= 0) continue
            val x0 = texto.centroX - ancho / 2f
            val y0 = texto.centroY - alto / 2f
            if (hx >= x0 && hx <= x0 + ancho && hy >= y0 && hy <= y0 + alto) {
                return texto.id
            }
        }
        return null
    }

    fun cambiarGrosor(delta: Int) {
        val actual = _estado.value.lapiz.grosor
        val nuevo = (actual + delta).coerceIn(1, 64)
        if (nuevo == actual) return
        actualizarLapiz { it.copy(grosor = nuevo) }
    }

    fun alternarModo() {
        // Si se está ajustando el dibujo, primero se descarta el ajuste (la
        // vista vuelve a su sitio) y recién ahí se cambia de modo.
        asegurarSinAjuste()
        val m = motor ?: return
        m.cancelarTrazo()
        val nuevo = if (_estado.value.modo == Modo.EDICION) Modo.MOVER else Modo.EDICION
        _estado.update {
            it.copy(
                modo = nuevo,
                trazoEnCurso = null,
                // Cambiar de modo sale del ajuste del calco: no tiene sentido
                // editar el calco y mover la hoja a la vez.
                calcoEnEdicion = false,
                textoEnEdicion = false,
                textoSeleccionado = null,
                vectorEnEdicion = false,
                vectorSeleccionado = null,
                trazoVectorialEnCurso = null,
                marco = it.marco + 1,
            )
        }
    }

    fun deshacer() {
        asegurarSinAjuste()
        val m = motor ?: return
        m.deshacer()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    fun rehacer() {
        asegurarSinAjuste()
        val m = motor ?: return
        m.rehacer()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    fun limpiar() {
        asegurarSinAjuste()
        val m = motor ?: return
        m.limpiarHoja()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                textos = emptyList(),
                textoSeleccionado = null,
                textoEnEdicion = false,
                vectores = emptyList(),
                vectorSeleccionado = null,
                vectorEnEdicion = false,
                trazoVectorialEnCurso = null,
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    fun centrar() {
        asegurarSinAjuste()
        val m = motor ?: return
        m.centrarVista()
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /**
     * "Limpiar trazos": adelgaza los trazos repasados a una línea pareja.
     * Recorre la hoja entera, así que corre fuera del hilo principal y deja el
     * resultado en el historial (se puede deshacer).
     */
    fun limpiarTrazos() {
        asegurarSinAjuste()
        val m = motor ?: return
        if (_estado.value.limpiando) return
        _estado.update { it.copy(limpiando = true) }
        viewModelScope.launch {
            val cambio = withContext(Dispatchers.Default) { m.limpiarTrazos() }
            _estado.update {
                it.copy(
                    limpiando = false,
                    puedeDeshacer = m.puedeDeshacer(),
                    puedeRehacer = m.puedeRehacer(),
                    modificado = it.modificado || cambio,
                    marco = if (cambio) it.marco + 1 else it.marco,
                )
            }
        }
    }

    /**
     * "Limpiar trazos" con el modelo IA (LiteRT): copia la hoja, la limpia con
     * la red chica fuera del hilo principal, la reemplaza (queda en el
     * historial para deshacer) y refresca el marco. Si el modelo no está
     * disponible o falla, cae al adelgazado algorítmico (`limpiarTrazos`).
     */
    fun limpiarTrazosConModelo() {
        asegurarSinAjuste()
        val m = motor ?: return
        if (_estado.value.limpiando) return
        _estado.update { it.copy(limpiando = true) }
        viewModelScope.launch {
            val ancho = m.ancho
            val alto = m.alto
            val hoja = IntArray(ancho * alto)
            val leida = withContext(Dispatchers.Default) { m.copiarHoja(hoja) }
            val resultado = if (leida) {
                withContext(Dispatchers.Default) {
                    LimpiadorTrazosIa.limpiar(aplicacion, ancho, alto, Colores.BLANCO, hoja)
                }
            } else {
                null
            }
            // Sin resultado válido se usa el algoritmo determinista. El reemplazo
            // de la hoja se hace en el hilo del ViewModel (mutación del motor).
            val cambio = if (resultado != null) {
                m.reemplazarHoja(resultado)
            } else {
                withContext(Dispatchers.Default) { m.limpiarTrazos() }
            }
            _estado.update {
                it.copy(
                    limpiando = false,
                    puedeDeshacer = m.puedeDeshacer(),
                    puedeRehacer = m.puedeRehacer(),
                    modificado = it.modificado || cambio,
                    marco = if (cambio) it.marco + 1 else it.marco,
                )
            }
        }
    }

    /**
     * Analiza la imagen de calco y extrae sus colores dominantes con k-medias
     * local (sin red neuronal ni dependencias). Los colores quedan en el estado
     * para que el usuario los use al colorear.
     */
    fun analizarCalco() {
        val ruta = _estado.value.calcoUri ?: return
        if (_estado.value.analizandoCalco) return
        _estado.update { it.copy(analizandoCalco = true) }
        viewModelScope.launch {
            val colores = withContext(Dispatchers.Default) {
                runCatching {
                    val bmp = cargarBitmap(aplicacion, Uri.parse(ruta))
                        ?: return@runCatching emptyList<Int>()
                    val muestras = muestrearCalco(bmp)
                    bmp.recycle()
                    Cuantizador.paleta(muestras, K_COLORES_CALCO)
                }.getOrDefault(emptyList())
            }
            _estado.update { it.copy(coloresCalco = colores, analizandoCalco = false) }
        }
    }

    /** Usa un color sugerido del calco y cambia a la herramienta PINTAR. */
    fun usarColorCalco(argb: Int) {
        actualizarLapiz { it.copy(color = argb, herramienta = Herramienta.PINTAR) }
    }

    /** Toma una grilla de muestras del bitmap del calco, sin fondo ni alfa. */
    private fun muestrearCalco(bmp: Bitmap): IntArray {
        val ancho = bmp.width
        val alto = bmp.height
        if (ancho <= 0 || alto <= 0) return IntArray(0)
        val pasoX = (ancho / MUESTRAS_LADO).coerceAtLeast(1)
        val pasoY = (alto / MUESTRAS_LADO).coerceAtLeast(1)
        val salida = ArrayList<Int>(MUESTRAS_LADO * MUESTRAS_LADO)
        var y = 0
        while (y < alto) {
            var x = 0
            while (x < ancho) {
                val c = bmp.getPixel(x, y)
                val alfa = c ushr 24
                // Descarta transparencias y el fondo casi blanco del calco.
                if (alfa >= 16 && luminancia(c) < 245) salida.add(c)
                x += pasoX
            }
            y += pasoY
        }
        return salida.toIntArray()
    }

    private fun luminancia(c: Int): Int {
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * Expande la hoja agregando [extraAncho] px a la derecha y [extraAlto] px
     * abajo (el contenido no se mueve). Al cambiar el tamaño, los motores
     * limpian el historial. Si hay render GPU, se recrea para reasignar la
     * textura al nuevo tamaño (por eso se enciende `gpuCargando`).
     */
    fun expandirHoja(extraAncho: Int, extraAlto: Int) {
        asegurarSinAjuste()
        val m = motor ?: return
        val dAncho = extraAncho.coerceAtLeast(0)
        val dAlto = extraAlto.coerceAtLeast(0)
        if (dAncho == 0 && dAlto == 0) return
        m.expandir(0, 0, dAncho, dAlto)
        _estado.update {
            it.copy(
                hojaAncho = m.ancho,
                hojaAlto = m.alto,
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                modificado = true,
                // Sólo se muestra la pantalla de carga si el render GPU se va a
                // recrear (si falló, se sigue por CPU y no hay nada que esperar).
                gpuCargando = it.gpuDisponible && !it.gpuFallo,
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Transforma el dibujo dentro de la misma hoja: lo voltea
     * (horizontal/vertical), lo mueve [desplazamientoX]/[desplazamientoY] px de
     * hoja y lo escala [escala] alrededor del centro. Recorre la hoja completa,
     * así que corre fuera del hilo principal y deja el resultado en el
     * historial (se puede deshacer).
     */
    fun transformarDibujo(
        voltearHorizontal: Boolean,
        voltearVertical: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ) {
        asegurarSinAjuste()
        val m = motor ?: return
        if (_estado.value.ajustandoLienzo) return
        val cambia = voltearHorizontal || voltearVertical ||
            desplazamientoX != 0f || desplazamientoY != 0f || escala != 1f
        if (!cambia || escala <= 0f) return
        _estado.update { it.copy(ajustandoLienzo = true) }
        viewModelScope.launch {
            val cambio = withContext(Dispatchers.Default) {
                m.transformarHoja(voltearHorizontal, voltearVertical, desplazamientoX, desplazamientoY, escala)
            }
            _estado.update {
                it.copy(
                    ajustandoLienzo = false,
                    puedeDeshacer = m.puedeDeshacer(),
                    puedeRehacer = m.puedeRehacer(),
                    textos = if (cambio) {
                        transformarTextos(
                            it.textos,
                            m.ancho,
                            m.alto,
                            voltearHorizontal,
                            voltearVertical,
                            desplazamientoX,
                            desplazamientoY,
                            escala,
                        )
                    } else {
                        it.textos
                    },
                    vectores = if (cambio) {
                        transformarVectores(
                            it.vectores,
                            m.ancho,
                            m.alto,
                            voltearHorizontal,
                            voltearVertical,
                            desplazamientoX,
                            desplazamientoY,
                            escala,
                        )
                    } else {
                        it.vectores
                    },
                    modificado = it.modificado || cambio,
                    marco = if (cambio) it.marco + 1 else it.marco,
                )
            }
        }
    }

    /**
     * Entra al **ajuste libre del dibujo**: los gestos mueven (un dedo) y
     * escalan (dos dedos) la vista, que hace de vista previa; al tocar "Listo"
     * ([terminarAjusteDibujo]) ese movimiento se hornea en la hoja con una sola
     * entrada de historial. Es el equivalente a acomodar el calco, pero sobre
     * el dibujo mismo. No rota (el giro de la vista se conserva tal cual).
     */
    fun iniciarAjusteDibujo() {
        val m = motor ?: return
        if (_estado.value.ajustandoLienzo) return
        m.cancelarTrazo()
        camaraBaseAjuste = m.camara()
        _estado.update {
            it.copy(
                ajustandoDibujo = true,
                calcoEnEdicion = false,
                textoEnEdicion = false,
                textoSeleccionado = null,
                vectorEnEdicion = false,
                vectorSeleccionado = null,
                trazoEnCurso = null,
                trazoVectorialEnCurso = null,
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Termina el ajuste libre: convierte el cambio de cámara desde que se
     * empezó (pan y zoom) en una transformación de la hoja y la aplica. La
     * vista se restaura a su estado inicial para que el resultado no salte.
     *
     * Sea `z0, pan0` la cámara al empezar y `z1, pan1` la actual, la relación
     * `hoja = R(−rot)·(pantalla + pan)/zoom` da
     * `escala = z1/z0` y
     * `desplazamiento = R(−rot)·(pan0 − pan1)/z0 + (escala − 1)·centro`,
     * que es justo lo que espera `transformarHoja` (escala alrededor del
     * centro `((ancho−1)/2, (alto−1)/2)`).
     */
    fun terminarAjusteDibujo() {
        val m = motor ?: return
        val base = camaraBaseAjuste
        camaraBaseAjuste = null
        if (base == null) {
            _estado.update { it.copy(ajustandoDibujo = false) }
            return
        }
        val cur = m.camara()
        val escala = if (base.zoom != 0f) cur.zoom / base.zoom else 1f
        val c = kotlin.math.cos(base.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(base.rotacion.toDouble()).toFloat()
        val dxp = base.panX - cur.panX
        val dyp = base.panY - cur.panY
        val rx = (c * dxp + s * dyp) / base.zoom
        val ry = (-s * dxp + c * dyp) / base.zoom
        val centroX = (m.ancho - 1) / 2f
        val centroY = (m.alto - 1) / 2f
        val dx = rx + (escala - 1f) * centroX
        val dy = ry + (escala - 1f) * centroY
        val cambia = kotlin.math.abs(escala - 1f) > 1e-4f ||
            kotlin.math.abs(dx) > 1e-3f || kotlin.math.abs(dy) > 1e-3f
        if (!cambia || escala <= 0f) {
            restaurarCamaraAjuste(base)
            _estado.update { it.copy(ajustandoDibujo = false) }
            return
        }
        _estado.update { it.copy(ajustandoLienzo = true) }
        viewModelScope.launch {
            val cambio = withContext(Dispatchers.Default) {
                m.transformarHoja(false, false, dx, dy, escala)
            }
            restaurarCamaraAjuste(base)
            _estado.update {
                it.copy(
                    ajustandoLienzo = false,
                    ajustandoDibujo = false,
                    puedeDeshacer = m.puedeDeshacer(),
                    puedeRehacer = m.puedeRehacer(),
                    textos = if (cambio) {
                        transformarTextos(
                            it.textos,
                            m.ancho,
                            m.alto,
                            false,
                            false,
                            dx,
                            dy,
                            escala,
                        )
                    } else {
                        it.textos
                    },
                    vectores = if (cambio) {
                        transformarVectores(
                            it.vectores,
                            m.ancho,
                            m.alto,
                            false,
                            false,
                            dx,
                            dy,
                            escala,
                        )
                    } else {
                        it.vectores
                    },
                    modificado = it.modificado || cambio,
                    marco = if (cambio) it.marco + 1 else it.marco,
                )
            }
        }
    }

    /** Sale del ajuste libre sin hornear nada: la vista vuelve a como estaba. */
    fun cancelarAjusteDibujo() {
        val m = motor ?: return
        val base = camaraBaseAjuste
        camaraBaseAjuste = null
        if (base != null) restaurarCamaraAjuste(base)
        _estado.update { it.copy(ajustandoDibujo = false) }
    }

    /**
     * Cualquier acción que toque la cámara o la hoja (centrar, deshacer,
     * limpiar, otra transformación…) cancela el ajuste libre pendiente: la
     * vista vuelve a su sitio y el ajuste se descarta sin hornear.
     */
    private fun asegurarSinAjuste() {
        if (_estado.value.ajustandoDibujo) cancelarAjusteDibujo()
    }

    /** Devuelve la cámara a [base] con deltas de pan/zoom (sin exponer setCamara). */
    private fun restaurarCamaraAjuste(base: Camara) {
        val m = motor ?: return
        val cur = m.camara()
        if (cur.zoom != base.zoom && cur.zoom != 0f) m.zoom(base.zoom / cur.zoom)
        val ahora = m.camara()
        m.pan(base.panX - ahora.panX, base.panY - ahora.panY)
        if (ahora.rotacion != base.rotacion) m.rotar(base.rotacion - ahora.rotacion)
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    /**
     * Recorta el lienzo al contenido dibujado: descarta los márgenes que son
     * sólo fondo y rehace la hoja con el tamaño del dibujo. No pierde ningún
     * trazo (sólo elimina fondo, porque el recuadro es el de la tinta). Al
     * cambiar el tamaño el historial arranca limpio, igual que al expandir. Si
     * la hoja está vacía o ya está ajustada, no hace nada.
     */
    fun recortarAlDibujo() {
        procesarLienzo(recortar = true, nuevoAncho = 0, nuevoAlto = 0)
    }

    /**
     * Redimensiona el lienzo a [nuevoAncho] × [nuevoAlto] px, centrando el
     * dibujo actual: lo que no entra se recorta y lo que falta se rellena con
     * el fondo. El historial arranca limpio (los snapshots viejos tienen otras
     * dimensiones, como al expandir).
     */
    fun redimensionarLienzo(nuevoAncho: Int, nuevoAlto: Int) {
        procesarLienzo(recortar = false, nuevoAncho = nuevoAncho, nuevoAlto = nuevoAlto)
    }

    private fun procesarLienzo(recortar: Boolean, nuevoAncho: Int, nuevoAlto: Int) {
        asegurarSinAjuste()
        val m = motor ?: return
        if (_estado.value.ajustandoLienzo) return
        val ancho = m.ancho
        val alto = m.alto
        val textos = _estado.value.textos
        val vectores = _estado.value.vectores
        _estado.update { it.copy(ajustandoLienzo = true) }
        viewModelScope.launch {
            // Recortar/redimensionar rehace la hoja: los objetos vivos (textos
            // y vectores) se hornean en la copia para que entren en el recorte
            // y se muevan con el dibujo (después de esto dejan de ser
            // editables). No se toca el motor hasta tener el resultado.
            val resultado = withContext(Dispatchers.Default) {
                val pixeles = IntArray(ancho * alto)
                if (!m.copiarHoja(pixeles)) return@withContext null
                if (vectores.isNotEmpty()) VectorHoja.dibujarEnPixeles(
                    pixeles,
                    ancho,
                    alto,
                    vectores,
                )
                if (textos.isNotEmpty()) dibujarTextosEn(pixeles, ancho, alto, textos)
                if (recortar) {
                    val recorte = recortarAlContenido(pixeles, ancho, alto, Colores.BLANCO)
                        ?: return@withContext null
                    if (recorte.first == ancho && recorte.second == alto) {
                        return@withContext null
                    }
                    recorte
                } else {
                    val na = nuevoAncho.coerceIn(1, MAX_LADO)
                    val nb = nuevoAlto.coerceIn(1, MAX_LADO)
                    if (na == ancho && nb == alto) return@withContext null
                    Triple(na, nb, lienzoCentrado(pixeles, ancho, alto, na, nb))
                }
            }
            _estado.update {
                it.copy(
                    ajustandoLienzo = false,
                    textos = if (resultado != null) emptyList() else it.textos,
                    textoSeleccionado = if (resultado != null) null else it.textoSeleccionado,
                    textoEnEdicion = if (resultado != null) false else it.textoEnEdicion,
                    vectores = if (resultado != null) emptyList() else it.vectores,
                    vectorSeleccionado = if (resultado != null) null else it.vectorSeleccionado,
                    vectorEnEdicion = if (resultado != null) false else it.vectorEnEdicion,
                )
            }
            // Recrea/reemplaza la hoja con el nuevo tamaño (reutiliza la ruta de
            // apertura de un dibujo: asigna la textura GPU al tamaño nuevo).
            if (resultado != null) {
                abrirDibujo(resultado.first, resultado.second, resultado.third)
            }
        }
    }

    /**
     * Copia el dibujo ([ancho] × [alto]) centrado dentro de una hoja nueva de
     * [na] × [nb] px llena con el fondo. Lo que no entra se recorta y lo que
     * falta queda en blanco.
     */
    private fun lienzoCentrado(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        na: Int,
        nb: Int,
    ): IntArray {
        val destino = IntArray(na * nb) { Colores.BLANCO }
        val offX = (na - ancho) / 2
        val offY = (nb - alto) / 2
        val x0 = maxOf(0, -offX)
        val x1 = minOf(ancho, na - offX)
        if (x1 <= x0) return destino
        val anchoCopia = x1 - x0
        for (y in 0 until alto) {
            val dy = y + offY
            if (dy !in 0 until nb) continue
            System.arraycopy(
                pixeles, y * ancho + x0,
                destino, dy * na + offX + x0, anchoCopia,
            )
        }
        return destino
    }

    /**
     * Toque sin arrastre: pinta un punto con el lápiz o rellena una región con
     * la herramienta PINTAR. Un arrastre (trazo) lo maneja la capa de gestos.
     */
    fun tocarHoja(px: Float, py: Float) {
        val m = motor ?: return
        if (_estado.value.modo != Modo.EDICION) return
        // La pluma vectorial no pinta píxeles: un toque suelto deja un punto.
        if (_estado.value.lapiz.herramienta == Herramienta.PLUMA) {
            insertarPuntoVector(px, py)
            return
        }
        // Tocar un texto lo selecciona para moverlo/escalarlo o editarlo, en
        // vez de pintar un punto encima. En cualquier momento se puede volver
        // a tocar para retomarlo.
        val texto = textoEnPunto(px, py)
        if (texto != null) {
            seleccionarTexto(texto)
            return
        }
        // Tocar fuera del texto sólo sale de la edición: no pinta un punto por
        // accidente al querer soltar la palabra.
        if (_estado.value.textoEnEdicion) {
            terminarEdicionTexto()
            return
        }
        val (hx, hy) = aHoja(m, px, py)
        m.iniciarTrazo(hx, hy)
        m.terminarTrazo()
        _estado.update {
            it.copy(
                puedeDeshacer = m.puedeDeshacer(),
                puedeRehacer = m.puedeRehacer(),
                modificado = true,
                marco = it.marco + 1,
            )
        }
    }

    /** Fija (o quita con null) la imagen de calco y la muestra al elegirla. */
    fun elegirCalco(uri: String?) {
        // Elegir una imagen corta el ajuste libre del dibujo (no se puede
        // ajustar el calco y la hoja a la vez).
        asegurarSinAjuste()
        _estado.update {
            it.copy(
                calcoUri = uri,
                calcoVisible = uri != null,
                // Al poner una imagen nueva se auto-centra SIEMPRE y se ajusta
                // a su tamaño natural; recién después el usuario la mueve o
                // escala. Además se entra en modo ajuste para que pueda
                // ubicarla enseguida.
                calcoOffsetX = 0f,
                calcoOffsetY = 0f,
                calcoEscala = 1f,
                calcoEnEdicion = uri != null,
                // La paleta sugerida era de la imagen anterior.
                coloresCalco = if (uri != it.calcoUri) emptyList() else it.coloresCalco,
                marco = it.marco + 1,
            )
        }
    }

    /** Muestra u oculta la imagen de calco en cualquier momento. */
    fun alternarCalco() {
        if (_estado.value.calcoUri == null) return
        _estado.update { it.copy(calcoVisible = !it.calcoVisible) }
    }

    fun cambiarCalcoAlfa(alfa: Float) {
        _estado.update { it.copy(calcoAlfa = alfa.coerceIn(0f, 1f)) }
    }

    /** Activa/desactiva el ajuste del calco (mover/escalar con los gestos). */
    fun alternarEdicionCalco() {
        if (_estado.value.calcoUri == null) return
        // No se puede ajustar el calco y la hoja a la vez: se cancela el ajuste.
        asegurarSinAjuste()
        _estado.update {
            it.copy(
                calcoEnEdicion = !it.calcoEnEdicion,
                textoEnEdicion = false,
                textoSeleccionado = null,
                vectorEnEdicion = false,
                vectorSeleccionado = null,
                // Al entrar en ajuste se asegura de que la imagen se vea.
                calcoVisible = if (!it.calcoEnEdicion) true else it.calcoVisible,
            )
        }
    }

    /** Sale del ajuste del calco sin tocar su posición ni su escala. */
    fun terminarEdicionCalco() {
        if (!_estado.value.calcoEnEdicion) return
        _estado.update { it.copy(calcoEnEdicion = false) }
    }

    /**
     * Vuelve el calco a su estado automático: centrado en la hoja y ajustado a
     * su tamaño natural. Es el botón "Centrar" del diálogo de calco.
     */
    fun centrarCalco() {
        if (_estado.value.calcoUri == null) return
        _estado.update {
            it.copy(
                calcoOffsetX = 0f,
                calcoOffsetY = 0f,
                calcoEscala = 1f,
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Mueve el calco [dxPantalla]/[dyPantalla] píxeles de pantalla. El
     * desplazamiento se pasa a coordenadas de hoja con la cámara (rotación
     * incluida): el offset del calco vive en el espacio de la hoja, así que
     * queda anclado a la hoja y no a la pantalla.
     */
    fun moverCalco(dxPantalla: Float, dyPantalla: Float) {
        val m = motor ?: return
        if (!_estado.value.calcoEnEdicion) return
        val cam = m.camara()
        val c = kotlin.math.cos(cam.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam.rotacion.toDouble()).toFloat()
        val hx = (c * dxPantalla + s * dyPantalla) / cam.zoom
        val hy = (-s * dxPantalla + c * dyPantalla) / cam.zoom
        _estado.update {
            it.copy(
                calcoOffsetX = it.calcoOffsetX + hx,
                calcoOffsetY = it.calcoOffsetY + hy,
                marco = it.marco + 1,
            )
        }
    }

    /**
     * Escala el calco [factor] anclado al punto de pantalla ([focoX], [focoY]):
     * el punto de la imagen bajo los dedos queda quieto. El calco se escala
     * alrededor de su centro en coordenadas de hoja.
     */
    fun escalarCalco(factor: Float, focoX: Float, focoY: Float) {
        val m = motor ?: return
        if (!_estado.value.calcoEnEdicion || factor <= 0f) return
        val foco = aHoja(m, focoX, focoY)
        _estado.update {
            val vieja = it.calcoEscala
            val nueva = (vieja * factor).coerceIn(CALCO_ESCALA_MIN, CALCO_ESCALA_MAX)
            val ratio = if (vieja > 0f) nueva / vieja else 1f
            val centroX = it.hojaAncho / 2f
            val centroY = it.hojaAlto / 2f
            val cX = centroX + it.calcoOffsetX
            val cY = centroY + it.calcoOffsetY
            // Escalar alrededor del foco: el centro C pasa a F + ratio·(C − F).
            val nuevoCX = foco.first + ratio * (cX - foco.first)
            val nuevoCY = foco.second + ratio * (cY - foco.second)
            it.copy(
                calcoEscala = nueva,
                calcoOffsetX = nuevoCX - centroX,
                calcoOffsetY = nuevoCY - centroY,
                marco = it.marco + 1,
            )
        }
    }

    /** Rasteriza la vista (vw x vh px) con la cámara aplicada, para el lienzo. */
    fun renderizar(destino: IntArray, vw: Int, vh: Int) {
        motor?.renderizar(destino, vw, vh)
    }

    /**
     * Copia la hoja completa (ancho * alto px ARGB) a [destino] para exportarla
     * como imagen. Devuelve `false` si el motor no está listo o [destino] no
     * alcanza. Es la misma hoja que ve el usuario, sin cámara.
     */
    fun copiarHoja(destino: IntArray): Boolean = motor?.copiarHoja(destino) ?: false

    /**
     * Copia la hoja con los objetos vivos (vectores y textos) ya compuestos
     * encima, horneados sólo en la copia. Es lo que se exporta a la galería o
     * se comparte, para que la imagen final coincida con lo que se ve.
     */
    fun copiarHojaConObjetosVivos(destino: IntArray): Boolean {
        val m = motor ?: return false
        if (!m.copiarHoja(destino)) return false
        val estado = _estado.value
        VectorHoja.dibujarEnPixeles(destino, m.ancho, m.alto, estado.vectores)
        dibujarTextosEn(destino, m.ancho, m.alto, estado.textos)
        return true
    }

    /**
     * Dibuja [textos] sobre el buffer ARGB de una hoja (ancho × alto). Cada
     * texto se compone a su tamaño base y se escala igual que en la capa viva
     * ([CapaTextos]), así lo exportado coincide con lo que se ve incluso con
     * textos muy grandes.
     */
    private fun dibujarTextosEn(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        textos: List<TextoEnHoja>,
    ) {
        textos.forEach { texto ->
            TextoHoja.dibujarEscalado(
                pixeles,
                ancho,
                alto,
                texto.texto,
                texto.tipografia,
                texto.tamano,
                texto.escala,
                texto.centroX,
                texto.centroY,
                texto.color,
                texto.voltearH,
                texto.voltearV,
            )
        }
    }

    /**
     * Aplica a los textos vivos la misma transformación que
     * `MotorDibujo.transformarHoja`: espejo, desplazamiento y escala alrededor
     * del centro de la hoja. Así el texto acompaña al dibujo sin hornearse y
     * sigue siendo editable.
     */
    private fun transformarTextos(
        textos: List<TextoEnHoja>,
        ancho: Int,
        alto: Int,
        voltearH: Boolean,
        voltearV: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ): List<TextoEnHoja> {
        if (textos.isEmpty()) return textos
        val cx = (ancho - 1) / 2f
        val cy = (alto - 1) / 2f
        val fx = if (voltearH) -1f else 1f
        val fy = if (voltearV) -1f else 1f
        return textos.map { texto ->
            texto.copy(
                centroX = cx + desplazamientoX + fx * escala * (texto.centroX - cx),
                centroY = cy + desplazamientoY + fy * escala * (texto.centroY - cy),
                escala = texto.escala * escala,
                voltearH = texto.voltearH xor voltearH,
                voltearV = texto.voltearV xor voltearV,
            )
        }
    }

    /**
     * Aplica a los vectores la misma transformación que
     * `MotorDibujo.transformarHoja`: espejo, desplazamiento y escala alrededor
     * del centro de la hoja. El grosor acompaña a la escala para que el trazo
     * mantenga su aspecto.
     */
    private fun transformarVectores(
        vectores: List<TrazoVectorial>,
        ancho: Int,
        alto: Int,
        voltearH: Boolean,
        voltearV: Boolean,
        desplazamientoX: Float,
        desplazamientoY: Float,
        escala: Float,
    ): List<TrazoVectorial> {
        if (vectores.isEmpty()) return vectores
        val cx = (ancho - 1) / 2f
        val cy = (alto - 1) / 2f
        val fx = if (voltearH) -1f else 1f
        val fy = if (voltearV) -1f else 1f
        return vectores.map { vector ->
            val puntos = ArrayList<Float>(vector.puntos.size)
            var i = 0
            while (i + 1 < vector.puntos.size) {
                puntos.add(cx + desplazamientoX + fx * escala * (vector.puntos[i] - cx))
                puntos.add(cy + desplazamientoY + fy * escala * (vector.puntos[i + 1] - cy))
                i += 2
            }
            vector.copy(
                puntos = puntos,
                grosor = (vector.grosor * escala)
                    .coerceIn(VECTOR_GROSOR_MIN, VECTOR_GROSOR_MAX),
            )
        }
    }

    override fun onCleared() {
        motor?.close()
        motor = null
        super.onCleared()
    }

    private fun marco() {
        val m = motor ?: return
        _estado.update { it.copy(camara = m.camara(), marco = it.marco + 1) }
    }

    private fun actualizarLapiz(transformar: (Lapiz) -> Lapiz) {
        val m = motor ?: return
        val nuevo = transformar(_estado.value.lapiz)
        m.configurarLapiz(nuevo)
        _estado.update { it.copy(lapiz = nuevo) }
    }

    private fun aHoja(m: MotorDibujo, px: Float, py: Float): Pair<Float, Float> =
        pantallaAUnrotado(m.camara(), px, py)

    /** Punto de hoja bajo (px, py) según la cámara: `R(−rot)(pantalla+pan)/zoom`. */
    private fun pantallaAUnrotado(cam: Camara, px: Float, py: Float): Pair<Float, Float> {
        val ex = px + cam.panX
        val ey = py + cam.panY
        val c = kotlin.math.cos(cam.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam.rotacion.toDouble()).toFloat()
        return ((c * ex + s * ey) / cam.zoom) to ((-s * ex + c * ey) / cam.zoom)
    }

    /**
     * Ajusta el pan para que el punto de hoja (hfx, hfy) vuelva a caer en el
     * foco de pantalla (focoX, focoY) tras cambiar zoom/rotación: despeja
     * `pan = R(rot)·(hoja·zoom) − foco` leyendo la cámara ya actualizada.
     */
    private fun anclarFoco(
        m: MotorDibujo,
        hfx: Float,
        hfy: Float,
        focoX: Float,
        focoY: Float,
    ) {
        val cam2 = m.camara()
        val zx = hfx * cam2.zoom
        val zy = hfy * cam2.zoom
        val c = kotlin.math.cos(cam2.rotacion.toDouble()).toFloat()
        val s = kotlin.math.sin(cam2.rotacion.toDouble()).toFloat()
        val deseadoX = (c * zx - s * zy) - focoX
        val deseadoY = (s * zx + c * zy) - focoY
        m.pan(deseadoX - cam2.panX, deseadoY - cam2.panY)
    }

    private fun leerBackend(): BackendRender =
        preferencias.getString(CLAVE_BACKEND, null)
            ?.let { nombre -> runCatching { BackendRender.valueOf(nombre) }.getOrNull() }
            ?: BackendRender.AUTOMATICO

    private companion object {
        /** Coincide con el límite de Lienzo. */
        const val MAX_LADO = 4096
        const val PREFERENCIAS = "dibujo_config"
        const val CLAVE_BACKEND = "backend_render"

        /** Colores que se sugieren al analizar el calco. */
        const val K_COLORES_CALCO = 8

        /** Lado de la grilla de muestras del calco (64×64 = 4096 muestras). */
        const val MUESTRAS_LADO = 64

        /** Límites de escala del calco sobre su ajuste automático. */
        const val CALCO_ESCALA_MIN = 0.1f
        const val CALCO_ESCALA_MAX = 20f

        /** Límites de escala de un texto vivo (gesto de dos dedos). */
        const val TEXTO_ESCALA_MIN = 0.1f
        const val TEXTO_ESCALA_MAX = 30f

        /** Cuántos colores recientes se recuerdan en la barra. */
        const val MAX_RECIENTES = 12

        /** Distancia mínima (px de pantalla) entre vértices de la pluma. */
        const val VECTOR_DISTANCIA_MIN = 1.5f

        /** Tolerancia de simplificación del trazo vectorial (px de pantalla). */
        const val VECTOR_TOLERANCIA = 1.2f

        /** Margen extra para tocar un vector, además de su grosor (px de pantalla). */
        const val VECTOR_TOLERANCIA_PUNTERO = 10f

        /** Límites del grosor de un vector al escalarlo. */
        const val VECTOR_GROSOR_MIN = 0.25f
        const val VECTOR_GROSOR_MAX = 512f
    }
}
