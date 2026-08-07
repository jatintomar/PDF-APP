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
import com.pdfutility.tools.databinding.FragmentPdfToImgBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfToImgFragment : Fragment() {

    private var _binding: FragmentPdfToImgBinding? = null
    private val binding get() = _binding!!

    private var selectedFileUri: Uri? = null
    private var customStorageUri: Uri? = null

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileName(requireContext(), uri)
            binding.tvSelectedFileName.text = fileName
            binding.btnConvertAction.isEnabled = true
        }
    }

    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                customStorageUri = uri
                binding.tvStorageLocation.text = "Save Location: SAF ➔ Selected Folder"
                Toast.makeText(requireContext(), "Folder selected successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to get persistent permissions.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfToImgBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectFile.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.btnSelectStorageFolder.setOnClickListener {
            selectFolderLauncher.launch(null)
        }

        binding.btnConvertAction.setOnClickListener {
            if (selectedFileUri != null) {
                processPdfToImages(selectedFileUri!!)
            }
        }
    }

    private fun processPdfToImages(inputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnConvertAction.isEnabled = false
        binding.btnSelectFile.isEnabled = false

        val selectedFormat = when (binding.rgImageFormat.checkedRadioButtonId) {
            R.id.rb_jpeg -> "JPEG"
            R.id.rb_webp -> "WEBP"
            else -> "PNG"
        }

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openInputStream(inputUri).use { inputStream ->
                        if (inputStream == null) return@withContext 0
                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                            PdfProcessor.cleanWatermark(document)
                            val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)
                            val totalPages = document.numberOfPages
                            
                            val targetTree = customStorageUri?.let { uri ->
                                androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), uri)
                            }

                            val outputDir = if (targetTree == null) {
                                val dir = File(requireContext().getExternalFilesDir(null), "PDF_Pages_${System.currentTimeMillis()}")
                                if (!dir.exists()) dir.mkdirs()
                                dir
                            } else null

                            val compressFormat = when (selectedFormat) {
                                "JPEG" -> android.graphics.Bitmap.CompressFormat.JPEG
                                "WEBP" -> android.graphics.Bitmap.CompressFormat.WEBP
                                else -> android.graphics.Bitmap.CompressFormat.PNG
                            }
                            val ext = selectedFormat.lowercase()

                            for (i in 0 until totalPages) {
                                val bitmap = renderer.renderImageWithDPI(i, 150f, com.tom_roush.pdfbox.rendering.ImageType.ARGB)
                                if (targetTree != null) {
                                    val mimeType = "image/$ext"
                                    val docFile = targetTree.createFile(mimeType, "page_${i + 1}")
                                    if (docFile != null) {
                                        requireContext().contentResolver.openOutputStream(docFile.uri).use { out ->
                                            if (out != null) {
                                                bitmap.compress(compressFormat, 100, out)
                                            }
                                        }
                                    }
                                } else {
                                    val imageFile = File(outputDir!!, "page_${i + 1}.$ext")
                                    java.io.FileOutputStream(imageFile).use { out ->
                                        bitmap.compress(compressFormat, 100, out)
                                    }
                                }
                            }
                            totalPages
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    0
                }
            }

            binding.progressContainer.visibility = View.GONE
            binding.btnSelectFile.isEnabled = true
            binding.btnConvertAction.isEnabled = true

            if (count > 0) {
                val destMessage = if (customStorageUri != null) "Saved directly in selected SAF Folder!" else "Saved in App External Files directory."
                Toast.makeText(requireContext(), "Successfully rendered $count pages to $selectedFormat! $destMessage", Toast.LENGTH_LONG).show()
                selectedFileUri = null
                binding.tvSelectedFileName.text = "No file selected"
                binding.btnConvertAction.isEnabled = false
            } else {
                Toast.makeText(requireContext(), "Failed to convert PDF pages to images.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document.pdf"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
