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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pdfutility.tools.databinding.FragmentLockBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LockFragment : Fragment() {

    private var _binding: FragmentLockBinding? = null
    private val binding get() = _binding!!

    private var selectedFileUri: Uri? = null

    // Launcher to select a PDF file
    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileName(requireContext(), uri)
            binding.tvSelectedFileName.text = fileName
            validateInputs()
        }
    }

    // Launcher to choose where to save the encrypted PDF
    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && selectedFileUri != null) {
            val password = binding.etPassword.text.toString()
            processLockPdf(selectedFileUri!!, password, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectFile.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.etPassword.addTextChangedListener {
            validateInputs()
        }

        binding.btnLockAction.setOnClickListener {
            val originalName = selectedFileUri?.let { getFileName(requireContext(), it) } ?: "document.pdf"
            val defaultName = originalName.replace(".pdf", "_locked.pdf")
            savePdfLauncher.launch(defaultName)
        }
    }

    private fun validateInputs() {
        val hasFile = selectedFileUri != null
        val hasPassword = !binding.etPassword.text.isNullOrBlank()
        binding.btnLockAction.isEnabled = hasFile && hasPassword
    }

    private fun processLockPdf(inputUri: Uri, password: String, outputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnLockAction.isEnabled = false
        binding.btnSelectFile.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    // Create a temp file to run lock operation on
                    val tempOutputFile = File.createTempFile("encrypted_temp_", ".pdf", requireContext().cacheDir)
                    
                    // Lock the PDF
                    PdfProcessor.lockPdf(requireContext(), inputUri, password, tempOutputFile)
                    
                    // Write temp file to destination Uri
                    requireContext().contentResolver.openOutputStream(outputUri).use { out ->
                        if (out == null) throw Exception("Cannot open destination stream")
                        tempOutputFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    
                    // Cleanup temp file
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
                Toast.makeText(requireContext(), "PDF Encrypted & Saved successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to lock PDF. Verify file and password.", Toast.LENGTH_LONG).show()
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
