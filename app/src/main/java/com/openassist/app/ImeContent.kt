package com.openassist.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.webkit.WebView
import android.widget.EditText

/**
 * Tells the keyboard "this text box accepts images and files" and receives them when the keyboard
 * commits one (clipboard panel, GIF/sticker search, ...). Without this the keyboard refuses with
 * "does not support image pasting here".
 */
object ImeContent {
    val MIME_TYPES = arrayOf(
        "image/png", "image/jpeg", "image/gif", "image/webp", "image/*",
        "application/pdf", "application/*", "text/*", "video/*", "audio/*", "*/*"
    )

    fun advertise(info: EditorInfo) {
        info.contentMimeTypes = MIME_TYPES
        // Older keyboards read the same list from these extras.
        val b = info.extras ?: Bundle().also { info.extras = it }
        b.putStringArray("androidx.core.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES", MIME_TYPES)
        b.putStringArray("android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES", MIME_TYPES)
    }

    fun wrap(ctx: Context, target: InputConnection, onStaged: (List<Staged>, Int) -> Unit): InputConnection =
        object : InputConnectionWrapper(target, false) {
            override fun commitContent(
                inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?
            ): Boolean {
                if ((flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                    try {
                        inputContentInfo.requestPermission()
                    } catch (e: Exception) {
                        return false
                    }
                }
                val app = ctx.applicationContext
                val uri = inputContentInfo.contentUri
                Thread {
                    val s = StagedFiles.stage(app, uri)
                    try { inputContentInfo.releasePermission() } catch (e: Exception) { /* already gone */ }
                    Handler(Looper.getMainLooper()).post {
                        onStaged(if (s != null) listOf(s) else emptyList(), if (s == null) 1 else 0)
                    }
                }.start()
                return true
            }
        }
}

/** The chat window's WebView, made image-aware for the keyboard. */
class ChatWebView(ctx: Context) : WebView(ctx) {
    var onFiles: ((List<Staged>, Int) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        ImeContent.advertise(outAttrs)
        return ImeContent.wrap(context, ic) { files, failed -> onFiles?.invoke(files, failed) }
    }
}

/** The overlay's ask bar: accepts images/files from the keyboard and from Paste. */
class OaEditText(ctx: Context) : EditText(ctx) {
    var onFiles: ((List<Staged>, Int) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        ImeContent.advertise(outAttrs)
        return ImeContent.wrap(context, ic) { files, failed -> onFiles?.invoke(files, failed) }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val uris = ClipboardHelper.clipboardFileUris(context)
            if (uris.isNotEmpty()) {
                StagedFiles.stageAsync(context, uris) { files, failed -> onFiles?.invoke(files, failed) }
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }
}
