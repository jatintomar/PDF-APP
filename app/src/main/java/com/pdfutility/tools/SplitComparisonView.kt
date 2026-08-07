package com.pdfutility.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SplitComparisonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var originalBitmap: Bitmap? = null
    private var compressedBitmap: Bitmap? = null

    private var splitRatio = 0.5f // 0f to 1f
    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setBitmaps(original: Bitmap?, compressed: Bitmap?) {
        this.originalBitmap = original
        this.compressedBitmap = compressed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val orig = originalBitmap ?: return
        val comp = compressedBitmap ?: return

        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val splitX = w * splitRatio

        // Draw Left half (Original) clipped to splitX
        canvas.save()
        canvas.clipRect(0f, 0f, splitX, h.toFloat())
        drawBitmapFit(canvas, orig, w, h)
        canvas.restore()

        // Draw Right half (Compressed) clipped from splitX
        canvas.save()
        canvas.clipRect(splitX, 0f, w.toFloat(), h.toFloat())
        drawBitmapFit(canvas, comp, w, h)
        canvas.restore()

        // Draw slider divider line
        canvas.drawLine(splitX, 0f, splitX, h.toFloat(), linePaint)

        // Draw "BEFORE" label on left
        canvas.drawRect(20f, 20f, 180f, 70f, labelPaint)
        canvas.drawText("ORIGINAL", 100f, 55f, textPaint)

        // Draw "AFTER" label on right
        canvas.drawRect(w - 240f, 20f, w - 20f, 70f, labelPaint)
        canvas.drawText("COMPRESSED", w - 130f, 55f, textPaint)
    }

    private fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, viewW: Int, viewH: Int) {
        val src = Rect(0, 0, bitmap.width, bitmap.height)
        
        // Calculate fit center destination rectangle
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height
        val viewRatio = viewW.toFloat() / viewH
        
        val dest = if (bitmapRatio > viewRatio) {
            val destH = (viewW / bitmapRatio).toInt()
            val top = (viewH - destH) / 2
            Rect(0, top, viewW, top + destH)
        } else {
            val destW = (viewH * bitmapRatio).toInt()
            val left = (viewW - destW) / 2
            Rect(left, 0, left + destW, viewH)
        }
        canvas.drawBitmap(bitmap, src, dest, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                splitRatio = (event.x / width).coerceIn(0.01f, 0.99f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}
