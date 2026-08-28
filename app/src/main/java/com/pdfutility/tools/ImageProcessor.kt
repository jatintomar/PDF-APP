package com.pdfutility.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance, on-device Image Processing Engine inspired by ImageToolbox.
 * Supports filters, color matrices, transforms, EXIF stripping, watermarking,
 * stitching/stacking, format conversions, and palette extraction.
 */
object ImageProcessor {

    enum class ImageFormat(val extension: String, val mimeType: String) {
        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png"),
        WEBP(".webp", "image/webp")
    }

    enum class FilterType {
        ORIGINAL,
        GRAYSCALE,
        SEPIA,
        INVERT,
        VINTAGE,
        WARM,
        COOL,
        HIGH_CONTRAST,
        VIGNETTE,
        SHARPEN,
        BLACK_AND_WHITE
    }

    enum class StitchOrientation {
        VERTICAL,
        HORIZONTAL
    }

    data class FilterParams(
        val brightness: Float = 0f,      // -100 to 100
        val contrast: Float = 1f,        // 0.1 to 3.0
        val saturation: Float = 1f,      // 0.0 to 3.0
        val filterType: FilterType = FilterType.ORIGINAL,
        val rotationAngle: Int = 0,      // 0, 90, 180, 270
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false
    )

    data class WatermarkOptions(
        val text: String,
        val textSize: Float = 36f,
        val textColor: Int = Color.WHITE,
        val alpha: Int = 180,
        val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT
    )

    enum class WatermarkPosition {
        CENTER,
        BOTTOM_RIGHT,
        TOP_LEFT,
        DIAGONAL_TILED
    }

    /**
     * Decodes a Bitmap from Uri with optional maximum dimension downsampling for speed and memory efficiency.
     */
    fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Applies ImageToolbox filter chains and transforms.
     */
    fun applyFiltersAndTransforms(source: Bitmap, params: FilterParams): Bitmap {
        val width = source.width
        val height = source.height

        // 1. Matrix transformations (Rotation & Flip)
        val matrix = Matrix()
        if (params.flipHorizontal) matrix.postScale(-1f, 1f, width / 2f, height / 2f)
        if (params.flipVertical) matrix.postScale(1f, -1f, width / 2f, height / 2f)
        if (params.rotationAngle != 0) matrix.postRotate(params.rotationAngle.toFloat())

        val transformedBitmap = Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)

        // 2. Color adjustments & Filter Matrix
        val colorMatrix = ColorMatrix()

