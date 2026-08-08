package com.pdfutility.tools

import android.content.Context
import android.graphics.RectF
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
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.viewer.MuPDFCore
import com.pdfutility.tools.databinding.FragmentReaderBinding
import com.tom_roush.pdfbox.pdmodel.PDDocument
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
    private var isDraggingScrollbar = false
    
    private var core: MuPDFCore? = null
    private var pdfAdapter: PdfViewerAdapter? = null
    private var currentSearchQuery: String? = null
    
    private var pdDocument: PDDocument? = null

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
        binding.pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val pos = lm.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION && pos != currentPage) {
                    currentPage = pos
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${pos + 1} of $totalPages"
                    
                    if (!isDraggingScrollbar) {
                        updateScrollbarThumbPosition(pos)
                    }
                    
                    // Save last opened page index
                    currentPdfUri?.let { uri ->
                        saveLastOpenedPage(uri, pos)
                    }
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
        val fileName = getFileName(context, uri)
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
                try {
                    core = MuPDFCore(tempFile.readBytes(), "pdf")
                    
                    if (core!!.needsPassword()) {
                        if (password != null && core!!.authenticatePassword(password)) {
                            // Password accepted
                        } else {
                            binding.pbRendering.visibility = View.GONE
                            showPasswordPrompt(uri)
                            return@launch
                        }
                    }
                    
                    val metrics = resources.displayMetrics
                    pdfAdapter = PdfViewerAdapter(context, core!!, metrics.widthPixels) { pageIndex, x, y, viewWidth, viewHeight ->
                        handlePageClick(pageIndex, x, y, viewWidth, viewHeight)
                    }
                    binding.pdfRecyclerView.adapter = pdfAdapter
                    
                    binding.pbRendering.visibility = View.GONE
                    totalPages = core!!.countPages()
                    
                    val savedPage = getLastOpenedPage(uri)
                    val startPage = if (savedPage in 0 until totalPages) savedPage else 0
                    currentPage = startPage
                    
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${startPage + 1} of $totalPages"
                    
                    setupScrubber(startPage, totalPages)
                    
                    binding.pdfRecyclerView.post {
                        if (startPage > 0 && startPage < totalPages) {
                            binding.pdfRecyclerView.scrollToPosition(startPage)
                            Toast.makeText(requireContext(), "Resumed at Page ${startPage + 1}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    withContext(Dispatchers.IO) {
                        try {
                            pdDocument = if (password != null) {
                                PDDocument.load(tempFile, password)
                            } else {
                                PDDocument.load(tempFile)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
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
                    core = MuPDFCore(tempFile.readBytes(), "pdf")
                    val metrics = resources.displayMetrics
                    pdfAdapter = PdfViewerAdapter(context, core!!, metrics.widthPixels) { pageIndex, x, y, viewWidth, viewHeight ->
                        handlePageClick(pageIndex, x, y, viewWidth, viewHeight)
                    }
                    binding.pdfRecyclerView.adapter = pdfAdapter
                    binding.pbRendering.visibility = View.GONE
                    
                    totalPages = core!!.countPages()
                    
                    val savedPage = getLastOpenedPage(uri)
                    val startPage = if (savedPage in 0 until totalPages) savedPage else 0
                    currentPage = startPage
                    
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${startPage + 1} of $totalPages"
                    
                    setupScrubber(startPage, totalPages)
                    
                    binding.pdfRecyclerView.post {
                        if (startPage > 0 && startPage < totalPages) {
                            binding.pdfRecyclerView.scrollToPosition(startPage)
                            Toast.makeText(requireContext(), "Resumed at Page ${startPage + 1}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    withContext(Dispatchers.IO) {
                        try {
                            pdDocument = PDDocument.load(tempFile)
                        } catch (e: Exception) {
                            e.printStackTrace()
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
        binding.rlVerticalScrollbar.visibility = View.GONE
        
        lifecycleScope.launch {
            val matches = withContext(Dispatchers.IO) {
                val results = mutableListOf<Int>()
                for (i in 0 until totalPages) {
                    val boxes = core?.searchPage(i, query)
                    if (boxes != null && boxes.isNotEmpty()) {
                        results.add(i)
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
                if (totalPages > 1) {
                    binding.rlVerticalScrollbar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun clearSearch() {
        currentSearchQuery = null
        pdfAdapter?.setSearchQuery(null, -1)
        binding.llSearchNavigation.visibility = View.GONE
        if (totalPages > 1) {
            binding.rlVerticalScrollbar.visibility = View.VISIBLE
        }
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
                } catch (e: Exception) {
                    e.printStackTrace()
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

    private fun saveLastOpenedPage(uri: Uri, pageIndex: Int) {
        val context = context ?: return
        val sharedPref = context.getSharedPreferences("pdf_page_history", Context.MODE_PRIVATE)
        sharedPref.edit().putInt(uri.toString(), pageIndex).apply()
    }

    private fun getLastOpenedPage(uri: Uri): Int {
        val context = context ?: return 0
        val sharedPref = context.getSharedPreferences("pdf_page_history", Context.MODE_PRIVATE)
        return sharedPref.getInt(uri.toString(), 0)
    }

    private fun setupScrubber(startPage: Int, total: Int) {
        if (total <= 1) {
            binding.rlVerticalScrollbar.visibility = View.GONE
            return
        }
        binding.rlVerticalScrollbar.visibility = View.VISIBLE
        binding.tvScrollbarPage.text = (startPage + 1).toString()
        
        binding.rlVerticalScrollbar.post {
            updateScrollbarThumbPosition(startPage)
        }
        
        binding.rlVerticalScrollbar.setOnTouchListener { view, event ->
            if (totalPages <= 1) return@setOnTouchListener false
            
            val containerHeight = view.height
            val thumbHeight = binding.scrollbarThumb.height
            val range = containerHeight - thumbHeight
            
            if (range <= 0) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isDraggingScrollbar = true
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    
                    val touchY = event.y
                    var targetY = touchY - (thumbHeight / 2f)
                    if (targetY < 0) targetY = 0f
                    if (targetY > range) targetY = range.toFloat()
                    
                    binding.scrollbarThumb.translationY = targetY
                    
                    val progress = targetY / range
                    val targetPage = Math.round(progress * (totalPages - 1)).toInt()
                    
                    if (targetPage in 0 until totalPages && targetPage != currentPage) {
                        currentPage = targetPage
                        binding.pdfRecyclerView.scrollToPosition(targetPage)
                        binding.tvScrollbarPage.text = (targetPage + 1).toString()
                        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle = "Page ${targetPage + 1} of $totalPages"
                        
                        currentPdfUri?.let { uri ->
                            saveLastOpenedPage(uri, targetPage)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingScrollbar = false
                    true
                }
                else -> false
            }
        }
    }

    private fun updateScrollbarThumbPosition(pageIndex: Int) {
        if (totalPages <= 1) return
        binding.tvScrollbarPage.text = (pageIndex + 1).toString()
        
        binding.rlVerticalScrollbar.post {
            val containerHeight = binding.rlVerticalScrollbar.height
            val thumbHeight = binding.scrollbarThumb.height
            val range = containerHeight - thumbHeight
            if (range > 0) {
                val progress = pageIndex.toFloat() / (totalPages - 1)
                val targetY = progress * range
                binding.scrollbarThumb.translationY = targetY
            }
        }
    }

    override fun onDestroyView() {
        pdDocument?.close()
        pdDocument = null
        super.onDestroyView()
        _binding = null
    }
}
