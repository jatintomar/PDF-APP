package com.pdfutility.tools

import android.net.Uri

data class PdfFile(
    val id: String,
    val name: String,
    val uri: Uri,
    val sizeString: String
)
