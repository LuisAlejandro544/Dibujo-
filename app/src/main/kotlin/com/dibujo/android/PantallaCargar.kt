package com.dibujo.android

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla "Cargar dibujo": lista las copias guardadas en la app (fecha,
 * tamaño y miniatura) y permite abrir una o borrarla. Es la biblioteca de
 * dibujos, separada de la galería del sistema (que sigue usándose al exportar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCargar(
    alVolver: () -> Unit,
    alAbrir: (AlmacenDibujos.DibujoGuardado) -> Unit,
) {
    BackHandler(onBack = alVolver)
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var dibujos by remember { mutableStateOf<List<AlmacenDibujos.DibujoGuardado>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var aBorrar by remember { mutableStateOf<AlmacenDibujos.DibujoGuardado?>(null) }

    // Se relee cada vez que se entra a la pantalla (incluida la vuelta de la
    // pantalla de dibujo, por si se guardó una copia nueva).
    LaunchedEffect(Unit) {
        dibujos = withContext(Dispatchers.IO) { AlmacenDibujos.listar(contexto) }
        cargando = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cargar_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_volver),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { margenes ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(margenes),
        ) {
            when {
                cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                dibujos.isEmpty() -> SinDibujos(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(dibujos, key = { it.id }) { dibujo ->
                        ItemDibujo(
                            dibujo = dibujo,
                            alAbrir = { alAbrir(dibujo) },
                            alBorrar = { aBorrar = dibujo },
                        )
                    }
                }
            }
        }
    }

    aBorrar?.let { dibujo ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text(stringResource(R.string.cargar_borrar_titulo)) },
            text = { Text(stringResource(R.string.cargar_borrar_confirmacion)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        aBorrar = null
                        alcance.launch {
                            withContext(Dispatchers.IO) { AlmacenDibujos.borrar(contexto, dibujo.id) }
                            dibujos = withContext(Dispatchers.IO) { AlmacenDibujos.listar(contexto) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.borrar))
                }
            },
            dismissButton = {
                TextButton(onClick = { aBorrar = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            },
        )
    }
}

@Composable
private fun SinDibujos(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.cargar_vacio),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(R.string.cargar_vacio_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fila de la biblioteca: miniatura + datos, se toca para abrir. */
@Composable
private fun ItemDibujo(
    dibujo: AlmacenDibujos.DibujoGuardado,
    alAbrir: () -> Unit,
    alBorrar: () -> Unit,
) {
    val contexto = LocalContext.current
    val miniatura = produceState<Bitmap?>(initialValue = null, dibujo.id) {
        value = withContext(Dispatchers.IO) {
            AlmacenDibujos.miniatura(contexto, dibujo.id, LADO_MINIATURA)
        }
    }
    val fecha = remember(dibujo.fecha) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dibujo.fecha))
    }
    val descripcionAbrir = stringResource(R.string.cd_abrir_dibujo)
    val descripcionBorrar = stringResource(R.string.cd_borrar_dibujo)

    Surface(
        onClick = alAbrir,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$descripcionAbrir. $fecha" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(LADO_MINIATURA.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = miniatura.value
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(fecha, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.cargar_dimensiones, dibujo.ancho, dibujo.alto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = alBorrar) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = descripcionBorrar,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Lado (en dp) de la miniatura de cada dibujo en la lista. */
private const val LADO_MINIATURA = 64
