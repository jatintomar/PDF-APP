package com.pdfutility.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class PdfViewerAdapter(
    private val context: Context,
    private val renderer: PdfRenderer,
    private val screenWidth: Int,
    private val onPageClick: (pageIndex: Int, x: Float, y: Float, viewWidth: Float, viewHeight: Float) -> Unit,
    private val onPageLongClick: (pageIndex: Int) -> Unit
) : RecyclerView.Adapter<PdfViewerAdapter.PageViewHolder>() {

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun onDestroy() {
        adapterScope.cancel()
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.job?.cancel()
        holder.imageView.setImageBitmap(null)
    }

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
        
        holder.job = adapterScope.launch {
            try {
                var size = pageSizes[position]
                if (size == null) {
                    withContext(Dispatchers.IO) {
                        synchronized(renderer) {
                            if (position in 0 until renderer.pageCount) {
                                val page = renderer.openPage(position)
                                val newSize = PointF(page.width.toFloat(), page.height.toFloat())
                                pageSizes[position] = newSize
                                size = newSize
                                page.close()
                            }
                        }
                    }
                }
                
                val finalSize = size
                if (finalSize != null && finalSize.x > 0 && finalSize.y > 0) {
                    val scale = screenWidth.toFloat() / finalSize.x
                    val width = screenWidth
                    val height = (finalSize.y * scale).toInt()
                    
                    if (width > 0 && height > 0) {
                        val bitmap = withContext(Dispatchers.IO) {
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(Color.WHITE)
                                synchronized(renderer) {
                                    if (position in 0 until renderer.pageCount) {
                                        val page = renderer.openPage(position)
                                        page.render(this@apply, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()
                                    }
                                }
                            }
                        }
                        
                        val q = searchQuery
                        if (!q.isNullOrEmpty() && position == highlightPage) {
                            val canvas = Canvas(bitmap)
                            val paint = Paint().apply {
                                color = Color.argb(40, 255, 255, 0) // Visual highlight overlay for the page
                                style = Paint.Style.FILL
                            }
                            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                        }
                        
                        holder.imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int {
        return try {
            renderer.pageCount
        } catch (e: Exception) {
            0
        }
    }
}
