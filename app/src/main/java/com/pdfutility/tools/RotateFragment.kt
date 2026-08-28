package com.pdfutility.tools

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pdfutility.tools.databinding.FragmentRotateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RotateFragment : Fragment() {

    private var _binding: FragmentRotateBinding? = null
    private val binding get() = _binding!!

    private var selectedFileUri: Uri? = null

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = PdfProcessor.getFileName(requireContext(), uri)
            binding.tvSelectedFileName.text = fileName
            binding.btnRotateAction.isEnabled = true
        }
    }

    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && selectedFileUri != null) {
            val angle = when (binding.rgRotationAngle.checkedRadioButtonId) {
                binding.rbRotate180.id -> 180
                binding.rbRotate270.id -> 270
                else -> 90
            }
            processRotatePdf(selectedFileUri!!, angle, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRotateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Rotate PDF"

        binding.btnSelectFile.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.btnRotateAction.setOnClickListener {
            val originalName = selectedFileUri?.let { PdfProcessor.getFileName(requireContext(), it) } ?: "document.pdf"
            val defaultName = originalName.replace(".pdf", "_rotated.pdf")
            savePdfLauncher.launch(defaultName)
        }
    }

    private fun processRotatePdf(inputUri: Uri, angle: Int, outputUri: Uri) {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnRotateAction.isEnabled = false
        binding.btnSelectFile.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val tempOutputFile = File.createTempFile("rotated_temp_", ".pdf", requireContext().cacheDir)
                    PdfProcessor.rotatePdf(requireContext(), inputUri, angle, tempOutputFile)

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
            binding.btnRotateAction.isEnabled = true
            binding.btnSelectFile.isEnabled = true

            if (success) {
                val outName = PdfProcessor.getFileName(requireContext(), outputUri)
                HistoryManager.recordAction(
                    context = requireContext(),
                    actionType = "ROTATE",
                    name = outName,
                    uriString = outputUri.toString(),
                    details = "Rotated $angle°"
                )
                Toast.makeText(requireContext(), "PDF Rotated Successfully!", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "Failed to rotate PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
