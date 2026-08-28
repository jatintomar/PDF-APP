package com.pdfutility.tools

import android.graphics.Bitmap
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
import com.google.android.material.chip.Chip
import com.pdfutility.tools.databinding.FragmentImageToolboxBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageToolboxFragment : Fragment() {

    private var _binding: FragmentImageToolboxBinding? = null
    private val binding get() = _binding!!

    private val selectedUris = mutableListOf<Uri>()
    private var loadedBitmaps = mutableListOf<Bitmap>()
    private var baseWorkingBitmap: Bitmap? = null
    private var renderedPreviewBitmap: Bitmap? = null

    private var filterParams = ImageProcessor.FilterParams()
    private var watermarkText: String = ""
    private var currentFormat = ImageProcessor.ImageFormat.JPEG
    private var currentQuality = 85
    private var isStitching = false
    private var stitchOrientation = ImageProcessor.StitchOrientation.VERTICAL

    private var renderJob: Job? = null

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            loadSelectedImages()
        }
    }

    private val saveImageLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
    ) { uri: Uri? ->
        if (uri != null) {
            processAndSaveImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageToolboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Image Toolbox"

        setupClicks()
        setupFilterChips()
        setupSliders()
    }

    private fun setupClicks() {
        binding.btnPickImages.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        binding.btnChangeImages.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        binding.btnRotateCw.setOnClickListener {
            val newAngle = (filterParams.rotationAngle + 90) % 360
            filterParams = filterParams.copy(rotationAngle = newAngle)
            schedulePreviewUpdate()
        }

        binding.btnFlipH.setOnClickListener {
            filterParams = filterParams.copy(flipHorizontal = !filterParams.flipHorizontal)
            schedulePreviewUpdate()
        }

        binding.btnFlipV.setOnClickListener {
            filterParams = filterParams.copy(flipVertical = !filterParams.flipVertical)
            schedulePreviewUpdate()
        }

        binding.rgStitchDirection.setOnCheckedChangeListener { _, checkedId ->
            stitchOrientation = if (checkedId == binding.rbStitchHorizontal.id) {
                ImageProcessor.StitchOrientation.HORIZONTAL
            } else {
                ImageProcessor.StitchOrientation.VERTICAL
            }
            if (selectedUris.size > 1) {
                rebuildStitchedBase()
            }
        }

        binding.etWatermark.addTextChangedListener {
            watermarkText = it?.toString()?.trim() ?: ""
            schedulePreviewUpdate()
        }

        binding.rgFormat.setOnCheckedChangeListener { _, checkedId ->
            currentFormat = when (checkedId) {
                binding.rbFormat_png.id -> ImageProcessor.ImageFormat.PNG
                binding.rbFormat_webp.id -> ImageProcessor.ImageFormat.WEBP
                else -> ImageProcessor.ImageFormat.JPEG
            }
        }

        binding.btnSaveProcessed.setOnClickListener {
            val originalName = selectedUris.firstOrNull()?.let { requireContext().getFileName(it) } ?: "image.jpg"
            val baseName = if (originalName.contains('.')) originalName.substringBeforeLast('.') else originalName
            val defaultSaveName = "${baseName}_processed${currentFormat.extension}"
            saveImageLauncher.launch(defaultSaveName)
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val type = when (checkedId) {
                binding.chipFilterGrayscale.id -> ImageProcessor.FilterType.GRAYSCALE
                binding.chipFilterSepia.id -> ImageProcessor.FilterType.SEPIA
                binding.chipFilterVintage.id -> ImageProcessor.FilterType.VINTAGE
                binding.chipFilterWarm.id -> ImageProcessor.FilterType.WARM
                binding.chipFilterCool.id -> ImageProcessor.FilterType.COOL
                binding.chipFilterHighContrast.id -> ImageProcessor.FilterType.HIGH_CONTRAST
                binding.chipFilterInvert.id -> ImageProcessor.FilterType.INVERT
                binding.chipFilterVignette.id -> ImageProcessor.FilterType.VIGNETTE
                binding.chipFilterBw.id -> ImageProcessor.FilterType.BLACK_AND_WHITE
                else -> ImageProcessor.FilterType.ORIGINAL
            }
            filterParams = filterParams.copy(filterType = type)
            schedulePreviewUpdate()
        }
    }

    private fun setupSliders() {
        binding.sliderBrightness.addOnChangeListener { _, value, _ ->
            binding.tvBrightnessLabel.text = "Brightness: ${value.toInt()}"
            filterParams = filterParams.copy(brightness = value)
            schedulePreviewUpdate()
        }

        binding.sliderContrast.addOnChangeListener { _, value, _ ->
            val formatted = String.format("%.2f", value)
            binding.tvContrastLabel.text = "Contrast: ${formatted}x"
            filterParams = filterParams.copy(contrast = value)
            schedulePreviewUpdate()
        }

        binding.sliderQuality.addOnChangeListener { _, value, _ ->
            currentQuality = value.toInt()
            binding.tvQualityLabel.text = "Compression Quality: $currentQuality%"
        }
    }

    private fun loadSelectedImages() {
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnSaveProcessed.isEnabled = false

        lifecycleScope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                selectedUris.mapNotNull { uri ->
                    ImageProcessor.decodeBitmap(requireContext(), uri, maxDimension = 1600)
                }
            }

            loadedBitmaps.clear()
            loadedBitmaps.addAll(bitmaps)

            if (bitmaps.isNotEmpty()) {
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutPreviewActive.visibility = View.VISIBLE
                binding.btnSaveProcessed.isEnabled = true

                if (bitmaps.size > 1) {
                    isStitching = true
                    binding.cardStitchOptions.visibility = View.VISIBLE
                    rebuildStitchedBase()
                } else {
                    isStitching = false
                    binding.cardStitchOptions.visibility = View.GONE
                    baseWorkingBitmap = bitmaps[0]
                    schedulePreviewUpdate()
                }
            } else {
                Toast.makeText(requireContext(), "Could not decode selected image(s)", Toast.LENGTH_SHORT).show()
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutPreviewActive.visibility = View.GONE
            }

            binding.progressContainer.visibility = View.GONE
        }
    }

    private fun rebuildStitchedBase() {
        if (loadedBitmaps.isEmpty()) return
        lifecycleScope.launch {
            binding.progressContainer.visibility = View.VISIBLE
            baseWorkingBitmap = withContext(Dispatchers.IO) {
                ImageProcessor.stitchBitmaps(loadedBitmaps, stitchOrientation, spacingPx = 16)
            }
            binding.progressContainer.visibility = View.GONE
            schedulePreviewUpdate()
        }
    }

    private fun schedulePreviewUpdate() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            delay(50) // Small debounce for smooth slider drag
            val base = baseWorkingBitmap ?: return@launch

            val processed = withContext(Dispatchers.IO) {
                var bmp = ImageProcessor.applyFiltersAndTransforms(base, filterParams)
                if (watermarkText.isNotBlank()) {
                    val opts = ImageProcessor.WatermarkOptions(
                        text = watermarkText,
                        textSize = (bmp.width / 22f).coerceIn(24f, 72f)
                    )
                    bmp = ImageProcessor.applyWatermark(bmp, opts)
                }
                bmp
            }

            renderedPreviewBitmap = processed
            binding.ivPreview.setImageBitmap(processed)
            binding.tvImageInfo.text = "${processed.width} x ${processed.height} px"
        }
    }

    private fun processAndSaveImage(outputUri: Uri) {
        val base = baseWorkingBitmap ?: return
        binding.progressContainer.visibility = View.VISIBLE
        binding.btnSaveProcessed.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    // Apply full quality processing pipeline
                    var finalBitmap = ImageProcessor.applyFiltersAndTransforms(base, filterParams)
                    if (watermarkText.isNotBlank()) {
                        val opts = ImageProcessor.WatermarkOptions(
                            text = watermarkText,
                            textSize = (finalBitmap.width / 22f).coerceIn(24f, 72f)
                        )
                        finalBitmap = ImageProcessor.applyWatermark(finalBitmap, opts)
                    }

                    val tempFile = File.createTempFile("img_toolbox_", currentFormat.extension, requireContext().cacheDir)
                    ImageProcessor.saveBitmap(
                        bitmap = finalBitmap,
                        outputFile = tempFile,
                        format = currentFormat,
                        quality = currentQuality,
                        stripMetadata = true
                    )

                    requireContext().contentResolver.openOutputStream(outputUri).use { out ->
                        if (out == null) throw Exception("Cannot open destination stream")
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    tempFile.delete()
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            binding.progressContainer.visibility = View.GONE
            binding.btnSaveProcessed.isEnabled = true

            if (success) {
                val outName = requireContext().getFileName(outputUri)
                HistoryManager.recordAction(
                    context = requireContext(),
                    actionType = "IMAGE_TOOLBOX",
                    name = outName,
                    uriString = outputUri.toString(),
                    details = "Filter: ${filterParams.filterType.name}, Format: ${currentFormat.name}"
                )
                Toast.makeText(requireContext(), "Image Processed & Saved Successfully!", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderJob?.cancel()
        _binding = null
    }
}
