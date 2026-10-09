package com.openassist.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

object ClipboardHelper {
    /** Puts [s] on the clipboard as a file/image URI. Returns false if the system refused. */
    fun copy(context: Context, s: Staged, label: String = "OpenAssist"): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val desc = ClipDescription(label, arrayOf(s.mime))
            cm.setPrimaryClip(ClipData(desc, ClipData.Item(s.uri)))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Saves [bitmap] as a PNG and copies it. */
    fun saveAndCopy(context: Context, bitmap: Bitmap): Staged? {
        val s = StagedFiles.stageBitmap(context, bitmap) ?: return null
        StagedFiles.lastSelection = s
        return if (copy(context, s)) s else null
    }

    /** File/image URIs currently on the clipboard (not plain text). */
    fun clipboardFileUris(context: Context): List<Uri> {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return emptyList()
            val desc = clip.description
            val isText = desc.mimeTypeCount > 0 &&
                (0 until desc.mimeTypeCount).all { desc.getMimeType(it).startsWith("text/") }
            if (isText) return emptyList()
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
