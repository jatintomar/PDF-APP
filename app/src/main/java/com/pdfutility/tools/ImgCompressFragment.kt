package com.pdfutility.tools

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StepStatus {
    PENDING,
    ACTIVE,
    COMPLETED
}

class ImgCompressFragment : Fragment() {
    private var selectedUri: Uri? = null
    private val selectedUris = mutableListOf<Uri>()
    private var originalBitmap: android.graphics.Bitmap? = null

    // Advanced Configuration Options State
    private var isJpeg = true
    private var targetWidth = 1024
    private var targetHeight = 1024
    private var quality = 80
    private var rotationDegrees = 0
    private var stripMetadata = true
    
    private val selectImgLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            selectedUri = uris[0]
            
            if (uris.size == 1) {
                view?.findViewById<TextView>(R.id.tv_selected_img)?.text = "1 Image Selected"
            } else {
                view?.findViewById<TextView>(R.id.tv_selected_img)?.text = "${uris.size} Images Selected (Batch Compression Active)"
            }
            view?.findViewById<Button>(R.id.btn_compress_img)?.isEnabled = true
            view?.findViewById<View>(R.id.btn_compare_fidelity)?.visibility = View.VISIBLE
            
            // Try to load original dimensions and update crop overlay preview
            try {
                requireContext().contentResolver.openInputStream(uris[0])?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    targetWidth = options.outWidth
                    targetHeight = options.outHeight
                    updateOptionsSummary()
                }
                
                requireContext().contentResolver.openInputStream(uris[0])?.use { stream ->
                    originalBitmap = BitmapFactory.decodeStream(stream)
                    updateCropPreview()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private val saveImgLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { destUri ->
        if (destUri != null) {
            compressImgFile(destUri)
        }
    }

    private val saveZipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destUri ->
        if (destUri != null) {
            compressBatchToZip(destUri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_img_compress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btn_select_img).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectImgLauncher.launch("image/*")
        }
        view.findViewById<Button>(R.id.btn_compress_img).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (selectedUris.size > 1) {
                saveZipLauncher.launch("compressed_images.zip")
            } else {
                val originalName = selectedUris.firstOrNull()?.let { requireContext().getFileName(it) } ?: (if (isJpeg) "image.jpg" else "image.png")
                saveImgLauncher.launch(originalName.addTagToFileName("compressed"))
            }
        }
        
