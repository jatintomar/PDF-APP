package com.pdfutility.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCropChangeListener {
        fun onCropChanged(xPct: Float, yPct: Float, wPct: Float, hPct: Float)
    }

    var listener: OnCropChangeListener? = null

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#2196F3") // Blue accent
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val handlePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Crop box in percentages (0f to 100f)
    var cropX = 0f
    var cropY = 0f
    var cropW = 100f
    var cropH = 100f

    var imageWidth: Int = 0
    var imageHeight: Int = 0

    private val cropRect = RectF()
    private val handleRadius = 28f
    private val minBoxSize = 50f // minimum box size in pixels

    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private enum class DragMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateCropRectFromPercentages()
    }

    private fun updateCropRectFromPercentages() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0 && h > 0) {
            cropRect.left = (cropX / 100f) * w
            cropRect.top = (cropY / 100f) * h
            cropRect.right = ((cropX + cropW) / 100f) * w
            cropRect.bottom = ((cropY + cropH) / 100f) * h
        }
    }

    private fun updatePercentagesFromCropRect() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0 && h > 0) {
            cropX = (cropRect.left / w) * 100f
            cropY = (cropRect.top / h) * 100f
            cropW = (cropRect.width() / w) * 100f
            cropH = (cropRect.height() / h) * 100f
            
            // Constrain
            cropX = max(0f, min(100f, cropX))
            cropY = max(0f, min(100f, cropY))
            cropW = max(1f, min(100f - cropX, cropW))
            cropH = max(1f, min(100f - cropY, cropH))
            
            listener?.onCropChanged(cropX, cropY, cropW, cropH)
        }
    }

    fun setCropValues(x: Float, y: Float, w: Float, h: Float) {
        cropX = x
        cropY = y
        cropW = w
        cropH = h
        updateCropRectFromPercentages()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        // 1. Draw dim overlays
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, overlayPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, overlayPaint)

        // 2. Draw border
        canvas.drawRect(cropRect, borderPaint)

        // 3. Draw corner handle circles
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, handlePaint)

        // 4. Draw crop dimension text inside box (optional, very nice touch)
        val pixelW = (cropW / 100f * imageWidth).toInt()
        val pixelH = (cropH / 100f * imageHeight).toInt()
        val text = if (imageWidth > 0 && imageHeight > 0) "$pixelW x $pixelH px" else "${cropW.toInt()}% x ${cropH.toInt()}%"
        val textY = cropRect.centerY() + 12f
        canvas.drawText(text, cropRect.centerX(), textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = getDragModeForTouch(x, y)
                if (dragMode != DragMode.NONE) {
                    lastTouchX = x
                    lastTouchY = y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode != DragMode.NONE) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    moveCropBox(dx, dy)
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getDragModeForTouch(tx: Float, ty: Float): DragMode {
        val clickRadius = handleRadius * 2.5f
        if (dist(tx, ty, cropRect.left, cropRect.top) < clickRadius) return DragMode.TOP_LEFT
        if (dist(tx, ty, cropRect.right, cropRect.top) < clickRadius) return DragMode.TOP_RIGHT
        if (dist(tx, ty, cropRect.left, cropRect.bottom) < clickRadius) return DragMode.BOTTOM_LEFT
        if (dist(tx, ty, cropRect.right, cropRect.bottom) < clickRadius) return DragMode.BOTTOM_RIGHT
        if (cropRect.contains(tx, ty)) return DragMode.MOVE
        return DragMode.NONE
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return max(0.1f, Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat())
    }

    private fun moveCropBox(dx: Float, dy: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        when (dragMode) {
            DragMode.MOVE -> {
                val boxW = cropRect.width()
                val boxH = cropRect.height()
                cropRect.left = max(0f, min(w - boxW, cropRect.left + dx))
                cropRect.top = max(0f, min(h - boxH, cropRect.top + dy))
                cropRect.right = cropRect.left + boxW
                cropRect.bottom = cropRect.top + boxH
            }
            DragMode.TOP_LEFT -> {
                cropRect.left = max(0f, min(cropRect.right - minBoxSize, cropRect.left + dx))
                cropRect.top = max(0f, min(cropRect.bottom - minBoxSize, cropRect.top + dy))
            }
            DragMode.TOP_RIGHT -> {
                cropRect.right = max(cropRect.left + minBoxSize, min(w, cropRect.right + dx))
                cropRect.top = max(0f, min(cropRect.bottom - minBoxSize, cropRect.top + dy))
            }
            DragMode.BOTTOM_LEFT -> {
                cropRect.left = max(0f, min(cropRect.right - minBoxSize, cropRect.left + dx))
                cropRect.bottom = max(cropRect.top + minBoxSize, min(h, cropRect.bottom + dy))
            }
            DragMode.BOTTOM_RIGHT -> {
                cropRect.right = max(cropRect.left + minBoxSize, min(w, cropRect.right + dx))
                cropRect.bottom = max(cropRect.top + minBoxSize, min(h, cropRect.bottom + dy))
            }
            else -> {}
        }
        updatePercentagesFromCropRect()
    }
}
