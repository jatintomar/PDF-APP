package com.pdfutility.tools

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.FileOutputStream

object PdfProcessor {

    /**
     * Initialize PDFBox for Android. Crucial! Must be called once before usage.
     */
    fun init(context: Context) {
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Locks/Encrypts a PDF using PDFBox.
     */
    fun lockPdf(context: Context, inputUri: Uri, password: String, outputFile: File) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            PDDocument.load(inputStream).use { document ->
                cleanWatermark(document)
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(password, password, ap)
                spp.encryptionKeyLength = 128
                spp.permissions = ap
                document.protect(spp)
                FileOutputStream(outputFile).use { outputStream ->
                    document.save(outputStream)
                }
            }
        }
    }

    /**
     * Unlocks/Decrypts a password protected PDF.
     */
    fun unlockPdf(context: Context, inputUri: Uri, password: String, outputFile: File) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            PDDocument.load(inputStream, password).use { document ->
                cleanWatermark(document)
                document.setAllSecurityToBeRemoved(true)
                FileOutputStream(outputFile).use { outputStream ->
                    document.save(outputStream)
                }
            }
        }
    }

    /**
     * Merges multiple PDFs into a single output file.
     */
    fun mergePdfs(context: Context, pdfFiles: List<PdfFile>, outputFile: File) {
        val merger = PDFMergerUtility()
        val tempFiles = mutableListOf<File>()

        try {
            for (pdfFile in pdfFiles) {
                val tempFile = File.createTempFile("merge_item_", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(pdfFile.uri).use { input ->
                    if (input != null) {
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                tempFiles.add(tempFile)
                merger.addSource(tempFile)
            }

            FileOutputStream(outputFile).use { out ->
                merger.destinationStream = out
                merger.mergeDocuments(null)
            }
        } finally {
            for (tempFile in tempFiles) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    /**
     * Splits a PDF and extracts pages from startPage to endPage (1-indexed, inclusive).
     */
    fun splitPdf(context: Context, inputUri: Uri, startPage: Int, endPage: Int, outputFile: File) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            PDDocument.load(inputStream).use { document ->
                cleanWatermark(document)
                val pageCount = document.numberOfPages
                val validStart = startPage.coerceIn(1, pageCount)
                val validEnd = endPage.coerceIn(validStart, pageCount)

                PDDocument().use { outputDocument ->
                    for (i in (validStart - 1) until validEnd) {
                        outputDocument.addPage(document.getPage(i))
                    }
                    FileOutputStream(outputFile).use { outputStream ->
                        outputDocument.save(outputStream)
                    }
                }
            }
        }
    }

    /**
     * Splits a PDF and extracts a custom list of page numbers (1-indexed).
     */
    fun splitPdfCustom(context: Context, inputUri: Uri, pages: List<Int>, outputFile: File) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            PDDocument.load(inputStream).use { document ->
                cleanWatermark(document)
                val pageCount = document.numberOfPages
                PDDocument().use { outputDocument ->
                    for (p in pages) {
                        val zeroIndex = p - 1
                        if (zeroIndex in 0 until pageCount) {
                            outputDocument.addPage(document.getPage(zeroIndex))
                        }
                    }
                    FileOutputStream(outputFile).use { outputStream ->
                        outputDocument.save(outputStream)
                    }
                }
            }
        }
    }

    /**
     * Converts selected PNG/JPG images into a single PDF document.
     */
    fun imgToPdf(context: Context, imageUris: List<Uri>, fitToPage: Boolean, outputFile: File) {
        PDDocument().use { document ->
            for (uri in imageUris) {
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    if (inputStream != null) {
                        val imageBytes = inputStream.readBytes()
                        val image = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(document, imageBytes, "image")
                        
                        val imgWidth = image.width.toFloat()
                        val imgHeight = image.height.toFloat()
                        
                        val page = if (fitToPage) {
                            // If fitting to standard A4 size, adjust page orientation to match image aspect ratio
                            val isLandscape = imgWidth > imgHeight
                            val a4Width = if (isLandscape) 842f else 595f
                            val a4Height = if (isLandscape) 595f else 842f
                            com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(a4Width, a4Height))
                        } else {
                            // If A4 is NOT selected, page size should match the image size exactly so there's no blank space and no cropping
                            com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(imgWidth, imgHeight))
                        }
                        
                        document.addPage(page)
                        
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page).use { contentStream ->
                            if (fitToPage) {
                                val pageWidth = page.mediaBox.width
                                val pageHeight = page.mediaBox.height
                                
                                // Scale to fit the page exactly, maintaining aspect ratio
                                val scale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight)
                                val drawWidth = imgWidth * scale
                                val drawHeight = imgHeight * scale
                                
                                // Center the image perfectly on the A4 page
                                val x = (pageWidth - drawWidth) / 2f
                                val y = (pageHeight - drawHeight) / 2f
                                contentStream.drawImage(image, x, y, drawWidth, drawHeight)
                            } else {
                                // Draw starting from (0, 0) since page is exactly image size
                                contentStream.drawImage(image, 0f, 0f, imgWidth, imgHeight)
                            }
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { out ->
                document.save(out)
            }
        }
    }

    /**
     * Converts a PDF into images per page (PNG/JPEG/WEBP).
     */
    fun pdfToImg(context: Context, inputUri: Uri, outputDir: File, format: String = "PNG"): List<File> {
        val imageFiles = mutableListOf<File>()
        val compressFormat = when (format.uppercase()) {
            "JPEG", "JPG" -> android.graphics.Bitmap.CompressFormat.JPEG
            "WEBP" -> android.graphics.Bitmap.CompressFormat.WEBP
            else -> android.graphics.Bitmap.CompressFormat.PNG
        }
        val ext = when (format.uppercase()) {
            "JPEG", "JPG" -> "jpg"
            "WEBP" -> "webp"
            else -> "png"
        }
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            PDDocument.load(inputStream).use { document ->
                cleanWatermark(document)
                val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)
                for (i in 0 until document.numberOfPages) {
                    val bitmap = renderer.renderImageWithDPI(i, 150f, com.tom_roush.pdfbox.rendering.ImageType.ARGB)
                    val imageFile = File(outputDir, "page_${i + 1}.$ext")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(compressFormat, 100, out)
                    }
                    imageFiles.add(imageFile)
                }
            }
        }
        return imageFiles
    }

    /**
     * Compresses a PDF by rasterizing pages to JPEG.
     */
    fun compressPdf(context: Context, inputUri: Uri, quality: Int, outputFile: File) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                cleanWatermark(document)
                for (page in document.pages) {
                    val resources = page.resources
                    if (resources != null) {
                        val xObjectNames = resources.xObjectNames.toList()
                        for (name in xObjectNames) {
                            try {
                                val xObject = resources.getXObject(name)
                                if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                    val bitmap = xObject.image
                                    if (bitmap != null) {
                                        var scaledBitmap = bitmap
                                        // Scale down if image is huge to save more space
                                        val maxDim = 1500
                                        if (bitmap.width > maxDim || bitmap.height > maxDim) {
                                            val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                                            scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                                        }

                                        val stream = java.io.ByteArrayOutputStream()
                                        if (scaledBitmap.hasAlpha()) {
                                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                        } else {
                                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                                        }
                                        val compressedImg = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(document, stream.toByteArray(), name.name)
                                        resources.put(name, compressedImg)
                                        
                                        if (scaledBitmap != bitmap) {
                                            scaledBitmap.recycle()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                java.io.FileOutputStream(outputFile).use { out ->
                    document.save(out)
                }
            }
        }
    }

    /**
     * Resizes, crops, rotates, flips, applies filters and compresses an image (PNG/JPG).
     */
    fun compressImage(
        context: Context,
        inputUri: Uri,
        width: Int,
        height: Int,
        quality: Int,
        format: android.graphics.Bitmap.CompressFormat,
        outputFile: File,
        cropX: Double = 0.0,
        cropY: Double = 0.0,
        cropW: Double = 100.0,
        cropH: Double = 100.0,
        rotation: Int = 0,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        colorFilter: String = "Normal"
    ) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open file stream")
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                ?: throw Exception("Failed to decode image")
            
            // 1. Rotate & Flip
            val rotatedBitmap = if (rotation != 0 || flipHorizontal || flipVertical) {
                val matrix = android.graphics.Matrix().apply {
                    if (rotation != 0) {
                        postRotate(rotation.toFloat())
                    }
                    val sx = if (flipHorizontal) -1.0f else 1.0f
                    val sy = if (flipVertical) -1.0f else 1.0f
                    postScale(sx, sy)
                }
                android.graphics.Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            } else {
                originalBitmap
            }
            
            // 2. Crop
            val rotW = rotatedBitmap.width
            val rotH = rotatedBitmap.height
            val cx = (cropX * rotW / 100.0).toInt().coerceIn(0, rotW - 1)
            val cy = (cropY * rotH / 100.0).toInt().coerceIn(0, rotH - 1)
            val cw = (cropW * rotW / 100.0).toInt().coerceIn(1, rotW - cx)
            val ch = (cropH * rotH / 100.0).toInt().coerceIn(1, rotH - cy)
            
            val croppedBitmap = android.graphics.Bitmap.createBitmap(rotatedBitmap, cx, cy, cw, ch).also {
                if (rotatedBitmap != originalBitmap) rotatedBitmap.recycle()
            }
            
            // 3. Scale
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, width, height, true).also {
                if (it != croppedBitmap && croppedBitmap != originalBitmap) croppedBitmap.recycle()
            }

            // 4. Color Filters
            val filteredBitmap = when (colorFilter) {
                "Grayscale" -> {
                    val bmp = android.graphics.Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint()
                    val cm = android.graphics.ColorMatrix()
                    cm.setSaturation(0f)
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
                    if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
                    bmp
                }
                "Sepia" -> {
                    val bmp = android.graphics.Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint()
                    val cm = android.graphics.ColorMatrix().apply {
                        set(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f,      0f,      0f,      1f, 0f
                        ))
                    }
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
                    if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
                    bmp
                }
                "Inverted" -> {
                    val bmp = android.graphics.Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint()
                    val cm = android.graphics.ColorMatrix().apply {
                        set(floatArrayOf(
                            -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
                             0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
                             0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
                             0.0f,  0.0f,  0.0f, 1.0f,   0.0f
                        ))
                    }
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
                    if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
                    bmp
                }
                else -> scaledBitmap
            }
            
            FileOutputStream(outputFile).use { out ->
                filteredBitmap.compress(format, quality, out)
            }
            
            if (filteredBitmap != originalBitmap) filteredBitmap.recycle()
            originalBitmap.recycle()
        }
    }

    /**
     * Cleans watermarks containing "crpf" or "sambhav" from a PDF document.
     */
    fun cleanWatermark(document: com.tom_roush.pdfbox.pdmodel.PDDocument) {
        try {
            for (page in document.pages) {
                try {
                    // 1. Clean page content stream(s)
                    val streams = page.contentStreams
                    if (streams != null) {
                        while (streams.hasNext()) {
                            val stream = streams.next()
                            cleanContentStream(document, stream)
                        }
                    }
                    // 2. Clean form XObjects in the resources recursively
                    cleanResources(document, page.resources)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanContentStream(document: com.tom_roush.pdfbox.pdmodel.PDDocument, stream: com.tom_roush.pdfbox.pdmodel.common.PDStream) {
        try {
            val parser = com.tom_roush.pdfbox.pdfparser.PDFStreamParser(stream)
            parser.parse()
            val tokens = parser.tokens ?: return
            val newTokens = ArrayList<Any>()
            var modified = false

            for (token in tokens) {
                if (token is com.tom_roush.pdfbox.cos.COSString) {
                    val str = token.string
                    if (str != null) {
                        val cleanStr = str.trim().lowercase().replace("\\s+".toRegex(), " ")
                        if (cleanStr == "crpf" || cleanStr == "sambhav" || cleanStr == "shambhav" || cleanStr == "crpf sambhav" || cleanStr == "crpf shambhav") {
                            newTokens.add(com.tom_roush.pdfbox.cos.COSString("".toByteArray()))
                            modified = true
                            continue
                        }
                    }
                    newTokens.add(token)
                } else if (token is com.tom_roush.pdfbox.cos.COSArray) {
                    val sb = java.lang.StringBuilder()
                    for (j in 0 until token.size()) {
                        val item = token.get(j)
                        if (item is com.tom_roush.pdfbox.cos.COSString) {
                            sb.append(item.string ?: "")
                        }
                    }
                    val combinedStr = sb.toString().trim().lowercase().replace("\\s+".toRegex(), " ")
                    if (combinedStr.contains("crpf") || combinedStr.contains("sambhav") || combinedStr.contains("shambhav")) {
                        val newArray = com.tom_roush.pdfbox.cos.COSArray()
                        for (j in 0 until token.size()) {
                            val item = token.get(j)
                            if (item is com.tom_roush.pdfbox.cos.COSString) {
                                newArray.add(com.tom_roush.pdfbox.cos.COSString("".toByteArray()))
                            } else {
                                newArray.add(item)
                            }
                        }
                        newTokens.add(newArray)
                        modified = true
                    } else {
                        var arrayModified = false
                        val newArray = com.tom_roush.pdfbox.cos.COSArray()
                        for (j in 0 until token.size()) {
                            val item = token.get(j)
                            if (item is com.tom_roush.pdfbox.cos.COSString) {
                                val str = item.string
                                if (str != null) {
                                    val cleanStr = str.trim().lowercase().replace("\\s+".toRegex(), " ")
                                    if (cleanStr == "crpf" || cleanStr == "sambhav" || cleanStr == "shambhav" || cleanStr == "crpf sambhav" || cleanStr == "crpf shambhav") {
                                        newArray.add(com.tom_roush.pdfbox.cos.COSString("".toByteArray()))
                                        arrayModified = true
                                        continue
                                    }
                                }
                            }
                            newArray.add(item)
                        }
                        newTokens.add(newArray)
                        if (arrayModified) {
                            modified = true
                        }
                    }
                } else {
                    newTokens.add(token)
                }
            }

            if (modified) {
                stream.createOutputStream().use { out ->
                    val writer = com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter(out)
                    writer.writeTokens(newTokens)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanResources(document: com.tom_roush.pdfbox.pdmodel.PDDocument, resources: com.tom_roush.pdfbox.pdmodel.PDResources?) {
        if (resources == null) return
        try {
            for (name in resources.xObjectNames) {
                try {
                    val xobject = resources.getXObject(name)
                    if (xobject is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) {
                        val stream = xobject.contentStream
                        if (stream != null) {
                            cleanContentStream(document, stream)
                        }
                        cleanResources(document, xobject.resources)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Converts a DOCX file to a temporary PDF file.
     */
    fun convertDocxToPdf(context: Context, docxUri: Uri, pdfFile: File) {
        val paragraphs = mutableListOf<String>()
        context.contentResolver.openInputStream(docxUri).use { inputStream ->
            if (inputStream == null) throw Exception("Cannot open DOCX stream")
            val zipInputStream = java.util.zip.ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = android.util.Xml.newPullParser()
                    parser.setInput(zipInputStream, "UTF-8")
                    var eventType = parser.eventType
                    val currentParagraph = java.lang.StringBuilder()
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        val name = parser.name
                        when (eventType) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                if (name == "p" || name == "w:p") {
                                    currentParagraph.setLength(0)
                                } else if (name == "t" || name == "w:t") {
                                    parser.next()
                                    if (parser.text != null) {
                                        currentParagraph.append(parser.text)
                                    }
                                } else if (name == "br" || name == "w:br") {
                                    currentParagraph.append("\n")
                                } else if (name == "tab" || name == "w:tab") {
                                    currentParagraph.append("\t")
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                                if (name == "p" || name == "w:p") {
                                    paragraphs.add(currentParagraph.toString())
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zipInputStream.nextEntry
            }
        }

        // Generate PDF using PDFBox
        val document = PDDocument()
        try {
            val fontSize = 11f
            val lineSpacing = 15f
            val margin = 54f
            val pageWidth = 612f // Letter size width
            val pageHeight = 792f // Letter size height
            val contentWidth = pageWidth - 2 * margin
            val contentHeight = pageHeight - 2 * margin
            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA

            var page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER)
            document.addPage(page)

            var contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
            contentStream.beginText()
            contentStream.setFont(font, fontSize)
            contentStream.newLineAtOffset(margin, pageHeight - margin)

            var currentY = pageHeight - margin

            for (pText in paragraphs) {
                val sanitized = sanitizeTextForPdf(pText)
                if (sanitized.trim().isEmpty()) {
                    currentY -= lineSpacing
                    if (currentY < margin) {
                        contentStream.endText()
                        contentStream.close()

                        page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER)
                        document.addPage(page)
                        contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                        contentStream.beginText()
                        contentStream.setFont(font, fontSize)
                        contentStream.newLineAtOffset(margin, pageHeight - margin)
                        currentY = pageHeight - margin
                    } else {
                        contentStream.newLineAtOffset(0f, -lineSpacing)
                    }
                    continue
                }

                val wrappedLines = wrapText(sanitized, font, fontSize, contentWidth)
                for (line in wrappedLines) {
                    currentY -= lineSpacing
                    if (currentY < margin) {
                        contentStream.endText()
                        contentStream.close()

                        page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER)
                        document.addPage(page)
                        contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                        contentStream.beginText()
                        contentStream.setFont(font, fontSize)
                        contentStream.newLineAtOffset(margin, pageHeight - margin)
                        currentY = pageHeight - margin - lineSpacing
                    } else {
                        contentStream.newLineAtOffset(0f, -lineSpacing)
                    }
                    try {
                        contentStream.showText(line)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Add spacing after paragraph
                currentY -= 6f
                if (currentY < margin) {
                    contentStream.endText()
                    contentStream.close()

                    page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER)
                    document.addPage(page)
                    contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.newLineAtOffset(margin, pageHeight - margin)
                    currentY = pageHeight - margin
                } else {
                    contentStream.newLineAtOffset(0f, -6f)
                }
            }

            contentStream.endText()
            contentStream.close()

            FileOutputStream(pdfFile).use { fos ->
                document.save(fos)
            }
        } finally {
            document.close()
        }
    }

    private fun sanitizeTextForPdf(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            val code = char.code
            when (char) {
                '’', '‘', '\'' -> sb.append('\'')
                '“', '”', '"' -> sb.append('"')
                '—', '–', '-' -> sb.append('-')
                '…' -> sb.append("...")
                else -> {
                    if (code in 32..126 || code in 160..255) {
                        sb.append(char)
                    } else if (char.isWhitespace()) {
                        sb.append(' ')
                    } else {
                        sb.append('?')
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun wrapText(text: String, font: com.tom_roush.pdfbox.pdmodel.font.PDFont, fontSize: Float, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (p in paragraphs) {
            val words = p.split(Regex("\\s+"))
            val currentLine = StringBuilder()
            for (word in words) {
                if (word.isEmpty()) continue
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val width = try {
                    font.getStringWidth(testLine) / 1000f * fontSize
                } catch (e: Exception) {
                    0f
                }
                if (width <= maxWidth) {
                    if (currentLine.isNotEmpty()) currentLine.append(" ")
                    currentLine.append(word)
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine.setLength(0)
                        currentLine.append(word)
                    } else {
                        lines.add(word)
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }
        return lines
    }

    /**
     * Gets the file name from a Uri.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document"
    }
}
