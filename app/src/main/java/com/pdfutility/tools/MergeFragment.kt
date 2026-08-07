package com.pdfutility.tools

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdfutility.tools.databinding.FragmentMergeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MergeFragment : Fragment() {

    private var _binding: FragmentMergeBinding? = null
    private val binding get() = _binding!!

    private val selectedFiles = mutableListOf<PdfFile>()
    private lateinit var adapter: PdfAdapter

    // Launcher to select multiple PDF files
    private val pickPdfsLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            val newFiles = uris.map { uri ->
                val name = getFileName(requireContext(), uri)
                val sizeStr = getFileSizeString(requireContext(), uri)
                PdfFile(UUID.randomUUID().toString(), name, uri, sizeStr)
            }
            selectedFiles.addAll(newFiles)
            adapter.updateList(selectedFiles)
            validateMergeButton()
        }
    }

    // Launcher to choose where to save the merged PDF
    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && selectedFiles.isNotEmpty()) {
            processMergePdfs(selectedFiles, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMergeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.btnSelectFiles.setOnClickListener {
            pickPdfsLauncher.launch("application/pdf")
        }

        binding.btnMergeAction.setOnClickListener {
            val originalName = selectedFiles.firstOrNull()?.uri?.let { requireContext().getFileName(it) } ?: "document.pdf"
            savePdfLauncher.launch(originalName.addTagToFileName("merged"))
        }
    }

    private fun setupRecyclerView() {
        adapter = PdfAdapter(selectedFiles) { fileToDelete ->
            selectedFiles.remove(fileToDelete)
            adapter.updateList(selectedFiles)
            validateMergeButton()
        }

        binding.rvPdfFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPdfFiles.adapter = adapter

        // Standard touch helper for reordering list items with standard drag gestures
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.onItemMove(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvPdfFiles)
    }

    private fun validateMergeButton() {
        binding.btnMergeAction.isEnabled = selectedFiles.size >= 2
    }

    private fun processMergePdfs(files: List<PdfFile>, outputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnMergeAction.isEnabled = false
        binding.btnSelectFiles.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempOutputFile = File.createTempFile("merged_temp_", ".pdf", requireContext().cacheDir)
                    
                    // Merge PDFs
                    PdfProcessor.mergePdfs(requireContext(), files, tempOutputFile)
                    
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
            binding.btnSelectFiles.isEnabled = true
            validateMergeButton()

            if (success) {
                Toast.makeText(requireContext(), "PDFs Merged & Saved successfully!", Toast.LENGTH_LONG).show()
                selectedFiles.clear()
                adapter.updateList(selectedFiles)
                validateMergeButton()
            } else {
                Toast.makeText(requireContext(), "Failed to merge PDFs. Verify your files are valid.", Toast.LENGTH_LONG).show()
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

    private fun getFileSizeString(context: Context, uri: Uri): String {
        var size: Long = 0
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0) {
                        size = cursor.getLong(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        return if (size > 0) "${size / 1024} KB" else "Unknown Size"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
