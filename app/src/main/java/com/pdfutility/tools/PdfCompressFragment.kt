package com.pdfutility.tools

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfCompressFragment : Fragment() {
    private var selectedUri: Uri? = null
    
    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            view?.findViewById<TextView>(R.id.tv_selected_file)?.text = "File Selected"
            view?.findViewById<Button>(R.id.btn_compress)?.isEnabled = true
        }
    }

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destUri ->
        if (destUri != null) {
            compressPdfFile(destUri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pdf_compress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btn_select_pdf).setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }
        view.findViewById<Button>(R.id.btn_compress).setOnClickListener {
            val originalName = selectedUri?.let { requireContext().getFileName(it) } ?: "document.pdf"
            savePdfLauncher.launch(originalName.addTagToFileName("compressed"))
        }
    }

    private fun compressPdfFile(destUri: Uri) {
        val inputUri = selectedUri ?: return
        val quality = view?.findViewById<Slider>(R.id.slider_quality)?.value?.toInt() ?: 50
        val progressBar = view?.findViewById<ProgressBar>(R.id.progress_bar)
        
        progressBar?.visibility = View.VISIBLE
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempFile = java.io.File.createTempFile("compress", ".pdf", requireContext().cacheDir)
                    PdfProcessor.compressPdf(requireContext(), inputUri, quality, tempFile)
                    
                    requireContext().contentResolver.openOutputStream(destUri)?.use { out ->
                        tempFile.inputStream().use { it.copyTo(out) }
                    }
                    tempFile.delete()
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            progressBar?.visibility = View.GONE
            if (success) {
                Toast.makeText(requireContext(), "PDF compressed successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Compression failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
