package com.pdfutility.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class PdfViewerAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val renderer: PdfRenderer,
    private val screenWidth: Int,
    private val onPageClick: (pageIndex: Int, x: Float, y: Float, viewWidth: Float, viewHeight: Float) -> Unit,
    private val onPageLongClick: (pageIndex: Int) -> Unit
) : RecyclerView.Adapter<PdfViewerAdapter.PageViewHolder>() {

    private val pageSizes = ConcurrentHashMap<Int, PointF>()
    private var searchQuery: String? = null
    private var highlightPage: Int = -1
    
    fun setSearchQuery(query: String?, activePage: Int = -1) {
        searchQuery = query
        highlightPage = activePage
        notifyDataSetChanged()
    }

    inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(context)
        imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = true
        return PageViewHolder(imageView)
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
        
        holder.imageView.setOnLongClickListener {
            onPageLongClick(position)
            true
        }
        
        holder.job = scope.launch(Dispatchers.IO) {
            try {
                var size = pageSizes[position]
                if (size == null) {
                    synchronized(renderer) {
                        try {
                            if (position in 0 until renderer.pageCount) {
                                val page = renderer.openPage(position)
                                size = PointF(page.width.toFloat(), page.height.toFloat())
                                page.close()
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                    if (size != null) {
                        pageSizes[position] = size!!
                    }
                }
                
                val currentSize = size
                if (currentSize != null && currentSize.x > 0 && currentSize.y > 0) {
                    val scale = screenWidth.toFloat() / currentSize.x
                    val width = screenWidth
                    val height = (currentSize.y * scale).toInt()
                    
                    if (width > 0 && height > 0) {
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        
                        synchronized(renderer) {
                            try {
                                if (position in 0 until renderer.pageCount) {
                                    val page = renderer.openPage(position)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()
                                }
                            } catch (t: Throwable) {
                                t.printStackTrace()
                            }
                        }
                        
                        val q = searchQuery
                        if (!q.isNullOrEmpty() && position == highlightPage) {
                            val canvas = Canvas(bitmap)
                            val paint = Paint().apply {
                                color = Color.parseColor("#331D4ED8") // 20% alpha primary blue
                                style = Paint.Style.FILL
                            }
                            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                            
                            val borderPaint = Paint().apply {
                                color = Color.parseColor("#1D4ED8") // primary blue
                                style = Paint.Style.STROKE
                                strokeWidth = 12f
                            }
                            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
                        }
                        
                        withContext(Dispatchers.Main) {
                            holder.imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        try {
            synchronized(renderer) {
                count = renderer.pageCount
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return count
    }
}
