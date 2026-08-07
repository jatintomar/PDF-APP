package com.pdfutility.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun Context.getFileName(uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        try {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    return result ?: "document"
}

fun String.addTagToFileName(tag: String): String {
    val lastDotIndex = this.lastIndexOf(".")
    if (lastDotIndex == -1) {
        return "${this}_$tag"
    }
    val name = this.substring(0, lastDotIndex)
    val ext = this.substring(lastDotIndex)
    return "${name}_$tag$ext"
}