        view.findViewById<Button>(R.id.btn_configure_advanced).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAdvancedSettingsBottomSheet()
        }

        view.findViewById<Button>(R.id.btn_compare_fidelity).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showFidelityComparisonDialog()
        }

        // Handle Crop section visibility toggle
        val cbEnableCrop = view.findViewById<android.widget.CheckBox>(R.id.cb_enable_crop)
        val layoutCropInputs = view.findViewById<View>(R.id.layout_crop_inputs)
        cbEnableCrop?.setOnCheckedChangeListener { _, isChecked ->
            layoutCropInputs?.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        updateOptionsSummary()
    }

    private fun showAdvancedSettingsBottomSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_advanced_compress_settings, null)
        dialog.setContentView(dialogView)

        val rgFormat = dialogView.findViewById<RadioGroup>(R.id.dialog_rg_format)
        val etWidth = dialogView.findViewById<TextInputEditText>(R.id.dialog_et_width)
        val etHeight = dialogView.findViewById<TextInputEditText>(R.id.dialog_et_height)
        val sliderQuality = dialogView.findViewById<Slider>(R.id.dialog_slider_quality)
        val rgRotation = dialogView.findViewById<RadioGroup>(R.id.dialog_rg_rotation)
        val cbStripMetadata = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.dialog_cb_strip_metadata)
        val btnApply = dialogView.findViewById<Button>(R.id.dialog_btn_apply)

        // Pre-populate sheet with current active state
        rgFormat?.check(if (isJpeg) R.id.dialog_rb_jpeg else R.id.dialog_rb_png)
        etWidth?.setText(targetWidth.toString())
        etHeight?.setText(targetHeight.toString())
        sliderQuality?.value = quality.toFloat()
        
        val rotationCheckedId = when (rotationDegrees) {
            90 -> R.id.dialog_rb_rot_90
            180 -> R.id.dialog_rb_rot_180
            270 -> R.id.dialog_rb_rot_270
            else -> R.id.dialog_rb_rot_0
        }
        rgRotation?.check(rotationCheckedId)
        cbStripMetadata?.isChecked = stripMetadata

        btnApply?.setOnClickListener {
            isJpeg = rgFormat?.checkedRadioButtonId == R.id.dialog_rb_jpeg
            targetWidth = etWidth?.text?.toString()?.toIntOrNull() ?: 1024
            targetHeight = etHeight?.text?.toString()?.toIntOrNull() ?: 1024
            quality = sliderQuality?.value?.toInt() ?: 80
            rotationDegrees = when (rgRotation?.checkedRadioButtonId) {
                R.id.dialog_rb_rot_90 -> 90
                R.id.dialog_rb_rot_180 -> 180
                R.id.dialog_rb_rot_270 -> 270
                else -> 0
            }
            stripMetadata = cbStripMetadata?.isChecked ?: true

            updateOptionsSummary()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateOptionsSummary() {
        view?.findViewById<TextView>(R.id.tv_summary_format)?.text = "Format: ${if (isJpeg) "JPEG" else "PNG"}"
        view?.findViewById<TextView>(R.id.tv_summary_resolution)?.text = "Resolution: ${targetWidth} x ${targetHeight} px"
        view?.findViewById<TextView>(R.id.tv_summary_quality)?.text = "Quality: ${quality}%"
        view?.findViewById<TextView>(R.id.tv_summary_rotation)?.text = "Rotation: ${rotationDegrees}°"
        updateCropPreview()
    }

    private fun updateCropPreview() {
        val bitmap = originalBitmap ?: return
        val rotated = if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        view?.findViewById<ImageView>(R.id.iv_crop_preview)?.setImageBitmap(rotated)
        
        val overlay = view?.findViewById<CropOverlayView>(R.id.crop_overlay)
        if (overlay != null) {
            overlay.imageWidth = rotated.width
            overlay.imageHeight = rotated.height
            overlay.setCropValues(0f, 0f, 100f, 100f)
        }
    }

    private fun showFidelityComparisonDialog() {
        val orig = originalBitmap ?: return
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.dialog_fidelity_comparison, null)
        dialog.setContentView(sheetView)

        val splitView = sheetView.findViewById<SplitComparisonView>(R.id.split_comparison_view)
        val slider = sheetView.findViewById<Slider>(R.id.slider_dialog_quality)
        val tvLabel = sheetView.findViewById<TextView>(R.id.tv_dialog_quality_label)
        val btnClose = sheetView.findViewById<Button>(R.id.btn_dialog_close)
        val btnApply = sheetView.findViewById<Button>(R.id.btn_dialog_apply)

        slider.value = quality.toFloat()

        // Initial preview generation
        updateFidelityPreviewBitmaps(splitView, quality, tvLabel)

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateFidelityPreviewBitmaps(splitView, value.toInt(), tvLabel)
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            quality = slider.value.toInt()
            updateOptionsSummary()
            Toast.makeText(requireContext(), "Applied Quality Level: $quality%", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateFidelityPreviewBitmaps(
        splitView: SplitComparisonView,
        q: Int,
        tvLabel: TextView
    ) {
        tvLabel.text = "Live Compression Quality: $q%"
        lifecycleScope.launch {
            val compressed = withContext(Dispatchers.IO) {
                try {
                    val stream = java.io.ByteArrayOutputStream()
                    val format = if (isJpeg) android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG
                    
                    val base = if (rotationDegrees != 0) {
                        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        android.graphics.Bitmap.createBitmap(originalBitmap!!, 0, 0, originalBitmap!!.width, originalBitmap!!.height, matrix, true)
                    } else {
                        originalBitmap!!
                    }

                    base.compress(format, q, stream)
                    val bytes = stream.toByteArray()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (compressed != null) {
                val alignedOriginal = if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    android.graphics.Bitmap.createBitmap(originalBitmap!!, 0, 0, originalBitmap!!.width, originalBitmap!!.height, matrix, true)
                } else {
                    originalBitmap!!
                }
                splitView.setBitmaps(alignedOriginal, compressed)
            }
        }
    }

    private fun showStepProgressDialog(title: String): android.app.AlertDialog {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_step_progress, null)
        dialogView.findViewById<TextView>(R.id.tv_stepper_title)?.text = title
        builder.setView(dialogView)
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        return dialog
    }

    private fun updateStep(dialog: android.app.AlertDialog, step: Int, status: StepStatus) {
        val containerId = when (step) {
            1 -> R.id.step_container_1
            2 -> R.id.step_container_2
            3 -> R.id.step_container_3
            else -> R.id.step_container_4
        }
        val pbId = when (step) {
            1 -> R.id.pb_step_1
            2 -> R.id.pb_step_2
            3 -> R.id.pb_step_3
            else -> R.id.pb_step_4
        }
        val ivDoneId = when (step) {
            1 -> R.id.iv_step_1_done
            2 -> R.id.iv_step_2_done
            3 -> R.id.iv_step_3_done
            else -> R.id.iv_step_4_done
        }

        val container = dialog.findViewById<View>(containerId)
        val progressBar = dialog.findViewById<ProgressBar>(pbId)
        val ivDone = dialog.findViewById<ImageView>(ivDoneId)

        when (status) {
            StepStatus.PENDING -> {
                container?.alpha = 0.5f
                progressBar?.visibility = View.GONE
                ivDone?.visibility = View.GONE
            }
            StepStatus.ACTIVE -> {
                container?.alpha = 1.0f
                progressBar?.visibility = View.VISIBLE
                ivDone?.visibility = View.GONE
            }
            StepStatus.COMPLETED -> {
                container?.alpha = 1.0f
                progressBar?.visibility = View.GONE
                ivDone?.visibility = View.VISIBLE
            }
        }
    }

    private fun compressImgFile(destUri: Uri) {
        val inputUri = selectedUri ?: return
        val format = if (isJpeg) android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG
        
        val isCropEnabled = view?.findViewById<android.widget.CheckBox>(R.id.cb_enable_crop)?.isChecked ?: false
        val cropOverlay = view?.findViewById<CropOverlayView>(R.id.crop_overlay)
        val cropX = if (isCropEnabled) cropOverlay?.cropX?.toDouble() ?: 0.0 else 0.0
        val cropY = if (isCropEnabled) cropOverlay?.cropY?.toDouble() ?: 0.0 else 0.0
        val cropW = if (isCropEnabled) cropOverlay?.cropW?.toDouble() ?: 100.0 else 100.0
        val cropH = if (isCropEnabled) cropOverlay?.cropH?.toDouble() ?: 100.0 else 100.0

        val dialog = showStepProgressDialog("Compressing Image")
        
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    // Step 1: Decode & load image
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 1, StepStatus.ACTIVE)
                    }
                    kotlinx.coroutines.delay(400)
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 1, StepStatus.COMPLETED)
                    }

                    // Step 2: Crop & rotate
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 2, StepStatus.ACTIVE)
                    }
                    kotlinx.coroutines.delay(400)
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 2, StepStatus.COMPLETED)
                    }

                    // Step 3: Compress & Format
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 3, StepStatus.ACTIVE)
                    }
                    val tempFile = java.io.File.createTempFile("img_compress", if (isJpeg) ".jpg" else ".png", requireContext().cacheDir)
                    PdfProcessor.compressImage(
                        requireContext(), 
                        inputUri, 
                        targetWidth, 
                        targetHeight, 
                        quality, 
                        format, 
                        tempFile,
                        cropX,
                        cropY,
                        cropW,
                        cropH,
                        rotationDegrees
                    )
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 3, StepStatus.COMPLETED)
                    }

                    // Step 4: Saving to storage (SAF)
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 4, StepStatus.ACTIVE)
                    }
                    requireContext().contentResolver.openOutputStream(destUri)?.use { out ->
                        tempFile.inputStream().use { it.copyTo(out) }
                    }
                    tempFile.delete()
                    kotlinx.coroutines.delay(400)
                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 4, StepStatus.COMPLETED)
                    }
                    
                    // Retrieve sizes to register with HistoryManager
                    val originalSize = requireContext().contentResolver.openFileDescriptor(inputUri, "r")?.use { 
                        it.statSize 
                    } ?: (1024L * 850)
                    val compressedSize = requireContext().contentResolver.openFileDescriptor(destUri, "r")?.use { 
                        it.statSize 
                    } ?: (1024L * 320)

                    HistoryManager.addHistoryItem(
                        context = requireContext(),
                        name = PdfProcessor.getFileName(requireContext(), inputUri) ?: "compressed_image.jpg",
                        originalSize = originalSize,
                        compressedSize = compressedSize,
                        uriString = destUri.toString(),
                        toolType = "Compress Image"
                    )

                    kotlinx.coroutines.delay(400)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            dialog.dismiss()
            if (success) {
                Toast.makeText(requireContext(), "Image compressed successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Compression failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun compressBatchToZip(destUri: Uri) {
        val format = if (isJpeg) android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG
        val isCropEnabled = view?.findViewById<android.widget.CheckBox>(R.id.cb_enable_crop)?.isChecked ?: false
        val cropOverlay = view?.findViewById<CropOverlayView>(R.id.crop_overlay)
        val cropX = if (isCropEnabled) cropOverlay?.cropX?.toDouble() ?: 0.0 else 0.0
        val cropY = if (isCropEnabled) cropOverlay?.cropY?.toDouble() ?: 0.0 else 0.0
        val cropW = if (isCropEnabled) cropOverlay?.cropW?.toDouble() ?: 100.0 else 100.0
        val cropH = if (isCropEnabled) cropOverlay?.cropH?.toDouble() ?: 100.0 else 100.0

        val dialog = showStepProgressDialog("Batch Compressing Images")

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) { updateStep(dialog, 1, StepStatus.ACTIVE) }
                    val tempFiles = mutableListOf<java.io.File>()
                    val names = mutableListOf<String>()

                    for ((idx, uri) in selectedUris.withIndex()) {
                        val tempFile = java.io.File.createTempFile("batch_img_${idx}", if (isJpeg) ".jpg" else ".png", requireContext().cacheDir)
                        PdfProcessor.compressImage(
                            requireContext(),
                            uri,
                            targetWidth,
                            targetHeight,
                            quality,
                            format,
                            tempFile,
                            cropX,
                            cropY,
                            cropW,
                            cropH,
                            rotationDegrees
                        )
                        tempFiles.add(tempFile)
                        val originalName = PdfProcessor.getFileName(requireContext(), uri) ?: "image_${idx}.jpg"
                        names.add(originalName)
                    }

                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 1, StepStatus.COMPLETED)
                        updateStep(dialog, 2, StepStatus.ACTIVE)
                    }

                    val zipFile = java.io.File.createTempFile("batch_zip", ".zip", requireContext().cacheDir)
                    java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
                        for ((idx, file) in tempFiles.withIndex()) {
                            zos.putNextEntry(java.util.zip.ZipEntry(names[idx]))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            file.delete()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 2, StepStatus.COMPLETED)
                        updateStep(dialog, 3, StepStatus.ACTIVE)
                    }

                    requireContext().contentResolver.openOutputStream(destUri)?.use { out ->
                        zipFile.inputStream().use { it.copyTo(out) }
                    }
                    val originalBatchSize = selectedUris.sumOf { uri ->
                        requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 1024L * 500
                    }
                    val compressedZipSize = zipFile.length()
                    zipFile.delete()

                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 3, StepStatus.COMPLETED)
                        updateStep(dialog, 4, StepStatus.ACTIVE)
                    }

                    HistoryManager.addHistoryItem(
                        context = requireContext(),
                        name = "compressed_images_${System.currentTimeMillis() / 1000}.zip",
                        originalSize = originalBatchSize,
                        compressedSize = compressedZipSize,
                        uriString = destUri.toString(),
                        toolType = "Compress Image"
                    )

                    withContext(Dispatchers.Main) {
                        updateStep(dialog, 4, StepStatus.COMPLETED)
                    }
                    kotlinx.coroutines.delay(400)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            dialog.dismiss()
            if (success) {
                Toast.makeText(requireContext(), "Batch compressed ZIP saved successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Batch compression failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
