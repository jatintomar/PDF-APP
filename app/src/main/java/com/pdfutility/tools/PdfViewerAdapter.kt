package com.pdfutility.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.viewer.MuPDFCore
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class PdfViewerAdapter(
    private val context: Context,
    private val core: MuPDFCore,
    private val screenWidth: Int,
    private val onPageClick: (pageIndex: Int, x: Float, y: Float, viewWidth: Float, viewHeight: Float) -> Unit
) : RecyclerView.Adapter<PdfViewerAdapter.PageViewHolder>() {

    private val pageSizes = ConcurrentHashMap<Int, PointF>()
    private var searchQuery: String? = null
    private var highlightPage: Int = -1
    
    fun setSearchQuery(query: String?, activePage: Int = -1) {
        searchQuery = query
        highlightPage = activePage
        notifyDataSetChanged()
    }

    inner class PageViewHolder(val cardView: View, val imageView: ImageView) : RecyclerView.ViewHolder(cardView) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val cardView = com.google.android.material.card.MaterialCardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                val density = context.resources.displayMetrics.density
                setMargins(
                    (16 * density).toInt(),
                    (8 * density).toInt(),
                    (16 * density).toInt(),
                    (8 * density).toInt()
                )
            }
            cardElevation = 3 * context.resources.displayMetrics.density
            radius = 6 * context.resources.displayMetrics.density
            strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            preventCornerOverlap = true
            useCompatPadding = false
        }
        
        val imageView = ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        
        cardView.addView(imageView)
        return PageViewHolder(cardView, imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.imageView.setImageBitmap(null)
        holder.job?.cancel()
        
        var lastTouchX = 0f
        var lastTouchY = 0f
        
        holder.imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }
        
        holder.imageView.setOnClickListener { view ->
            onPageClick(position, lastTouchX, lastTouchY, view.width.toFloat(), view.height.toFloat())
        }
        
        holder.job = CoroutineScope(Dispatchers.IO).launch {
            try {
                var size = pageSizes[position]
                if (size == null) {
                    size = core.getPageSize(position)
                    pageSizes[position] = size
                }
                
                if (size != null && size.x > 0 && size.y > 0) {
                    val density = context.resources.displayMetrics.density
                    // Limit the maximum width of the rendered bitmap for small page sizes (like receipt/image PDFs)
                    // so we do not stretch them to the extreme screen width boundaries
                    val targetWidth = minOf(screenWidth, (size.x * density * 1.3f).toInt())
                    val scale = targetWidth.toFloat() / size.x
                    val width = targetWidth
                    val height = (size.y * scale).toInt()
                    
                    if (width > 0 && height > 0) {
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        core.drawPage(bitmap, position, width, height, 0, 0, width, height, null)
                        
                        val q = searchQuery?.trim()
                        if (!q.isNullOrEmpty()) {
                            // Find all match boxes with case variations
                            var boxes = core.searchPage(position, q)
                            if (boxes.isNullOrEmpty()) {
                                boxes = core.searchPage(position, q.lowercase())
                            }
                            if (boxes.isNullOrEmpty()) {
                                boxes = core.searchPage(position, q.uppercase())
                            }
                            if (boxes.isNullOrEmpty()) {
                                boxes = core.searchPage(position, q.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                            }
                            
                            if (!boxes.isNullOrEmpty()) {
                                val canvas = Canvas(bitmap)
                                val isCurrentFocusedPage = (position == highlightPage)
                                
                                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = if (isCurrentFocusedPage) Color.argb(160, 255, 170, 0) else Color.argb(120, 255, 235, 59)
                                    style = Paint.Style.FILL
                                }
                                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = if (isCurrentFocusedPage) Color.argb(220, 230, 81, 0) else Color.argb(150, 251, 192, 45)
                                    style = Paint.Style.STROKE
                                    strokeWidth = 2f * density
                                }

                                for (boxArray in boxes) {
                                    for (quad in boxArray) {
                                        val left = minOf(quad.ul_x, quad.ll_x, quad.ur_x, quad.lr_x) * scale
                                        val right = maxOf(quad.ul_x, quad.ll_x, quad.ur_x, quad.lr_x) * scale
                                        val top = minOf(quad.ul_y, quad.ll_y, quad.ur_y, quad.lr_y) * scale
                                        val bottom = maxOf(quad.ul_y, quad.ll_y, quad.ur_y, quad.lr_y) * scale
                                        
                                        if (right > left && bottom > top) {
                                            val rect = android.graphics.RectF(left - 2f, top - 1f, right + 2f, bottom + 1f)
                                            canvas.drawRoundRect(rect, 3f * density, 3f * density, fillPaint)
                                            canvas.drawRoundRect(rect, 3f * density, 3f * density, strokePaint)
                                        }
                                    }
                                }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            holder.imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int = core.countPages()
}
