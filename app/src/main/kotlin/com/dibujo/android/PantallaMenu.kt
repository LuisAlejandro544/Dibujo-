package com.dibujo.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pantalla de inicio de la app: en vez de entrar directo al lienzo, ofrece tres
 * accesos a pantallas propias.
 *
 *  - Continuar dibujando: reanuda el borrador autoguardado.
 *  - Cargar dibujo: abre una copia de la biblioteca.
 *  - Opciones: configuración (motor gráfico).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMenu(
    alContinuar: () -> Unit,
    alCargar: () -> Unit,
    alOpciones: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { margenes ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.menu_subtitulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            TarjetaMenu(
                icono = Icons.Rounded.Draw,
                titulo = stringResource(R.string.menu_continuar),
                descripcion = stringResource(R.string.menu_continuar_desc),
                alPulsar = alContinuar,
            )
            TarjetaMenu(
                icono = Icons.Rounded.FolderOpen,
                titulo = stringResource(R.string.menu_cargar),
                descripcion = stringResource(R.string.menu_cargar_desc),
                alPulsar = alCargar,
            )
            TarjetaMenu(
                icono = Icons.Rounded.Settings,
                titulo = stringResource(R.string.menu_opciones),
                descripcion = stringResource(R.string.menu_opciones_desc),
                alPulsar = alOpciones,
            )
        }
    }
}

/** Fila grande y accesible del menú: icono, título y una línea de ayuda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarjetaMenu(
    icono: ImageVector,
    titulo: String,
    descripcion: String,
    alPulsar: () -> Unit,
) {
    Surface(
        onClick = alPulsar,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$titulo. $descripcion" },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
