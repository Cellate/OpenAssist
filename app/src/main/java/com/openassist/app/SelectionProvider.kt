package com.openassist.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class SelectionProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.openassist.app.selection"
        const val DIR = "selections"

        fun getUriForFile(file: File): Uri {
            val parentName = file.parentFile?.name ?: "temp"
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(parentName)
                .appendPath(file.name)
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    private fun fileFor(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException("no context")
        val segs = uri.pathSegments
        if (segs.size != 2) throw FileNotFoundException("bad uri")
        val base = File(ctx.cacheDir, DIR).canonicalFile
        val file = File(File(base, segs[0]), segs[1]).canonicalFile
        if (!file.path.startsWith(base.path + File.separator)) {
            throw FileNotFoundException(segs[1])
        }
        return file
    }

    override fun getType(uri: Uri): String? =
        try { StagedFiles.guessMime(fileFor(uri).name) } catch (e: FileNotFoundException) { null }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = fileFor(uri)
        file.parentFile?.mkdirs()
        val pfdMode = when {
            mode.contains("w") -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, pfdMode)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val file = try { fileFor(uri) } catch (e: FileNotFoundException) { return null }
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(cols).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
}
