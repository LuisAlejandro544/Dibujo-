package com.dibujo.android.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dibujo.android.R
import com.dibujo.android.TemaDibujo
import kotlinx.coroutines.delay
import leakcanary.LeakCanary

/**
 * Panel de depuración: su propia Activity con su propio icono en el launcher.
 *
 * Solo existe en el variant debug. Lee los datos que [MonitorDibujo] recolecta
 * continuamente (memoria, hilos, acciones, logs y crashes) y permite copiar o
 * compartir un reporte para pegar en un issue sin depender de adb.
 */
class PanelDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(ANDROID_TRANSPARENT, ANDROID_TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(ANDROID_TRANSPARENT, ANDROID_TRANSPARENT),
        )
        setContent {
            TemaDibujo {
                PanelDebug(
                    alVerFugas = ::abrirFugas,
                    alCopiar = ::copiarReporte,
                    alCompartir = ::compartirReporte,
                )
            }
        }
    }

    private fun abrirFugas() {
        runCatching { startActivity(LeakCanary.newLeakDisplayActivityIntent()) }
            .onFailure {
                Toast.makeText(this, getString(R.string.debug_fugas_no_disponible), Toast.LENGTH_SHORT).show()
            }
    }

    private fun copiarReporte() {
        val gestor = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        gestor.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.debug_reporte_titulo), construirReporte(this)),
        )
        Toast.makeText(this, getString(R.string.debug_reporte_copiado), Toast.LENGTH_SHORT).show()
    }

    private fun compartirReporte() {
        val intencion = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.debug_reporte_titulo))
            putExtra(Intent.EXTRA_TEXT, construirReporte(this@PanelDebugActivity))
        }
        startActivity(Intent.createChooser(intencion, getString(R.string.debug_compartir_con)))
    }

    private companion object {
        val ANDROID_TRANSPARENT = android.graphics.Color.TRANSPARENT
    }
}

private enum class Seccion { RESUMEN, MEMORIA, HILOS, REGISTRO, ENTORNO }

@Composable
private fun etiquetaSeccion(seccion: Seccion): Int = when (seccion) {
    Seccion.RESUMEN -> R.string.debug_tab_resumen
    Seccion.MEMORIA -> R.string.debug_tab_memoria
    Seccion.HILOS -> R.string.debug_tab_hilos
    Seccion.REGISTRO -> R.string.debug_tab_registro
    Seccion.ENTORNO -> R.string.debug_tab_entorno
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelDebug(
    alVerFugas: () -> Unit,
    alCopiar: () -> Unit,
    alCompartir: () -> Unit,
) {
    var seccion by remember { mutableStateOf(Seccion.RESUMEN) }
    var refresco by remember { mutableIntStateOf(0) }

    // La UI se refresca sola cada segundo leyendo los buffers del monitor.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            refresco++
        }
    }

    val cdLimpiar = stringResource(R.string.debug_cd_limpiar)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_titulo)) },
                actions = {
                    IconButton(
                        onClick = { MonitorDibujo.limpiarRegistro() },
                        modifier = Modifier.semantics { contentDescription = cdLimpiar },
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            BarraAcciones(alVerFugas = alVerFugas, alCopiar = alCopiar, alCompartir = alCompartir)
        },
    ) { margenes ->
        Column(
            Modifier
                .padding(margenes)
                .fillMaxSize(),
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = seccion.ordinal, edgePadding = 0.dp) {
                Seccion.entries.forEach { s ->
                    Tab(
                        selected = seccion == s,
                        onClick = { seccion = s },
                        text = { Text(stringResource(etiquetaSeccion(s))) },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (seccion) {
                    Seccion.RESUMEN -> SeccionResumen(refresco)
                    Seccion.MEMORIA -> SeccionMemoria(refresco)
                    Seccion.HILOS -> SeccionHilos(refresco)
                    Seccion.REGISTRO -> SeccionRegistro(refresco)
                    Seccion.ENTORNO -> SeccionEntorno(refresco)
                }
            }
        }
    }
}

