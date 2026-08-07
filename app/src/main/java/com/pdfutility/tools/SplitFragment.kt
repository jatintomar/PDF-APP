package com.pdfutility.tools

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pdfutility.tools.databinding.FragmentSplitBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SplitFragment : Fragment() {

    private var _binding: FragmentSplitBinding? = null
    private val binding get() = _binding!!

    private var selectedFileUri: Uri? = null
    private var totalPdfPages = 0
    private val selectedPages = mutableSetOf<Int>()

    // Launcher to select a PDF file
    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileName(requireContext(), uri)
            binding.tvSelectedFileName.text = fileName
            selectedPages.clear()
            validateInputs()

            // Fetch page count and show grid picker
            lifecycleScope.launch {
                val pageCount = withContext(Dispatchers.IO) {
                    try {
                        requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                                renderer.pageCount
                            }
                        } ?: 0
                    } catch (e: Exception) {
                        e.printStackTrace()
                        0
                    }
                }
                totalPdfPages = pageCount
                if (pageCount > 0) {
                    binding.etStartPage.setText("1")
                    binding.etEndPage.setText(pageCount.toString())
                    binding.layoutGridPicker.visibility = View.VISIBLE
                    setupPageGrid()
                } else {
                    binding.layoutGridPicker.visibility = View.GONE
                }
            }
        }
    }

    private fun setupPageGrid() {
        val uri = selectedFileUri ?: return
        binding.rvSplitPageGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
        binding.rvSplitPageGrid.adapter = SplitPageAdapter(
            pageCount = totalPdfPages,
            pdfUri = uri,
            selectedPages = selectedPages,
            onPageToggle = { pageNum ->
                if (selectedPages.contains(pageNum)) {
                    selectedPages.remove(pageNum)
                } else {
                    selectedPages.add(pageNum)
                }
                binding.rvSplitPageGrid.adapter?.notifyDataSetChanged()

                // Dynamically update manual inputs to cover the selected range
                if (selectedPages.isNotEmpty()) {
                    binding.etStartPage.setText(selectedPages.minOrNull().toString())
                    binding.etEndPage.setText(selectedPages.maxOrNull().toString())
                }
            }
        )
    }

    // Launcher to choose where to save the split PDF
    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && selectedFileUri != null) {
            val startPageStr = binding.etStartPage.text.toString()
            val endPageStr = binding.etEndPage.text.toString()
            
            val startPage = startPageStr.toIntOrNull() ?: 1
            val endPage = endPageStr.toIntOrNull() ?: 1

            processSplitPdf(selectedFileUri!!, startPage, endPage, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectFile.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.btnSplitAction.setOnClickListener {
            val startPage = binding.etStartPage.text.toString().toIntOrNull()
            val endPage = binding.etEndPage.text.toString().toIntOrNull()

            if (startPage == null || endPage == null || startPage <= 0 || endPage <= 0 || startPage > endPage) {
                Toast.makeText(requireContext(), "Please enter a valid page range (Start must be <= End)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val originalName = selectedFileUri?.let { getFileName(requireContext(), it) } ?: "document.pdf"
            val defaultName = originalName.replace(".pdf", "_pages_${startPage}_to_${endPage}.pdf")
            savePdfLauncher.launch(defaultName)
        }
    }

    private fun validateInputs() {
        binding.btnSplitAction.isEnabled = selectedFileUri != null
    }

    private fun processSplitPdf(inputUri: Uri, startPage: Int, endPage: Int, outputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnSplitAction.isEnabled = false
        binding.btnSelectFile.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempOutputFile = File.createTempFile("split_temp_", ".pdf", requireContext().cacheDir)
                    
                    if (selectedPages.isNotEmpty()) {
                        // Extract exactly the selected pages in order
                        val sortedPagesList = selectedPages.toList().sorted()
                        PdfProcessor.splitPdfCustom(requireContext(), inputUri, sortedPagesList, tempOutputFile)
                    } else {
                        // Split by continuous range
                        PdfProcessor.splitPdf(requireContext(), inputUri, startPage, endPage, tempOutputFile)
                    }
                    
                    // Copy to destination Uri
                    requireContext().contentResolver.openOutputStream(outputUri).use { out ->
                        if (out == null) throw Exception("Cannot open destination stream")
                        tempOutputFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }

                    if (tempOutputFile.exists()) {
                        tempOutputFile.delete()
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            binding.progressContainer.visibility = View.GONE
            binding.btnSelectFile.isEnabled = true
            validateInputs()

            if (success) {
                Toast.makeText(requireContext(), "Pages extracted & saved successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to split PDF. Check if page range is within the document bounds.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        if (result == null) {
            try {
                result = uri.path
                val cut = result?.lastIndexOf('/') ?: -1
                if (cut != -1) {
                    result = result?.substring(cut + 1)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        return result ?: "document.pdf"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class SplitPageAdapter(
    private val pageCount: Int,
    private val pdfUri: Uri,
    private val selectedPages: Set<Int>,
    private val onPageToggle: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<SplitPageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.card_thumbnail_container)
        val ivThumbnail: ImageView = view.findViewById(R.id.iv_split_page_thumb)
        val ivIndicator: ImageView = view.findViewById(R.id.iv_selection_indicator)
        val tvPageNum: android.widget.TextView = view.findViewById(R.id.tv_split_page_num)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_split_page_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pageNum = position + 1
        holder.tvPageNum.text = "Page $pageNum"

        val isSelected = selectedPages.contains(pageNum)
        if (isSelected) {
            holder.card.strokeColor = android.graphics.Color.parseColor("#2196F3")
            holder.ivIndicator.setImageResource(android.R.drawable.checkbox_on_background)
            holder.ivIndicator.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3"))
        } else {
            holder.card.strokeColor = android.graphics.Color.parseColor("#E2E8F0")
            holder.ivIndicator.setImageResource(android.R.drawable.checkbox_off_background)
            holder.ivIndicator.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#94A3B8"))
        }

        holder.itemView.setOnClickListener {
            onPageToggle(pageNum)
        }

        // Asynchronously render low-res preview of the page
        holder.ivThumbnail.setImageBitmap(null)
        val context = holder.itemView.context
        val scope = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope
        scope?.launch(Dispatchers.IO) {
            try {
                // Open file descriptor for rendering
                context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                        if (position < renderer.pageCount) {
                            renderer.openPage(position).use { page ->
                                val bitmap = android.graphics.Bitmap.createBitmap(120, 160, android.graphics.Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                withContext(Dispatchers.Main) {
                                    holder.ivThumbnail.setImageBitmap(bitmap)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int = pageCount
}
