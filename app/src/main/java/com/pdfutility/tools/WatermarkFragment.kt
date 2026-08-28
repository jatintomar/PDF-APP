package com.pdfutility.tools

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pdfutility.tools.databinding.FragmentWatermarkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class WatermarkFragment : Fragment() {

    private var _binding: FragmentWatermarkBinding? = null
    private val binding get() = _binding!!

    private var selectedFileUri: Uri? = null

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = PdfProcessor.getFileName(requireContext(), uri)
            binding.tvSelectedFileName.text = fileName
            validateInputs()
        }
    }

    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && selectedFileUri != null) {
            val text = binding.etWatermarkText.text.toString().trim()
            processWatermarkPdf(selectedFileUri!!, text, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatermarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Watermark PDF"

        binding.btnSelectFile.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.etWatermarkText.addTextChangedListener {
            validateInputs()
        }

        binding.btnWatermarkAction.setOnClickListener {
            val originalName = selectedFileUri?.let { PdfProcessor.getFileName(requireContext(), it) } ?: "document.pdf"
            val defaultName = originalName.replace(".pdf", "_watermarked.pdf")
            savePdfLauncher.launch(defaultName)
        }
    }

    private fun validateInputs() {
        val hasFile = selectedFileUri != null
        val hasText = !binding.etWatermarkText.text.isNullOrBlank()
        binding.btnWatermarkAction.isEnabled = hasFile && hasText
    }

    private fun processWatermarkPdf(inputUri: Uri, watermarkText: String, outputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnWatermarkAction.isEnabled = false
        binding.btnSelectFile.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempOutputFile = File.createTempFile("watermarked_temp_", ".pdf", requireContext().cacheDir)
                    PdfProcessor.watermarkPdf(requireContext(), inputUri, watermarkText, tempOutputFile)

                    requireContext().contentResolver.openOutputStream(outputUri).use { out ->
                        if (out == null) throw Exception("Cannot open destination stream")
                        tempOutputFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    tempOutputFile.delete()
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            binding.progressContainer.visibility = View.GONE
            binding.btnWatermarkAction.isEnabled = true
            binding.btnSelectFile.isEnabled = true

            if (success) {
                val outName = PdfProcessor.getFileName(requireContext(), outputUri)
                HistoryManager.recordAction(
                    context = requireContext(),
                    actionType = "WATERMARK",
                    name = outName,
                    uriString = outputUri.toString(),
                    details = "Watermark: $watermarkText"
                )
                Toast.makeText(requireContext(), "Watermark Applied Successfully!", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "Failed to apply watermark", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
