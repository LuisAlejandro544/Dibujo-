package com.dibujo.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icono propio de borrador (Material Symbols `ink_eraser`, rounded + filled).
 *
 * `material-icons-extended` (versión que trae el BOM actual) todavía no incluye
 * un borrador real, así que lo definimos acá con el path oficial. El viewport de
 * Material Symbols es `0 -960 960 960` (Y en negativo); el grupo lo normaliza al
 * rango `0..960` para que se dibuje dentro del icono.
 */
val IconoBorrador: ImageVector by lazy {
    ImageVector.Builder(
        name = "Borrador",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    )
        .addGroup(translationY = 960f)
        .addPath(
            pathData = addPathNodes(
                "M690-240h150q17 0 28.5 11.5T880-200q0 17-11.5 28.5T840-160H610l80-80Z" +
                    "m-483 80q-8 0-15.5-3t-13.5-9l-73-73q-23-23-23.5-57t22.5-58l440-456q23-24 56.5-24t56.5 23l199 199q23 23 23 57t-23 57L532-172q-6 6-13.5 9t-15.5 3H207Z",
            ),
            fill = SolidColor(Color.Black),
        )
        .clearGroup()
        .build()
}
