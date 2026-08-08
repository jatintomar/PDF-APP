package com.pdfutility.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.Toast
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdfutility.tools.databinding.FragmentPdfListBinding
import com.pdfutility.tools.databinding.ItemDiscoveredPdfBinding
import com.pdfutility.tools.databinding.ItemFolderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortOption {
    NAME_ASC, NAME_DESC,
    DATE_NEWEST, DATE_OLDEST,
    SIZE_LARGEST, SIZE_SMALLEST
}

data class DiscoveredPdf(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val path: String
)

data class PdfFolder(
    val path: String,
    val name: String,
    val pdfs: List<DiscoveredPdf>
)

class PdfListFragment : Fragment() {

    private var _binding: FragmentPdfListBinding? = null
    private val binding get() = _binding!!

    private val allPdfs = mutableListOf<DiscoveredPdf>()
    private val filteredPdfs = mutableListOf<DiscoveredPdf>()
    private val filteredFolders = mutableListOf<PdfFolder>()
    
    private lateinit var pdfAdapter: DiscoveredPdfAdapter
    private lateinit var folderAdapter: FolderAdapter

    private var currentFolderPath: String? = null
    private var currentSortOption = SortOption.DATE_NEWEST

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            binding.llPermissionOverlay.visibility = View.GONE
            scanDevicePdfs()
        } else {
            binding.llPermissionOverlay.visibility = View.VISIBLE
            binding.pbScanningPdfs.visibility = View.GONE
            Toast.makeText(requireContext(), "Storage permission is required to find local PDFs.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Device Document Discovery"

        setupRecyclerView()
        setupListeners()

        // Handle device back button for nested folder navigation
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFolderPath != null) {
                    goBackToFolders()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        checkPermissionsAndScan()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(filteredFolders) { folder ->
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openFolder(folder.path)
        }

        pdfAdapter = DiscoveredPdfAdapter(filteredPdfs, 
            onItemClicked = { pdf ->
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val reader = ReaderFragment.newInstance(pdf.uri)
                (activity as? MainActivity)?.loadFragment(reader, addToBackStack = true)
            },
            onShareClicked = { pdf ->
                sharePdf(pdf)
            }
        )
        binding.rvDiscoveredPdfs.layoutManager = LinearLayoutManager(requireContext())
        updateAdapter()
    }

    private fun setupListeners() {
        binding.swipeRefreshPdfs.setOnRefreshListener {
            scanDevicePdfs()
        }

        binding.btnRefreshPdfs.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            scanDevicePdfs()
        }

        binding.btnBackToFolders.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            goBackToFolders()
        }

        binding.btnSortPdfs.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showSortMenu()
        }

        binding.btnGrantPermission.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        Toast.makeText(requireContext(), "Failed to open settings: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        binding.etSearchPdfs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                binding.llPermissionOverlay.visibility = View.GONE
                scanDevicePdfs()
            } else {
                binding.llPermissionOverlay.visibility = View.VISIBLE
            }
        } else {
            val permission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (permission == PackageManager.PERMISSION_GRANTED) {
                binding.llPermissionOverlay.visibility = View.GONE
                scanDevicePdfs()
            } else {
                binding.llPermissionOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun scanDevicePdfs() {
        binding.pbScanningPdfs.visibility = View.VISIBLE
        binding.llEmptyDiscovered.visibility = View.GONE
        binding.tvPdfFoundCount.text = "Scanning device..."

        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val list = mutableListOf<DiscoveredPdf>()
                val contentResolver = requireContext().contentResolver
                
                // Set up collection URI
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.DATA
                )

                // Filter by pdf/docx mime-types or file extensions
                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "%.pdf", "%.docx")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

                try {
                    contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "Unnamed PDF"
                            val size = cursor.getLong(sizeColumn)
                            val dateModified = cursor.getLong(dateColumn)
                            val dataPath = cursor.getString(dataColumn) ?: ""
                            
                            val parentPath = if (dataPath.isNotEmpty()) {
                                try {
                                    File(dataPath).parent ?: ""
                                } catch (e: Exception) {
                                    ""
                                }
                            } else {
                                ""
                            }
                            
                            val contentUri = Uri.withAppendedPath(collection, id.toString())
                            
                            list.add(
                                DiscoveredPdf(
                                    uri = contentUri,
                                    name = name,
                                    size = size,
                                    dateModified = dateModified * 1000L, // convert seconds to ms
                                    path = parentPath
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Fallback and supplemental scan of direct directories (especially for emulators or un-indexed external storage)
                try {
                    val rootDir = Environment.getExternalStorageDirectory()
                    if (rootDir != null && rootDir.exists()) {
                        scanDirectoryRecursive(rootDir, list)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Also always include app's custom external directories
                try {
                    val externalDirs = requireContext().getExternalFilesDirs(null)
                    for (dir in externalDirs) {
                        if (dir != null) {
                            scanDirectoryRecursive(dir, list)
                        }
                    }
                    scanDirectoryRecursive(requireContext().cacheDir, list)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Ensure unique lists by path and name
                list.distinctBy { it.path + "/" + it.name }
            }

            allPdfs.clear()
            allPdfs.addAll(results)
            
            binding.pbScanningPdfs.visibility = View.GONE
            binding.swipeRefreshPdfs.isRefreshing = false

            // Update UI adapters with new data
            updateAdapter()
        }
    }

    private fun scanDirectoryRecursive(directory: File, results: MutableList<DiscoveredPdf>) {
        val name = directory.name
        // Skip hidden directories and system folders we shouldn't scan
        if (name.startsWith(".") || 
            name.equals("data", ignoreCase = true) && directory.parentFile?.name.equals("Android", ignoreCase = true) ||
            name.equals("obb", ignoreCase = true) && directory.parentFile?.name.equals("Android", ignoreCase = true) ||
            name.equals("node_modules", ignoreCase = true) ||
            name.equals("cache", ignoreCase = true) ||
            name.equals("temp", ignoreCase = true) ||
            name.equals("tmp", ignoreCase = true)
        ) {
            return
        }

        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursive(file, results)
            } else {
                val fileName = file.name
                if (fileName.startsWith(".")) continue
                if (fileName.endsWith(".pdf", ignoreCase = true) || fileName.endsWith(".docx", ignoreCase = true)) {
                    results.add(
                        DiscoveredPdf(
                            uri = Uri.fromFile(file),
                            name = fileName,
                            size = file.length(),
                            dateModified = file.lastModified(),
                            path = file.parentFile?.absolutePath ?: file.parent ?: ""
                        )
                    )
                }
            }
        }
    }

    private fun getFriendlyFolderName(path: String): String {
        if (path.isEmpty()) return "Unknown Folder"
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (path == rootPath) {
            return "Internal Storage"
        }
        
        // Normalize path separators
        val normalizedPath = path.replace("\\", "/")
        
        when {
            normalizedPath.contains("/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Sent") -> return "WhatsApp Documents (Sent)"
            normalizedPath.contains("/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents") -> return "WhatsApp Documents"
            normalizedPath.contains("/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents/Sent") -> return "WhatsApp Business Documents (Sent)"
            normalizedPath.contains("/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents") -> return "WhatsApp Business Documents"
            normalizedPath.contains("/Android/media/com.gbwhatsapp/GBWhatsApp/Media/GBWhatsApp Documents") -> return "GBWhatsApp Documents"
            normalizedPath.contains("/Android/media/org.telegram.messenger/Telegram/Telegram Documents") -> return "Telegram Documents"
            normalizedPath.contains("/Android/media/org.thoughtcrime.securesms") -> return "Signal Media"
            normalizedPath.contains("/Android/media/") -> {
                // Try to extract app name from package
                val mediaIndex = normalizedPath.indexOf("/Android/media/")
                val rest = normalizedPath.substring(mediaIndex + 15)
                val parts = rest.split("/")
                if (parts.isNotEmpty()) {
                    val packageName = parts[0]
                    val appName = when {
                        packageName.contains("whatsapp") -> "WhatsApp"
                        packageName.contains("telegram") -> "Telegram"
                        packageName.contains("signal") -> "Signal"
                        packageName.contains("facebook") -> "Facebook"
                        packageName.contains("instagram") -> "Instagram"
                        packageName.contains("viber") -> "Viber"
                        packageName.contains("skype") -> "Skype"
                        packageName.contains("discord") -> "Discord"
                        packageName.contains("slack") -> "Slack"
                        packageName.contains("adobe") -> "Adobe Reader"
                        packageName.contains("camscanner") -> "CamScanner"
                        packageName.contains("wps") -> "WPS Office"
                        else -> packageName.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                    val subFolder = File(path).name
                    if (subFolder.isNotEmpty() && subFolder != packageName) {
                        return "$appName - $subFolder"
                    }
                    return "$appName Documents"
                }
            }
        }
        
        // Handle standard folders
        val file = File(path)
        val name = file.name.ifEmpty { "Folder" }
        return when (name.lowercase(Locale.getDefault())) {
            "download" -> "Downloads"
            "documents" -> "Documents"
            "document" -> "Documents"
            "dcim" -> "Camera (DCIM)"
            "pictures" -> "Pictures"
            "movies" -> "Movies"
            "music" -> "Music"
            else -> name
        }
    }

    private fun updateAdapter() {
        if (currentFolderPath == null) {
            binding.rvDiscoveredPdfs.adapter = folderAdapter
            binding.btnBackToFolders.visibility = View.GONE
            binding.tvCurrentFolderTitle.text = "📁 Folders"
        } else {
            binding.rvDiscoveredPdfs.adapter = pdfAdapter
            binding.btnBackToFolders.visibility = View.VISIBLE
            val folderName = getFriendlyFolderName(currentFolderPath!!)
            binding.tvCurrentFolderTitle.text = "📁 $folderName"
        }
        filterAndDisplay()
    }

    private fun filterAndDisplay() {
        val query = binding.etSearchPdfs.text?.toString() ?: ""
        val lowerQuery = query.lowercase(Locale.getDefault())

        if (currentFolderPath == null) {
            // Folders view
            filteredFolders.clear()
            val grouped = allPdfs.groupBy { it.path }
            val folders = grouped.map { (path, pdfs) ->
                val folderName = getFriendlyFolderName(path)
                PdfFolder(path = path, name = folderName, pdfs = pdfs)
            }

            // Filter folders by folder name, or if folder contains any matching pdfs
            val matchedFolders = if (query.isEmpty()) {
                folders
            } else {
                folders.filter { folder ->
                    folder.name.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    folder.pdfs.any { it.name.lowercase(Locale.getDefault()).contains(lowerQuery) }
                }
            }

            val sorted = sortFolders(matchedFolders, currentSortOption)
            filteredFolders.addAll(sorted)
            folderAdapter.notifyDataSetChanged()

            binding.tvPdfFoundCount.text = "${allPdfs.size} document(s) in ${folders.size} folder(s)"

            if (filteredFolders.isEmpty()) {
                binding.llEmptyDiscovered.visibility = View.VISIBLE
            } else {
                binding.llEmptyDiscovered.visibility = View.GONE
            }
        } else {
            // Folder contents view
            filteredPdfs.clear()
            val folderPdfs = allPdfs.filter { it.path == currentFolderPath }

            val matchedPdfs = if (query.isEmpty()) {
                folderPdfs
            } else {
                folderPdfs.filter { it.name.lowercase(Locale.getDefault()).contains(lowerQuery) }
            }

            val sorted = sortPdfs(matchedPdfs, currentSortOption)
            filteredPdfs.addAll(sorted)
            pdfAdapter.notifyDataSetChanged()

            binding.tvPdfFoundCount.text = "${filteredPdfs.size} of ${folderPdfs.size} document(s)"

            if (filteredPdfs.isEmpty()) {
                binding.llEmptyDiscovered.visibility = View.VISIBLE
            } else {
                binding.llEmptyDiscovered.visibility = View.GONE
            }
        }
    }

    private fun openFolder(path: String) {
        currentFolderPath = path
        updateAdapter()
    }

    private fun goBackToFolders() {
        currentFolderPath = null
        updateAdapter()
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.btnSortPdfs)
        popup.menu.add(0, 1, 0, "Name (A to Z)").apply { isChecked = currentSortOption == SortOption.NAME_ASC }
        popup.menu.add(0, 2, 0, "Name (Z to A)").apply { isChecked = currentSortOption == SortOption.NAME_DESC }
        popup.menu.add(0, 3, 0, "Date (Newest first)").apply { isChecked = currentSortOption == SortOption.DATE_NEWEST }
        popup.menu.add(0, 4, 0, "Date (Oldest first)").apply { isChecked = currentSortOption == SortOption.DATE_OLDEST }
        popup.menu.add(0, 5, 0, "Size (Largest first)").apply { isChecked = currentSortOption == SortOption.SIZE_LARGEST }
        popup.menu.add(0, 6, 0, "Size (Smallest first)").apply { isChecked = currentSortOption == SortOption.SIZE_SMALLEST }

        popup.menu.setGroupCheckable(0, true, true)

        popup.setOnMenuItemClickListener { item ->
            val newOption = when (item.itemId) {
                1 -> SortOption.NAME_ASC
                2 -> SortOption.NAME_DESC
                3 -> SortOption.DATE_NEWEST
                4 -> SortOption.DATE_OLDEST
                5 -> SortOption.SIZE_LARGEST
                6 -> SortOption.SIZE_SMALLEST
                else -> SortOption.DATE_NEWEST
            }
            currentSortOption = newOption
            filterAndDisplay()
            true
        }
        popup.show()
    }

    private fun sortPdfs(list: List<DiscoveredPdf>, option: SortOption): List<DiscoveredPdf> {
        return when (option) {
            SortOption.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOption.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOption.DATE_NEWEST -> list.sortedByDescending { it.dateModified }
            SortOption.DATE_OLDEST -> list.sortedBy { it.dateModified }
            SortOption.SIZE_LARGEST -> list.sortedByDescending { it.size }
            SortOption.SIZE_SMALLEST -> list.sortedBy { it.size }
        }
    }

    private fun sortFolders(list: List<PdfFolder>, option: SortOption): List<PdfFolder> {
        return when (option) {
            SortOption.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOption.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOption.DATE_NEWEST -> list.sortedByDescending { folder -> folder.pdfs.maxOfOrNull { it.dateModified } ?: 0L }
            SortOption.DATE_OLDEST -> list.sortedBy { folder -> folder.pdfs.minOfOrNull { it.dateModified } ?: 0L }
            SortOption.SIZE_LARGEST -> list.sortedByDescending { folder -> folder.pdfs.sumOf { it.size } }
            SortOption.SIZE_SMALLEST -> list.sortedBy { folder -> folder.pdfs.sumOf { it.size } }
        }
    }

    private fun sharePdf(pdf: DiscoveredPdf) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdf.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Discovered PDF"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class FolderAdapter(
    private val list: List<PdfFolder>,
    private val onItemClicked: (PdfFolder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvFolderName.text = item.name
        
        val size = item.pdfs.size
        holder.binding.tvFolderFileCount.text = "$size document${if (size == 1) "" else "s"}"
        holder.binding.tvFolderPath.text = item.path

        holder.binding.root.setOnClickListener {
            onItemClicked(item)
        }
    }

    override fun getItemCount(): Int = list.size
}

private class DiscoveredPdfAdapter(
    private val list: List<DiscoveredPdf>,
    private val onItemClicked: (DiscoveredPdf) -> Unit,
    private val onShareClicked: (DiscoveredPdf) -> Unit
) : RecyclerView.Adapter<DiscoveredPdfAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDiscoveredPdfBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiscoveredPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvDiscoveredName.text = item.name
        
        val isDocx = item.name.endsWith(".docx", ignoreCase = true)
        if (isDocx) {
            holder.binding.flDiscoveredIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFE0F2FE"))
            holder.binding.ivDiscoveredIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF0284C7"))
            holder.binding.ivDiscoveredIcon.setImageResource(android.R.drawable.ic_menu_edit)
        } else {
            holder.binding.flDiscoveredIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFEE2E2"))
            holder.binding.ivDiscoveredIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFE11D48"))
            holder.binding.ivDiscoveredIcon.setImageResource(android.R.drawable.ic_menu_agenda)
        }
        
        // Size string conversion
        val kb = item.size / 1024
        val sizeStr = if (kb >= 1024) {
            String.format(Locale.US, "%.2f MB", kb / 1024f)
        } else {
            "$kb KB"
        }
        holder.binding.tvDiscoveredSize.text = sizeStr

        // Date formatting
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        holder.binding.tvDiscoveredDate.text = sdf.format(Date(item.dateModified))

        // Path
        holder.binding.tvDiscoveredPath.text = item.path

        holder.binding.root.setOnClickListener {
            onItemClicked(item)
        }

        holder.binding.btnDiscoveredShare.setOnClickListener {
            onShareClicked(item)
        }
    }

    override fun getItemCount(): Int = list.size
}
