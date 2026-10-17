package com.dibujo.android.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Proveedor sin datos: existe únicamente para que el sistema instancie el
 * proceso en el arranque y podamos arrancar [MonitorDibujo] antes de
 * `Application.onCreate`. Así el monitor queda activo siempre que la app
 * principal corre, sin tocar código del source set `main`.
 *
 * Definido solo en `src/debug/AndroidManifest.xml`: no existe en release.
 */
class MonitorProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.applicationContext?.let { MonitorDibujo.iniciar(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