        // Apply Preset Filter
        when (params.filterType) {
            FilterType.GRAYSCALE -> {
                colorMatrix.setSaturation(0f)
            }
            FilterType.SEPIA -> {
                val sepiaMatrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(sepiaMatrix)
            }
            FilterType.INVERT -> {
                val invertMatrix = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(invertMatrix)
            }
            FilterType.VINTAGE -> {
                val vintageMatrix = ColorMatrix(floatArrayOf(
                    0.9f, 0.1f, 0.1f, 0f, 20f,
                    0.1f, 0.8f, 0.1f, 0f, 10f,
                    0.1f, 0.1f, 0.7f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(vintageMatrix)
            }
            FilterType.WARM -> {
                val warmMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 15f,
                    0f, 1.0f, 0f, 0f, 5f,
                    0f, 0f, 0.8f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(warmMatrix)
            }
            FilterType.COOL -> {
                val coolMatrix = ColorMatrix(floatArrayOf(
                    0.8f, 0f, 0f, 0f, -10f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(coolMatrix)
            }
            FilterType.HIGH_CONTRAST -> {
                val scale = 1.4f
                val translate = (-0.5f * scale + 0.5f) * 255f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(contrastMatrix)
            }
            FilterType.BLACK_AND_WHITE -> {
                colorMatrix.setSaturation(0f)
                val scale = 2.0f
                val translate = (-0.5f * scale + 0.5f) * 255f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(contrastMatrix)
            }
            else -> {
                // Keep original
            }
        }

        // Apply Custom Saturation
        if (params.saturation != 1f && params.filterType != FilterType.GRAYSCALE && params.filterType != FilterType.BLACK_AND_WHITE) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(params.saturation)
            colorMatrix.postConcat(satMatrix)
        }

        // Apply Custom Contrast & Brightness
        if (params.contrast != 1f || params.brightness != 0f) {
            val c = params.contrast
            val b = params.brightness
            val translate = (-0.5f * c + 0.5f) * 255f + b
            val cbMatrix = ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(cbMatrix)
        }

        // Draw onto filtered result
        val resultBitmap = Bitmap.createBitmap(
            transformedBitmap.width,
            transformedBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(transformedBitmap, 0f, 0f, paint)

        // Apply Vignette if requested
        if (params.filterType == FilterType.VIGNETTE) {
            applyVignette(resultBitmap)
        }

        return resultBitmap
    }

    /**
     * Applies a radial vignette gradient.
     */
    private fun applyVignette(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val radius = max(bitmap.width, bitmap.height) / 1.2f
        val gradient = android.graphics.RadialGradient(
            bitmap.width / 2f,
            bitmap.height / 2f,
            radius,
            intArrayOf(Color.TRANSPARENT, Color.argb(160, 0, 0, 0)),
            floatArrayOf(0.4f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            isDither = true
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    }

    /**
     * Resizes a Bitmap to exact dimensions with high-quality bilinear filtering.
     */
    fun resizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) return source
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    /**
     * Stamps customizable text watermark onto bitmap.
     */
    fun applyWatermark(source: Bitmap, options: WatermarkOptions): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.textColor
            alpha = options.alpha
            textSize = options.textSize
            style = Paint.Style.FILL
            isFakeBoldText = true
            setShadowLayer(4f, 2f, 2f, Color.argb(120, 0, 0, 0))
        }

        val textBounds = Rect()
        paint.getTextBounds(options.text, 0, options.text.length, textBounds)
        val textWidth = paint.measureText(options.text)
        val textHeight = textBounds.height().toFloat()

        when (options.position) {
            WatermarkPosition.CENTER -> {
                val x = (result.width - textWidth) / 2f
                val y = (result.height + textHeight) / 2f
                canvas.drawText(options.text, x, y, paint)
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                val padding = 24f
                val x = result.width - textWidth - padding
                val y = result.height - padding
                canvas.drawText(options.text, x, y, paint)
            }
            WatermarkPosition.TOP_LEFT -> {
                val padding = 24f
                val x = padding
                val y = textHeight + padding
                canvas.drawText(options.text, x, y, paint)
            }
            WatermarkPosition.DIAGONAL_TILED -> {
                canvas.save()
                canvas.rotate(-35f, result.width / 2f, result.height / 2f)
                val stepX = (textWidth * 1.8f).toInt()
                val stepY = (textHeight * 4.0f).toInt()
                for (x in -result.width until result.width * 2 step stepX) {
                    for (y in -result.height until result.height * 2 step stepY) {
                        canvas.drawText(options.text, x.toFloat(), y.toFloat(), paint)
                    }
                }
                canvas.restore()
            }
        }
        return result
    }

    /**
     * Stitches multiple bitmaps together either vertically or horizontally.
     */
    fun stitchBitmaps(
        bitmaps: List<Bitmap>,
        orientation: StitchOrientation = StitchOrientation.VERTICAL,
        spacingPx: Int = 12,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        if (bitmaps.isEmpty()) throw IllegalArgumentException("Bitmap list cannot be empty")
        if (bitmaps.size == 1) return bitmaps[0]

        return if (orientation == StitchOrientation.VERTICAL) {
            val targetWidth = bitmaps.maxOf { it.width }
            var totalHeight = spacingPx * (bitmaps.size - 1)
            val scaledBitmaps = bitmaps.map { bmp ->
                if (bmp.width != targetWidth) {
                    val scale = targetWidth.toFloat() / bmp.width.toFloat()
                    val newHeight = (bmp.height * scale).roundToInt()
                    Bitmap.createScaledBitmap(bmp, targetWidth, newHeight, true)
                } else {
                    bmp
                }
            }
            totalHeight += scaledBitmaps.sumOf { it.height }

            val stitched = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            canvas.drawColor(backgroundColor)

            var currentY = 0f
            for (bmp in scaledBitmaps) {
                canvas.drawBitmap(bmp, 0f, currentY, null)
                currentY += bmp.height + spacingPx
            }
            stitched
        } else {
            val targetHeight = bitmaps.maxOf { it.height }
            var totalWidth = spacingPx * (bitmaps.size - 1)
            val scaledBitmaps = bitmaps.map { bmp ->
                if (bmp.height != targetHeight) {
                    val scale = targetHeight.toFloat() / bmp.height.toFloat()
                    val newWidth = (bmp.width * scale).roundToInt()
                    Bitmap.createScaledBitmap(bmp, newWidth, targetHeight, true)
                } else {
                    bmp
                }
            }
            totalWidth += scaledBitmaps.sumOf { it.width }

            val stitched = Bitmap.createBitmap(totalWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            canvas.drawColor(backgroundColor)

            var currentX = 0f
            for (bmp in scaledBitmaps) {
                canvas.drawBitmap(bmp, currentX, 0f, null)
                currentX += bmp.width + spacingPx
            }
            stitched
        }
    }

    /**
     * Extracts top dominant colors from a bitmap for UI color palette generation.
     */
    fun extractColorPalette(bitmap: Bitmap, maxColors: Int = 5): List<Int> {
        val sample = Bitmap.createScaledBitmap(bitmap, 40, 40, false)
        val colorCounts = mutableMapOf<Int, Int>()

        for (x in 0 until sample.width) {
            for (y in 0 until sample.height) {
                val pixel = sample.getPixel(x, y)
                if (Color.alpha(pixel) > 128) {
                    // Quantize colors slightly to group similar tones
                    val r = (Color.red(pixel) / 16) * 16
                    val g = (Color.green(pixel) / 16) * 16
                    val b = (Color.blue(pixel) / 16) * 16
                    val quantized = Color.rgb(r, g, b)
                    colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
                }
            }
        }

        return colorCounts.entries
            .sortedByDescending { it.value }
            .take(maxColors)
            .map { it.key }
    }

    /**
     * Compresses and saves a bitmap to a destination file with format and quality settings,
     * stripping EXIF tracking metadata if requested.
     */
    fun saveBitmap(
        bitmap: Bitmap,
        outputFile: File,
        format: ImageFormat = ImageFormat.JPEG,
        quality: Int = 85,
        stripMetadata: Boolean = true
    ) {
        val compressFormat = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(compressFormat, quality.coerceIn(1, 100), out)
            out.flush()
        }

        if (stripMetadata && (format == ImageFormat.JPEG || format == ImageFormat.WEBP)) {
            try {
                // Clear any leftover GPS/Camera tags by initializing empty ExifInterface
                val exif = ExifInterface(outputFile.absolutePath)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_MAKE, null)
                exif.setAttribute(ExifInterface.TAG_MODEL, null)
                exif.saveAttributes()
            } catch (e: Exception) {
                // Non-critical EXIF sanitize catch
            }
        }
    }
}
