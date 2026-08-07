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
import com.pdfutility.tools.databinding.FragmentImgToPdfBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImgToPdfFragment : Fragment() {

    private var _binding: FragmentImgToPdfBinding? = null
    private val binding get() = _binding!!

    private val selectedImageUris = mutableListOf<Uri>()

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(uris)
            binding.tvSelectedImagesCount.text = "${uris.size} image(s) selected"
            binding.btnConvertAction.isEnabled = true
        }
    }

    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && selectedImageUris.isNotEmpty()) {
            val fitToPage = binding.cbFitToPage.isChecked
            processImagesToPdf(selectedImageUris, fitToPage, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImgToPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectImages.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        binding.btnConvertAction.setOnClickListener {
            val originalName = selectedImageUris.firstOrNull()?.let { requireContext().getFileName(it) } ?: "images.jpg"
            savePdfLauncher.launch(originalName.addTagToFileName("converted_to_pdf").replace(".jpg", ".pdf").replace(".png", ".pdf"))
        }
    }

    private fun processImagesToPdf(imageUris: List<Uri>, fitToPage: Boolean, outputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnConvertAction.isEnabled = false
        binding.btnSelectImages.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempOutputFile = File.createTempFile("img_pdf_temp_", ".pdf", requireContext().cacheDir)
                    
                    // Convert images to PDF
                    PdfProcessor.imgToPdf(requireContext(), imageUris, fitToPage, tempOutputFile)
                    
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
            binding.btnSelectImages.isEnabled = true
            binding.btnConvertAction.isEnabled = true

            if (success) {
                Toast.makeText(requireContext(), "PDF Created from Images successfully!", Toast.LENGTH_LONG).show()
                selectedImageUris.clear()
                binding.tvSelectedImagesCount.text = "No images selected"
                binding.btnConvertAction.isEnabled = false
            } else {
                Toast.makeText(requireContext(), "Failed to convert images to PDF.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