@Composable
private fun BarraAcciones(
    alVerFugas: () -> Unit,
    alCopiar: () -> Unit,
    alCompartir: () -> Unit,
) {
    val cdFugas = stringResource(R.string.debug_cd_fugas)
    val cdCopiar = stringResource(R.string.debug_cd_copiar)
    val cdCompartir = stringResource(R.string.debug_cd_compartir)
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = alVerFugas,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = cdFugas },
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.debug_accion_fugas))
            }
            OutlinedButton(
                onClick = alCopiar,
                modifier = Modifier.semantics { contentDescription = cdCopiar },
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(18.dp))
            }
            OutlinedButton(
                onClick = alCompartir,
                modifier = Modifier.semantics { contentDescription = cdCompartir },
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SeccionResumen(refresco: Int) {
    val contexto = LocalContext.current
    val muestra = remember(refresco) { MonitorDibujo.ultimaMuestra() }
    val fallos = remember(refresco) { MonitorDibujo.totalFallosFatales }
    val backend = remember(refresco) { leerBackendGuardado(contexto) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Titulo(stringResource(R.string.debug_estado_titulo))
            if (fallos == 0) {
                Text(
                    stringResource(R.string.debug_sin_incidencias),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    stringResource(R.string.debug_fallos_fatales, fallos),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            LineaDato(
                stringResource(
                    R.string.debug_uptime,
                    formatearDuracion(contexto, System.currentTimeMillis() - MonitorDibujo.inicioMs),
                ),
            )
            LineaDato(
                stringResource(R.string.debug_hilos_activos, muestra?.hilos?.size ?: 0),
            )
            if (muestra != null) {
                LineaDato(
                    stringResource(R.string.debug_pss_total_resumen, formatearKb(contexto, muestra.pssTotalKb)),
                )
                LineaDato(
                    stringResource(R.string.debug_ultima_actualizacion, formatearRelativo(contexto, muestra.tiempoMs)),
                )
            } else {
                Text(stringResource(R.string.debug_sin_muestras))
            }
            LineaDato(stringResource(R.string.debug_motor_backend, backend))
        }
        item {
            Spacer(Modifier.height(12.dp))
            if (muestra == null) return@item
            RAMPorCategoria(contexto, muestra)
        }
    }
}

@Composable
private fun SeccionMemoria(refresco: Int) {
    val contexto = LocalContext.current
    val muestra = remember(refresco) { MonitorDibujo.ultimaMuestra() }
    val valoresPss = remember(refresco) { MonitorDibujo.muestras(60).map { it.pssTotalKb.toFloat() } }
    val valoresJava = remember(refresco) { MonitorDibujo.muestras(60).map { it.javaUsado.toFloat() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (muestra == null) {
            item { Text(stringResource(R.string.debug_sin_muestras)) }
            return@LazyColumn
        }
        item {
            LineaDato(
                stringResource(
                    R.string.debug_mem_java_heap,
                    formatearBytes(contexto, muestra.javaUsado),
                    formatearBytes(contexto, muestra.javaMax),
                ),
            )
            LineaDato(
                stringResource(
                    R.string.debug_mem_nativo,
                    formatearBytes(contexto, muestra.nativoAsignado),
                    formatearBytes(contexto, muestra.nativoTamano),
                ),
            )
            LineaDato(stringResource(R.string.debug_mem_pss, formatearKb(contexto, muestra.pssTotalKb)))
            LineaDato(stringResource(R.string.debug_mem_private_dirty, formatearKb(contexto, muestra.privateDirtyKb)))
        }
        item {
            Spacer(Modifier.height(12.dp))
            RAMPorCategoria(contexto, muestra)
        }
        item {
            Spacer(Modifier.height(16.dp))
            Titulo(stringResource(R.string.debug_mem_grafico_pss))
            GraficoBarras(valoresPss, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(72.dp))
            Spacer(Modifier.height(16.dp))
            Titulo(stringResource(R.string.debug_mem_grafico_java))
            GraficoBarras(valoresJava, MaterialTheme.colorScheme.tertiary, Modifier.fillMaxWidth().height(72.dp))
        }
    }
}

@Composable
private fun RAMPorCategoria(contexto: Context, muestra: Muestra) {
    val total = muestra.pssTotalKb.coerceAtLeast(1)
    Titulo(stringResource(R.string.debug_mem_ram_titulo))
    BarraMemoria(
        etiqueta = stringResource(R.string.debug_mem_pss_java, formatearKb(contexto, muestra.pssJavaKb)),
        fraccion = muestra.pssJavaKb.toFloat() / total,
        color = MaterialTheme.colorScheme.primary,
    )
    BarraMemoria(
        etiqueta = stringResource(R.string.debug_mem_pss_nativo, formatearKb(contexto, muestra.pssNativoKb)),
        fraccion = muestra.pssNativoKb.toFloat() / total,
        color = MaterialTheme.colorScheme.tertiary,
    )
    BarraMemoria(
        etiqueta = stringResource(R.string.debug_mem_pss_graficos, formatearKb(contexto, muestra.pssGraficosKb)),
        fraccion = muestra.pssGraficosKb.toFloat() / total,
        color = MaterialTheme.colorScheme.secondary,
    )
    BarraMemoria(
        etiqueta = stringResource(R.string.debug_mem_pss_otros, formatearKb(contexto, muestra.pssOtrosKb)),
        fraccion = muestra.pssOtrosKb.toFloat() / total,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun SeccionHilos(refresco: Int) {
    val contexto = LocalContext.current
    val hilos = remember(refresco) { MonitorDibujo.hilosActuales() }
    val cambios = remember(refresco) { MonitorDibujo.cambiosHilos(80).reversed() }
    val acciones = remember(refresco) { MonitorDibujo.accionesRecientes(80).reversed() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Titulo(stringResource(R.string.debug_hilos_titulo, hilos.size)) }
        items(hilos, key = { it.nombre }) { hilo ->
            val tipo = if (hilo.daemon) {
                contexto.getString(R.string.debug_hilo_daemon)
            } else {
                contexto.getString(R.string.debug_hilo_usuario)
            }
            val valor = if (hilo.nuevo) {
                contexto.getString(
                    R.string.debug_formato_tres,
                    hilo.estado,
                    tipo,
                    contexto.getString(R.string.debug_hilos_nuevo),
                )
            } else {
                contexto.getString(R.string.debug_formato_dos, hilo.estado, tipo)
            }
            FilaDato(etiqueta = hilo.nombre, valor = valor)
        }
        item {
            Spacer(Modifier.height(16.dp))
            Titulo(stringResource(R.string.debug_cambios_titulo))
            if (cambios.isEmpty()) {
                Text(stringResource(R.string.debug_cambios_vacio))
            }
        }
        items(cambios) { evento ->
            val tipo = if (evento.tipo == TipoCambioHilo.APARECIO) {
                stringResource(R.string.debug_cambio_aparecio)
            } else {
                stringResource(R.string.debug_cambio_termino)
            }
            FilaDato(formatearRelativo(contexto, evento.tiempoMs), stringResource(R.string.debug_formato_dos, evento.nombre, tipo))
        }
        item {
            Spacer(Modifier.height(16.dp))
            Titulo(stringResource(R.string.debug_acciones_titulo))
            if (acciones.isEmpty()) {
                Text(stringResource(R.string.debug_acciones_vacio))
            }
        }
        items(acciones) { accion ->
            FilaDato(formatearRelativo(contexto, accion.tiempoMs), accion.nombre)
        }
    }
}

@Composable
private fun SeccionRegistro(refresco: Int) {
    val contexto = LocalContext.current
    var filtro by remember { mutableStateOf(NivelLog.WARN) }
    val registros = remember(refresco, filtro) { MonitorDibujo.logsRecientes(filtro, 500).reversed() }
    val fallos = remember(refresco) { MonitorDibujo.totalFallosFatales }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipFiltro(
                texto = stringResource(R.string.debug_filtro_warn),
                seleccionado = filtro == NivelLog.WARN,
                alPulsar = { filtro = NivelLog.WARN },
            )
            ChipFiltro(
                texto = stringResource(R.string.debug_filtro_error),
                seleccionado = filtro == NivelLog.ERROR,
                alPulsar = { filtro = NivelLog.ERROR },
            )
            ChipFiltro(
                texto = stringResource(R.string.debug_filtro_fatal),
                seleccionado = filtro == NivelLog.FATAL,
                alPulsar = { filtro = NivelLog.FATAL },
            )
        }
        Text(
            stringResource(R.string.debug_crashes, fallos),
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (fallos == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item { Titulo(stringResource(R.string.debug_registro_titulo)) }
            if (registros.isEmpty()) {
                item { Text(stringResource(R.string.debug_registro_vacio)) }
            }
            items(registros) { entrada ->
                EntradaRegistro(entrada)
            }
        }
    }
}

@Composable
private fun SeccionEntorno(refresco: Int) {
    val contexto = LocalContext.current
    val capacidades = remember(refresco) { com.dibujo.android.CapacidadesGraficas(contexto.applicationContext) }
    val paquete = remember(refresco) {
        runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0) }.getOrNull()
    }
    val recursos = contexto.resources
    val densidad = remember(refresco) { recursos.displayMetrics.densityDpi.toString() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            LineaDato(
                stringResource(R.string.debug_entorno_abi, android.os.Build.SUPPORTED_ABIS.joinToString(", ")),
            )
            LineaDato(
                stringResource(
                    R.string.debug_entorno_android,
                    android.os.Build.VERSION.RELEASE,
                    android.os.Build.VERSION.SDK_INT,
                ),
            )
            LineaDato(stringResource(R.string.debug_entorno_fabricante, android.os.Build.MANUFACTURER))
            LineaDato(stringResource(R.string.debug_entorno_modelo, android.os.Build.MODEL))
            LineaDato(stringResource(R.string.debug_entorno_densidad, densidad))
            LineaDato(stringResource(R.string.debug_entorno_gl, capacidades.openglEsVersion))
            LineaDato(
                stringResource(
                    R.string.debug_entorno_vulkan,
                    if (capacidades.vulkanSoportado) stringResource(R.string.debug_si) else stringResource(R.string.debug_no),
                ),
            )
            LineaDato(stringResource(R.string.debug_entorno_paquete, contexto.packageName))
            LineaDato(stringResource(R.string.debug_entorno_pid, android.os.Process.myPid()))
            LineaDato(
                stringResource(R.string.debug_entorno_version, paquete?.versionName ?: "?", codigoVersion(paquete)),
            )
            LineaDato(
                stringResource(R.string.debug_entorno_leakcanary, stringResource(R.string.debug_leakcanary_integrado)),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun codigoVersion(paquete: android.content.pm.PackageInfo?): Long =
    paquete?.versionCode?.toLong() ?: 0L

@Composable
private fun EntradaRegistro(entrada: EntradaLog) {
    val color = when (entrada.nivel) {
        NivelLog.FATAL -> MaterialTheme.colorScheme.error
        NivelLog.ERROR -> MaterialTheme.colorScheme.error
        NivelLog.WARN -> Color(0xFFB26A00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(
                formatearHora(entrada.tiempoMs),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = color,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.debug_formato_log, entrada.nivel.letra.toString(), entrada.tag),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = color,
            )
        }
        Text(
            entrada.mensaje,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ChipFiltro(texto: String, seleccionado: Boolean, alPulsar: () -> Unit) {
    FilterChip(
        selected = seleccionado,
        onClick = alPulsar,
        label = { Text(texto) },
        modifier = Modifier.semantics { contentDescription = texto },
    )
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun LineaDato(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun BarraMemoria(etiqueta: String, fraccion: Float, color: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { fraccion.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
        )
    }
}

@Composable
private fun GraficoBarras(valores: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (valores.isEmpty()) {
        Text(stringResource(R.string.debug_sin_muestras))
        return
    }
    val maximo = (valores.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val minimo = valores.minOrNull() ?: 0f
    val rango = (maximo - minimo).coerceAtLeast(1f)
    Canvas(modifier) {
        val ancho = size.width / valores.size
        valores.forEachIndexed { indice, valor ->
            val normalizado = (valor - minimo) / rango
            val alto = (normalizado.coerceIn(0f, 1f) * size.height).coerceAtLeast(1f)
            drawRect(
                color = color,
                topLeft = Offset(indice * ancho, size.height - alto),
                size = Size((ancho - 1f).coerceAtLeast(1f), alto),
            )
        }
    }
}

private fun leerBackendGuardado(contexto: Context): String =
    runCatching {
        contexto
            .getSharedPreferences("dibujo_config", Context.MODE_PRIVATE)
            .getString("backend_render", null)
    }.getOrNull() ?: "AUTOMATICO"
