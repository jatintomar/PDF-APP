package com.pdfutility.tools

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdfutility.tools.databinding.FragmentReaderBinding
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderFragment : Fragment() {

    private var _binding: FragmentReaderBinding? = null
    private val binding get() = _binding!!

    private var currentPdfUri: Uri? = null
    private var currentPdfPassword: String? = null
    private var isFromIntent = false
    private var totalPages = 0
    private var currentPage = 0
    
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfViewerAdapter? = null
    private var currentSearchQuery: String? = null
    
    private var pdDocument: PDDocument? = null
    private var decryptedFile: File? = null

    private val saveUnlockedLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            saveUnlockedPdf(uri)
        }
    }

    companion object {
        private const val ARG_PDF_URI = "pdf_uri"

        fun newInstance(uri: Uri): ReaderFragment {
            val fragment = ReaderFragment()
            val args = Bundle()
            args.putParcelable(ARG_PDF_URI, uri)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = arguments?.getParcelable<Uri>(ARG_PDF_URI)
        if (uri != null) {
            currentPdfUri = uri
            isFromIntent = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupMenu()
        
        binding.btnSearchPrev.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
                pdfAdapter?.setSearchQuery(currentSearchQuery, searchResults[currentSearchIndex])
                updateSearchUI()
                binding.pdfRecyclerView.scrollToPosition(searchResults[currentSearchIndex])
            }
        }
        binding.btnSearchNext.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
                pdfAdapter?.setSearchQuery(currentSearchQuery, searchResults[currentSearchIndex])
                updateSearchUI()
                binding.pdfRecyclerView.scrollToPosition(searchResults[currentSearchIndex])
            }
        }
        binding.btnSearchClose.setOnClickListener {
            clearSearch()
        }

        binding.pdfRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.pdfRecyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val rects = listOf(
                    android.graphics.Rect(
                        binding.pdfRecyclerView.width - 100,
                        0,
                        binding.pdfRecyclerView.width,
                        binding.pdfRecyclerView.height
                    )
                )
                binding.pdfRecyclerView.systemGestureExclusionRects = rects
            }
        }
        binding.pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val pos = lm.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION && pos != currentPage) {
                    currentPage = pos
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${pos + 1} of $totalPages"
                    saveLastReadPosition()
                }
            }
        })

        val uri = currentPdfUri
        if (uri != null) {
            loadPdf(uri)
        } else {
            showEmptyState()
        }
    }

    private fun setupMenu() {
        val menuHost: androidx.core.view.MenuHost = requireActivity()
        menuHost.addMenuProvider(object : androidx.core.view.MenuProvider {
            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(com.pdfutility.tools.R.menu.reader_menu, menu)
                val saveItem = menu.findItem(com.pdfutility.tools.R.id.action_save_unlocked)
                saveItem?.isVisible = currentPdfPassword != null

                val searchItem = menu.findItem(com.pdfutility.tools.R.id.action_search)
                val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
                
                searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        if (!query.isNullOrEmpty()) {
                            performSearch(query)
                        } else {
                            clearSearch()
                        }
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (newText.isNullOrEmpty()) {
                            clearSearch()
                        }
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                return when (menuItem.itemId) {
                    com.pdfutility.tools.R.id.action_share -> {
                        sharePdf()
                        true
                    }
                    com.pdfutility.tools.R.id.action_save_unlocked -> {
                        val originalName = currentPdfUri?.let { requireContext().getFileName(it) } ?: "document.pdf"
                        saveUnlockedLauncher.launch(originalName.addTagToFileName("unlocked"))
                        true
                    }
                    com.pdfutility.tools.R.id.action_add_bookmark -> {
                        showAddBookmarkDialog()
                        true
                    }
                    com.pdfutility.tools.R.id.action_view_bookmarks -> {
                        showBookmarksListDialog()
                        true
                    }
                    com.pdfutility.tools.R.id.action_jump_to_page -> {
                        showJumpToPageDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, androidx.lifecycle.Lifecycle.State.RESUMED)
    }

    private fun loadPdf(uri: Uri, password: String? = null) {
        currentPdfUri = uri
        binding.llEmptyState.visibility = View.GONE
        binding.llReaderContent.visibility = View.VISIBLE
        binding.pbRendering.visibility = View.VISIBLE

        val context = requireContext()
        val fileName = context.getFileName(uri)
        val isDocx = fileName.endsWith(".docx", ignoreCase = true)

        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = fileName
        
        pdDocument?.close()
        pdDocument = null

        if (isDocx) {
            handleDocx(uri)
            return
        }

        lifecycleScope.launch {
            val tempFile = File(context.cacheDir, "current_viewing.pdf")
            val copySuccess = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (copySuccess) {
                // 1. Check if encrypted and decrypt if necessary
                var isEncrypted = false
                withContext(Dispatchers.IO) {
                    try {
                        PDDocument.load(tempFile).use { doc ->
                            isEncrypted = doc.isEncrypted
                        }
                    } catch (t: Throwable) {
                        isEncrypted = true
                    }
                }

                if (isEncrypted && password == null) {
                    binding.pbRendering.visibility = View.GONE
                    showPasswordPrompt(uri)
                    return@launch
                }

                val decryptedFileLocal = File(context.cacheDir, "decrypted_view.pdf")
                val decryptionSuccess = withContext(Dispatchers.IO) {
                    try {
                        val doc = if (password != null) {
                            PDDocument.load(tempFile, password)
                        } else {
                            PDDocument.load(tempFile)
                        }
                        doc.use { d ->
                            d.setAllSecurityToBeRemoved(true)
                            decryptedFileLocal.outputStream().use { out ->
                                d.save(out)
                            }
                        }
                        true
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        false
                    }
                }

                if (!decryptionSuccess) {
                    binding.pbRendering.visibility = View.GONE
                    if (isEncrypted) {
                        Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        showPasswordPrompt(uri)
                    } else {
                        showEmptyState("Failed to decrypt or read PDF.")
                    }
                    return@launch
                }

                currentPdfPassword = password
                decryptedFile = decryptedFileLocal

                try {
                    parcelFileDescriptor?.close()
                    parcelFileDescriptor = ParcelFileDescriptor.open(decryptedFileLocal, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(parcelFileDescriptor!!)
                    pdfRenderer = renderer
                    
                    val metrics = resources.displayMetrics
                    pdfAdapter = PdfViewerAdapter(context, viewLifecycleOwner.lifecycleScope, renderer, metrics.widthPixels,
                        onPageClick = { pageIndex, x, y, viewWidth, viewHeight ->
                            handlePageClick(pageIndex, x, y, viewWidth, viewHeight)
                        },
                        onPageLongClick = { pageIndex ->
                            handlePageLongClick(pageIndex)
                        }
                    )
                    binding.pdfRecyclerView.adapter = pdfAdapter
                    
                    binding.pbRendering.visibility = View.GONE
                    totalPages = renderer.pageCount
                    
                    val savedPage = getSavedPage(uri)
                    val savedScale = getSavedScale(uri)
                    val savedTransX = getSavedTransX(uri)
                    val savedTransY = getSavedTransY(uri)
                    
                    if (savedPage in 0 until totalPages) {
                        currentPage = savedPage
                        binding.pdfRecyclerView.scrollToPosition(savedPage)
                    }
                    if (savedScale > 1f) {
                        binding.pdfRecyclerView.post {
                            binding.pdfRecyclerView.setZoom(savedScale, savedTransX, savedTransY)
                        }
                    }
                    
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${currentPage + 1} of $totalPages"

                    withContext(Dispatchers.IO) {
                        try {
                            pdDocument = PDDocument.load(decryptedFileLocal)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                    
                } catch (e: Exception) {
                    binding.pbRendering.visibility = View.GONE
                    showEmptyState("Error loading PDF: ${e.message}")
                }
            } else {
                binding.pbRendering.visibility = View.GONE
                showEmptyState("Failed to access the PDF file.")
            }
        }
    }

    private fun showPasswordPrompt(uri: Uri) {
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter PDF Password"
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Password Protected")
            .setMessage("This PDF is encrypted. Please enter the password to open it.")
            .setView(input)
            .setPositiveButton("Open") { _, _ ->
                val pwd = input.text.toString()
                currentPdfPassword = pwd
                loadPdf(uri, pwd)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
                showEmptyState("Could not open this PDF. Password is required.")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleDocx(uri: Uri) {
        val context = requireContext()
        lifecycleScope.launch {
            val tempFile = File(context.cacheDir, "temp_reader.pdf")
            val success = withContext(Dispatchers.IO) {
                try {
                    PdfProcessor.convertDocxToPdf(context, uri, tempFile)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (success) {
                try {
                    parcelFileDescriptor?.close()
                    parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(parcelFileDescriptor!!)
                    pdfRenderer = renderer

                    val metrics = resources.displayMetrics
                    pdfAdapter = PdfViewerAdapter(context, viewLifecycleOwner.lifecycleScope, renderer, metrics.widthPixels,
                        onPageClick = { pageIndex, x, y, viewWidth, viewHeight ->
                            handlePageClick(pageIndex, x, y, viewWidth, viewHeight)
                        },
                        onPageLongClick = { pageIndex ->
                            handlePageLongClick(pageIndex)
                        }
                    )
                    binding.pdfRecyclerView.adapter = pdfAdapter
                    binding.pbRendering.visibility = View.GONE
                    
                    totalPages = renderer.pageCount
                    
                    val savedPage = getSavedPage(uri)
                    val savedScale = getSavedScale(uri)
                    val savedTransX = getSavedTransX(uri)
                    val savedTransY = getSavedTransY(uri)
                    
                    if (savedPage in 0 until totalPages) {
                        currentPage = savedPage
                        binding.pdfRecyclerView.scrollToPosition(savedPage)
                    }
                    if (savedScale > 1f) {
                        binding.pdfRecyclerView.post {
                            binding.pdfRecyclerView.setZoom(savedScale, savedTransX, savedTransY)
                        }
                    }
                    
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${currentPage + 1} of $totalPages"

                    withContext(Dispatchers.IO) {
                        try {
                            pdDocument = PDDocument.load(tempFile)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    binding.pbRendering.visibility = View.GONE
                    showEmptyState("Error loading PDF: ${e.message}")
                }
            } else {
                binding.pbRendering.visibility = View.GONE
                showEmptyState("Failed to convert DOCX to PDF.")
            }
        }
    }

    private var searchResults = mutableListOf<Int>()
    private var currentSearchIndex = -1

    private fun performSearch(query: String) {
        if (query.isEmpty()) return
        binding.pbRendering.visibility = View.VISIBLE
        binding.llSearchNavigation.visibility = View.GONE
        
        lifecycleScope.launch {
            val matches = withContext(Dispatchers.IO) {
                val results = mutableListOf<Int>()
                val doc = pdDocument ?: try {
                    val context = requireContext()
                    val tempFile = File(context.cacheDir, "current_viewing.pdf")
                    val fileToLoad = if (currentPdfPassword != null) File(context.cacheDir, "decrypted_view.pdf") else tempFile
                    if (currentPdfPassword != null) {
                        PDDocument.load(fileToLoad, currentPdfPassword)
                    } else {
                        PDDocument.load(fileToLoad)
                    }
                } catch (t: Throwable) {
                    null
                }
                
                if (doc != null) {
                    try {
                        val stripper = PDFTextStripper()
                        for (i in 0 until totalPages) {
                            stripper.startPage = i + 1
                            stripper.endPage = i + 1
                            val pageText = stripper.getText(doc)
                            if (pageText != null && pageText.contains(query, ignoreCase = true)) {
                                results.add(i)
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
                results
            }

            binding.pbRendering.visibility = View.GONE
            currentSearchQuery = query
            
            if (matches.isNotEmpty()) {
                searchResults.clear()
                searchResults.addAll(matches)
                currentSearchIndex = 0
                
                pdfAdapter?.setSearchQuery(query, searchResults[currentSearchIndex])
                
                binding.llSearchNavigation.visibility = View.VISIBLE
                updateSearchUI()
                binding.pdfRecyclerView.scrollToPosition(searchResults[currentSearchIndex])
            } else {
                pdfAdapter?.setSearchQuery(null, -1)
                searchResults.clear()
                currentSearchIndex = -1
                Toast.makeText(requireContext(), "No matches found for '$query'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearSearch() {
        currentSearchQuery = null
        pdfAdapter?.setSearchQuery(null, -1)
        binding.llSearchNavigation.visibility = View.GONE
        searchResults.clear()
        currentSearchIndex = -1
    }

    private fun updateSearchUI() {
        if (searchResults.isEmpty()) {
            binding.llSearchNavigation.visibility = View.GONE
            return
        }
        binding.tvSearchResult.text = "${currentSearchIndex + 1} of ${searchResults.size}"
    }

    private fun showEmptyState(message: String? = null) {
        binding.llEmptyState.visibility = View.VISIBLE
        binding.llReaderContent.visibility = View.GONE
        if (message != null) {
            val tvTitle = binding.llEmptyState.getChildAt(1) as? android.widget.TextView
            tvTitle?.text = "Failed to Open PDF"
            val tvDesc = binding.llEmptyState.getChildAt(2) as? android.widget.TextView
            tvDesc?.text = message
        }
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "PDF Reader"
    }

    private fun sharePdf() {
        val uri = currentPdfUri ?: return
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share PDF using"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveUnlockedPdf(destUri: Uri) {
        val uri = currentPdfUri ?: return
        val pwd = currentPdfPassword ?: return
        lifecycleScope.launch {
            binding.pbRendering.visibility = View.VISIBLE
            val success = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) throw Exception("Cannot open stream")
                        PDDocument.load(inputStream, pwd).use { document ->
                            PdfProcessor.cleanWatermark(document)
                            document.setAllSecurityToBeRemoved(true)
                            requireContext().contentResolver.openOutputStream(destUri).use { outputStream ->
                                if (outputStream != null) {
                                    document.save(outputStream)
                                }
                            }
                        }
                    }
                    true
                } catch (t: Throwable) {
                    t.printStackTrace()
                    false
                }
            }
            binding.pbRendering.visibility = View.GONE
            if (success) {
                Toast.makeText(requireContext(), "Unlocked PDF saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save unlocked PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showJumpToPageDialog() {
        if (totalPages <= 0) return
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(totalPages.toString().length))
            hint = "1 to $totalPages"
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Jump to Page")
            .setMessage("Enter page number (1 to $totalPages):")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val pageStr = input.text.toString()
                val pageNum = pageStr.toIntOrNull()
                if (pageNum != null && pageNum in 1..totalPages) {
                    binding.pdfRecyclerView.scrollToPosition(pageNum - 1)
                } else {
                    Toast.makeText(requireContext(), "Invalid page number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handlePageClick(pageIndex: Int, touchX: Float, touchY: Float, viewWidth: Float, viewHeight: Float) {
        val doc = pdDocument ?: return
        if (pageIndex < 0 || pageIndex >= doc.numberOfPages) return
        
        try {
            val page = doc.getPage(pageIndex)
            val cropBox = page.cropBox ?: page.mediaBox ?: return
            val pageWidth = cropBox.width
            val pageHeight = cropBox.height
            val rotation = page.rotation
            
            val uX = if (viewWidth > 0f) touchX / viewWidth else 0f
            val uY = if (viewHeight > 0f) touchY / viewHeight else 0f
            
            // Convert touch coordinate space to PDF Box space (where origin is lower-left)
            val pdfX: Float
            val pdfY: Float
            
            when (rotation) {
                90 -> {
                    pdfX = uY * pageWidth + cropBox.lowerLeftX
                    pdfY = uX * pageHeight + cropBox.lowerLeftY
                }
                180 -> {
                    pdfX = (1f - uX) * pageWidth + cropBox.lowerLeftX
                    pdfY = uY * pageHeight + cropBox.lowerLeftY
                }
                270 -> {
                    pdfX = (1f - uY) * pageWidth + cropBox.lowerLeftX
                    pdfY = (1f - uX) * pageHeight + cropBox.lowerLeftY
                }
                else -> {
                    pdfX = uX * pageWidth + cropBox.lowerLeftX
                    pdfY = (1f - uY) * pageHeight + cropBox.lowerLeftY
                }
            }
            
            val annotations = page.annotations ?: return
            for (ann in annotations) {
                if (ann is PDAnnotationLink) {
                    val rect = ann.rectangle ?: continue
                    val minX = minOf(rect.lowerLeftX, rect.upperRightX)
                    val maxX = maxOf(rect.lowerLeftX, rect.upperRightX)
                    val minY = minOf(rect.lowerLeftY, rect.upperRightY)
                    val maxY = maxOf(rect.lowerLeftY, rect.upperRightY)
                    
                    if (pdfX >= minX && pdfX <= maxX && pdfY >= minY && pdfY <= maxY) {
                        var dest = ann.destination
                        val action = ann.action
                        if (dest == null && action is PDActionGoTo) {
                            dest = action.destination
                        }
                        
                        if (dest != null) {
                            val destPage = (dest as? PDPageDestination)?.page
                            if (destPage != null) {
                                val targetIndex = doc.pages.indexOf(destPage)
                                if (targetIndex != -1) {
                                    binding.pdfRecyclerView.scrollToPosition(targetIndex)
                                    Toast.makeText(requireContext(), "Jumping to Page ${targetIndex + 1}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else if (action is PDActionURI) {
                            val uriStr = action.uri
                            if (!uriStr.isNullOrEmpty()) {
                                try {
                                    var formattedUri = uriStr.trim()
                                    if (!formattedUri.startsWith("http://", ignoreCase = true) &&
                                        !formattedUri.startsWith("https://", ignoreCase = true) &&
                                        !formattedUri.startsWith("mailto:", ignoreCase = true) &&
                                        !formattedUri.startsWith("tel:", ignoreCase = true)) {
                                        formattedUri = "https://" + formattedUri
                                    }
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(formattedUri))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Cannot open link: $uriStr", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSavedPage(uri: Uri): Int {
        if (!isAdded) return 0
        val prefs = requireContext().getSharedPreferences("pdf_reader_positions", Context.MODE_PRIVATE)
        return prefs.getInt("page_${uri}", 0)
    }

    private fun getSavedScale(uri: Uri): Float {
        if (!isAdded) return 1f
        val prefs = requireContext().getSharedPreferences("pdf_reader_positions", Context.MODE_PRIVATE)
        return prefs.getFloat("scale_${uri}", 1f)
    }

    private fun getSavedTransX(uri: Uri): Float {
        if (!isAdded) return 0f
        val prefs = requireContext().getSharedPreferences("pdf_reader_positions", Context.MODE_PRIVATE)
        return prefs.getFloat("transX_${uri}", 0f)
    }

    private fun getSavedTransY(uri: Uri): Float {
        if (!isAdded) return 0f
        val prefs = requireContext().getSharedPreferences("pdf_reader_positions", Context.MODE_PRIVATE)
        return prefs.getFloat("transY_${uri}", 0f)
    }

    private fun saveLastReadPosition() {
        val uri = currentPdfUri ?: return
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences("pdf_reader_positions", Context.MODE_PRIVATE)
        val scale = binding.pdfRecyclerView.getZoomScale()
        val transX = binding.pdfRecyclerView.getTranslationXVal()
        val transY = binding.pdfRecyclerView.getTranslationYVal()
        
        prefs.edit().apply {
            putInt("page_${uri}", currentPage)
            putFloat("scale_${uri}", scale)
            putFloat("transX_${uri}", transX)
            putFloat("transY_${uri}", transY)
            apply()
        }
    }

    private fun showAddBookmarkDialog() {
        val uri = currentPdfUri ?: return
        val pageNum = currentPage + 1
        
        val input = android.widget.EditText(requireContext()).apply {
            hint = "e.g., Chapter 1, Important Formula, etc."
            setText("Page $pageNum")
            selectAll()
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Bookmark")
            .setMessage("Add bookmark for Page $pageNum with a label:")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val label = input.text.toString().trim().ifEmpty { "Page $pageNum" }
                addBookmark(uri, currentPage, label)
                Toast.makeText(requireContext(), "Bookmark added!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addBookmark(uri: Uri, pageIndex: Int, label: String) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("pdf_bookmarks", Context.MODE_PRIVATE)
        val key = "bookmarks_${uri}"
        val existingStr = prefs.getString(key, "[]")
        try {
            val arr = org.json.JSONArray(existingStr)
            val obj = org.json.JSONObject().apply {
                put("pageIndex", pageIndex)
                put("label", label)
                put("timestamp", System.currentTimeMillis())
            }
            arr.put(obj)
            prefs.edit().putString(key, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBookmarksListDialog() {
        val uri = currentPdfUri ?: return
        val context = requireContext()
        val prefs = context.getSharedPreferences("pdf_bookmarks", Context.MODE_PRIVATE)
        val key = "bookmarks_${uri}"
        val existingStr = prefs.getString(key, "[]")
        
        val itemsList = mutableListOf<Pair<Int, String>>()
        try {
            val arr = org.json.JSONArray(existingStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                itemsList.add(Pair(obj.getInt("pageIndex"), obj.getString("label")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (itemsList.isEmpty()) {
            Toast.makeText(context, "No bookmarks added yet for this PDF.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val displayItems = itemsList.map { "${it.second} (Page ${it.first + 1})" }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Bookmarks")
            .setItems(displayItems) { _, which ->
                val selected = itemsList[which]
                binding.pdfRecyclerView.scrollToPosition(selected.first)
                currentPage = selected.first
                (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${selected.first + 1} of $totalPages"
                saveLastReadPosition()
            }
            .setNeutralButton("Clear All") { _, _ ->
                prefs.edit().remove(key).apply()
                Toast.makeText(context, "All bookmarks cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handlePageLongClick(pageIndex: Int) {
        val context = requireContext()
        binding.pbRendering.visibility = View.VISIBLE
        lifecycleScope.launch {
            val extractedText = withContext(Dispatchers.IO) {
                try {
                    val doc = pdDocument ?: run {
                        val tempFile = File(context.cacheDir, "current_viewing.pdf")
                        val fileToLoad = if (currentPdfPassword != null) File(context.cacheDir, "decrypted_view.pdf") else tempFile
                        if (currentPdfPassword != null) {
                            PDDocument.load(fileToLoad, currentPdfPassword)
                        } else {
                            PDDocument.load(fileToLoad)
                        }
                    }
                    val stripper = PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(doc)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    null
                }
            }
            
            binding.pbRendering.visibility = View.GONE
            
            if (extractedText.isNullOrBlank()) {
                Toast.makeText(context, "No selectable text found on this page.", Toast.LENGTH_SHORT).show()
            } else {
                showTextSelectionDialog(pageIndex, extractedText)
            }
        }
    }

    private fun showTextSelectionDialog(pageIndex: Int, text: String) {
        val context = requireContext()
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        
        val editText = android.widget.EditText(context).apply {
            setText(text)
            setTextIsSelectable(true)
            keyListener = null
            background = null
            setTextColor(android.graphics.Color.DKGRAY)
            textSize = 15f
        }
        
        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(editText)
        }
        
        container.addView(scroll)
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Page ${pageIndex + 1} Text Selection")
            .setView(container)
            .setPositiveButton("Copy All") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Copied Text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        saveLastReadPosition()
    }

    override fun onDestroyView() {
        pdDocument?.close()
        pdDocument = null
        val rendererToClose = pdfRenderer
        if (rendererToClose != null) {
            synchronized(rendererToClose) {
                try {
                    rendererToClose.close()
                } catch (e: Exception) {}
            }
        }
        pdfRenderer = null
        try {
            parcelFileDescriptor?.close()
        } catch (e: Exception) {}
        parcelFileDescriptor = null
        super.onDestroyView()
        _binding = null
    }
}
