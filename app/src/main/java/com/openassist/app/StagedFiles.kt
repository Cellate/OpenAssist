package com.openassist.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** A file copied into the app's cache so any part of the app (and the WebView) can read it. */
class Staged(val file: File, val name: String, val mime: String, val uri: Uri) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val ext: String
        get() = name.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "FILE" }
}

/**
 * Copies anything (images, PDFs, Office files, ...) into cache/selections/<id>/<name>.
 * Every attachment path in the app goes through here: the screen selection, the keyboard's
 * image/GIF commit, the file picker and "Share to OpenAssist".
 */
object StagedFiles {

    const val MAX_BYTES = 40L * 1024 * 1024
    private var counter = 0

    /** The most recent screen selection; used by "attach selection again". */
    @Volatile
    var lastSelection: Staged? = null

    fun root(ctx: Context): File = File(ctx.cacheDir, SelectionProvider.DIR).apply { mkdirs() }

    private fun uriFor(dirName: String, fileName: String): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(SelectionProvider.AUTHORITY)
            .appendPath(dirName)
            .appendPath(fileName)
            .build()

    fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun safeName(raw: String?): String {
        val invalidChars = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        var n = (raw ?: "file").substringAfterLast('/')
            .map { if (it in invalidChars) '_' else it }
            .joinToString("")
            .filter { it >= ' ' }
            .trim()
        if (n.isEmpty() || n == "." || n == "..") n = "file"
        if (n.length > 100) {
            val ext = n.substringAfterLast('.', "")
            n = n.take(80) + if (ext.length in 1..10) ".$ext" else ""
        }
        return n
    }

    @Synchronized
    private fun newDir(ctx: Context): Pair<String, File> {
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36) + (counter++).toString(36)
        val d = File(root(ctx), id)
        d.mkdirs()
        return Pair(id, d)
    }

    /** Removes staged files older than a day so the cache never grows. */
    fun cleanOld(ctx: Context) {
        val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
        root(ctx).listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
    }

    /** Copies the content behind [src] into the cache. Returns null if it can't be read or is too big. */
    fun stage(ctx: Context, src: Uri): Staged? {
        return try {
            val cr = ctx.contentResolver
            var display: String? = null
            cr.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) display = c.getString(0)
            }
            var name = safeName(display ?: src.lastPathSegment)
            var mime = cr.getType(src) ?: guessMime(name)
            if (mime == "application/octet-stream") mime = guessMime(name)
            if (!name.contains('.')) {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                if (ext != null) name = "$name.$ext"
            }

            val made = newDir(ctx)
            val out = File(made.second, name)
            var total = 0L
            val input = cr.openInputStream(src) ?: return null
            input.use { ins ->
                FileOutputStream(out).use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("too big")
                        os.write(buf, 0, n)
                    }
                }
            }
            Staged(out, name, mime, uriFor(made.first, name))
        } catch (e: Exception) {
            null
        }
    }

    fun stageBitmap(ctx: Context, bmp: Bitmap, baseName: String = "selection"): Staged? {
        return try {
            val made = newDir(ctx)
            val name = "$baseName.png"
            val f = File(made.second, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Staged(f, name, "image/png", uriFor(made.first, name))
        } catch (e: Exception) {
            null
        }
    }

    /** Rebuilds a [Staged] from a path that was passed between activities. Only paths inside our cache are accepted. */
    fun fromPath(ctx: Context, path: String): Staged? {
        return try {
            val f = File(path)
            val base = root(ctx).canonicalPath + File.separator
            val parent = f.parentFile
            if (parent == null || !f.isFile || !f.canonicalPath.startsWith(base)) null
            else Staged(f, f.name, guessMime(f.name), uriFor(parent.name, f.name))
        } catch (e: Exception) {
            null
        }
    }

    /** Stages [uris] on a background thread and calls [done] on the main thread: (staged files, how many failed). */
    fun stageAsync(ctx: Context, uris: List<Uri>, done: (List<Staged>, Int) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            cleanOld(app)
            val ok = ArrayList<Staged>()
            var failed = 0
            for (u in uris) {
                val s = stage(app, u)
                if (s != null) ok.add(s) else failed++
            }
            Handler(Looper.getMainLooper()).post { done(ok, failed) }
        }.start()
    }
}
