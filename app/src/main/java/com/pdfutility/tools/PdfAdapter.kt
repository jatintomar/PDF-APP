package com.pdfutility.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pdfutility.tools.databinding.ItemPdfFileBinding

class PdfAdapter(
    private val files: MutableList<PdfFile>,
    private val onDeleteClick: (PdfFile) -> Unit
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    class PdfViewHolder(val binding: ItemPdfFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val binding = ItemPdfFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val file = files[position]
        holder.binding.tvFileName.text = file.name
        holder.binding.btnDeleteFile.setOnClickListener {
            onDeleteClick(file)
        }

        // Move item Up click handler
        holder.binding.btnMoveUp.isEnabled = position > 0
        holder.binding.btnMoveUp.alpha = if (position > 0) 1.0f else 0.3f
        holder.binding.btnMoveUp.setOnClickListener {
            val from = holder.adapterPosition
            if (from > 0) {
                val temp = files[from]
                files[from] = files[from - 1]
                files[from - 1] = temp
                notifyItemMoved(from, from - 1)
                notifyItemRangeChanged(from - 1, 2)
            }
        }

        // Move item Down click handler
        holder.binding.btnMoveDown.isEnabled = position < files.size - 1
        holder.binding.btnMoveDown.alpha = if (position < files.size - 1) 1.0f else 0.3f
        holder.binding.btnMoveDown.setOnClickListener {
            val from = holder.adapterPosition
            if (from in 0 until files.size - 1) {
                val temp = files[from]
                files[from] = files[from + 1]
                files[from + 1] = temp
                notifyItemMoved(from, from + 1)
                notifyItemRangeChanged(from, 2)
            }
        }
    }

    override fun getItemCount(): Int = files.size

    fun updateList(newFiles: List<PdfFile>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val movedItem = files.removeAt(fromPosition)
        files.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
    }
}
