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
        
        holder.job = CoroutineScope(Dispatchers.IO).launch {
            try {
                var size = pageSizes[position]
                if (size == null) {
                    size = core.getPageSize(position)
                    pageSizes[position] = size
                }
                
                if (size != null && size.x > 0 && size.y > 0) {
                    val scale = screenWidth.toFloat() / size.x
                    val width = screenWidth
                    val height = (size.y * scale).toInt()
                    
                    if (width > 0 && height > 0) {
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        core.drawPage(bitmap, position, width, height, 0, 0, width, height, null)
                        
                        val q = searchQuery
                        if (!q.isNullOrEmpty() && position == highlightPage) {
                            val boxes = core.searchPage(position, q)
                            if (boxes != null && boxes.isNotEmpty()) {
                                val canvas = Canvas(bitmap)
                                val paint = Paint().apply {
                                    color = Color.argb(128, 255, 255, 0)
                                    style = Paint.Style.FILL
                                }
                                for (boxArray in boxes) {
                                    for (quad in boxArray) {
                                        // quad coordinates are relative to the original page size, so scale them
                                        canvas.drawRect(
                                            quad.ul_x * scale,
                                            quad.ul_y * scale,
                                            quad.lr_x * scale,
                                            quad.lr_y * scale,
                                            paint
                                        )
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
