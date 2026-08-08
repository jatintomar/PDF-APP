package com.pdfutility.tools

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class ZoomRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var mScaleFactor = 1f
    private val scaleGestureDetector: ScaleGestureDetector

    private var mTranslationX = 0f
    private var mTranslationY = 0f
    
    private var lastX = 0f
    private var lastY = 0f
    private var skipNextMove = false

    init {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mScaleFactor *= detector.scaleFactor
                mScaleFactor = max(1f, min(mScaleFactor, 5f))
                
                scaleX = mScaleFactor
                scaleY = mScaleFactor
                
                if (mScaleFactor == 1f) {
                    mTranslationX = 0f
                    mTranslationY = 0f
                    translationX = 0f
                    translationY = 0f
                } else {
                    updateBounds()
                }
                return true
            }
        })
    }

    private fun updateBounds() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val maxTranslateX = (mScaleFactor - 1) * viewWidth / 2
        val maxTranslateY = (mScaleFactor - 1) * viewHeight / 2
        
        mTranslationX = max(-maxTranslateX, min(mTranslationX, maxTranslateX))
        mTranslationY = max(-maxTranslateY, min(mTranslationY, maxTranslateY))
        
        translationX = mTranslationX
        translationY = mTranslationY
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(ev)
        
        val rawX = ev.rawX
        val rawY = ev.rawY

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = rawX
                lastY = rawY
                skipNextMove = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                skipNextMove = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (skipNextMove) {
                    lastX = rawX
                    lastY = rawY
                    skipNextMove = false
                } else if (mScaleFactor > 1f && !scaleGestureDetector.isInProgress && ev.pointerCount == 1) {
                    val dx = rawX - lastX
                    val dy = rawY - lastY
                    
                    val oldTransX = mTranslationX
                    val oldTransY = mTranslationY
                    
                    mTranslationX += dx
                    mTranslationY += dy
                    
                    updateBounds()
                    
                    val consumedX = mTranslationX - oldTransX
                    val consumedY = mTranslationY - oldTransY
                    
                    lastX = rawX
                    lastY = rawY
                    
                    if (consumedX != 0f || consumedY != 0f) {
                        ev.offsetLocation(-consumedX / mScaleFactor, -consumedY / mScaleFactor)
                    }
                } else {
                    lastX = rawX
                    lastY = rawY
                }
            }
        }
        
        val handled = super.dispatchTouchEvent(ev)
        return handled || mScaleFactor > 1f
    }
}
