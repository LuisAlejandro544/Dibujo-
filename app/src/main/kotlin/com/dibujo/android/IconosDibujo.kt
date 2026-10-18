package com.dibujo.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icono propio de borrador (Material Symbols `ink_eraser`, rounded + outlined).
 *
 * `material-icons-extended` (versión que trae el BOM actual) todavía no incluye
 * un borrador real, así que lo definimos acá con el path oficial en versión
 * **contorno**: se lee mejor que la rellena a 24 dp (se distingue el cuerpo
 * inclinado y la base), que era el problema de que no se entendiera.
 *
 * Se usa el path tal como lo exporta Material Symbols para Android (viewport
 * `0 0 960 960`, Y positiva); antes se normalizaba a mano desde el rango
 * `0 -960 960 960`, pero así es más simple y no depende de un `translationY`.
 */
val IconoBorrador: ImageVector by lazy {
    ImageVector.Builder(
        name = "Borrador",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).addPath(
        pathData = addPathNodes(
            "M690,720L880,720L880,800L610,800L690,720Z" +
                "M190,800L105,715Q82,692 81.5,658Q81,624 104,600L544,144Q567,120 600.5,120Q634,120 657,143L856,342Q879,365 879,399Q879,433 856,456L520,800L190,800Z" +
                "M486,720L800,398L602,200L160,656L224,720L486,720Z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * Icono de la herramienta DOBLE: dos trazos paralelos.
 *
 * `ContentCopy` (las dos hojitas de "copiar") no se entendía como "doble
 * lápiz/pincel"; este dibujo muestra de un vistazo las dos líneas paralelas que
 * traza la herramienta. El `Icon` que lo usa lo tiñe igual que al resto.
 */
val IconoTrazoDoble: ImageVector by lazy {
    ImageVector.Builder(
        name = "TrazoDoble",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes("M4,16 L14,6"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3.2f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = addPathNodes("M10,19 L20,9"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3.2f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}
