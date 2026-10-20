package com.dibujo.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Índice de configuración: cada apartado tiene su propia pantalla y archivo.
 * Antes todo (motor, diagnóstico y créditos) vivía en este archivo; ahora acá
 * sólo se elige el apartado y se agregan los créditos de IA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaOpciones(
    alAbrirMotor: () -> Unit,
    alAbrirDibujo: () -> Unit,
    alAbrirRetroalimentacion: () -> Unit,
    alAbrirHistorial: () -> Unit,
    alVolver: () -> Unit,
) {
    BackHandler(onBack = alVolver)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_titulo)) },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(margenes)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TarjetaAjuste(
                icono = Icons.Rounded.Memory,
                titulo = stringResource(R.string.config_motor_titulo),
                descripcion = stringResource(R.string.config_motor_desc),
                alPulsar = alAbrirMotor,
            )
            TarjetaAjuste(
                icono = Icons.Rounded.Brush,
                titulo = stringResource(R.string.config_dibujo_titulo),
                descripcion = stringResource(R.string.config_dibujo_desc),
                alPulsar = alAbrirDibujo,
            )
            TarjetaAjuste(
                icono = Icons.AutoMirrored.Rounded.VolumeUp,
                titulo = stringResource(R.string.config_feedback_titulo),
                descripcion = stringResource(R.string.config_feedback_desc),
                alPulsar = alAbrirRetroalimentacion,
            )
            TarjetaAjuste(
                icono = Icons.Rounded.History,
                titulo = stringResource(R.string.config_historial_titulo),
                descripcion = stringResource(R.string.config_historial_desc),
                alPulsar = alAbrirHistorial,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.config_atribucion_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.config_atribucion_quickdraw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
