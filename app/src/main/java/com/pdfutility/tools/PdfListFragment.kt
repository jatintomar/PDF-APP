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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdfutility.tools.databinding.FragmentPdfListBinding
import com.pdfutility.tools.databinding.ItemDiscoveredPdfBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiscoveredPdf(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val path: String
)

class PdfListFragment : Fragment() {

    private var _binding: FragmentPdfListBinding? = null
    private val binding get() = _binding!!

    private val allPdfs = mutableListOf<DiscoveredPdf>()
    private val filteredPdfs = mutableListOf<DiscoveredPdf>()
    private lateinit var pdfAdapter: DiscoveredPdfAdapter

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
        checkPermissionsAndScan()
    }

    private fun setupRecyclerView() {
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
        binding.rvDiscoveredPdfs.adapter = pdfAdapter
    }

    private fun setupListeners() {
        binding.swipeRefreshPdfs.setOnRefreshListener {
            scanDevicePdfs()
        }

        binding.btnRefreshPdfs.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            scanDevicePdfs()
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
                filterPdfs(s?.toString() ?: "")
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
                            
                            val contentUri = Uri.withAppendedPath(collection, id.toString())
                            
                            list.add(
                                DiscoveredPdf(
                                    uri = contentUri,
                                    name = name,
                                    size = size,
                                    dateModified = dateModified * 1000L, // convert seconds to ms
                                    path = dataPath
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Ensure unique lists by path and name
                list.distinctBy { it.path + "/" + it.name }
            }

            allPdfs.clear()
            allPdfs.addAll(results)
            
            // Initial filter
            filterPdfs(binding.etSearchPdfs.text?.toString() ?: "")

            binding.pbScanningPdfs.visibility = View.GONE
            binding.swipeRefreshPdfs.isRefreshing = false

            if (filteredPdfs.isEmpty()) {
                binding.llEmptyDiscovered.visibility = View.VISIBLE
                binding.tvPdfFoundCount.text = "0 documents found on device"
            } else {
                binding.llEmptyDiscovered.visibility = View.GONE
                binding.tvPdfFoundCount.text = "${allPdfs.size} document(s) found on device"
            }
        }
    }

    private fun scanDirectoryRecursive(directory: File, results: MutableList<DiscoveredPdf>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursive(file, results)
            } else if (file.name.endsWith(".pdf", ignoreCase = true) || file.name.endsWith(".docx", ignoreCase = true)) {
                results.add(
                    DiscoveredPdf(
                        uri = Uri.fromFile(file),
                        name = file.name,
                        size = file.length(),
                        dateModified = file.lastModified(),
                        path = file.parentFile?.absolutePath ?: file.parent ?: ""
                    )
                )
            }
        }
    }

    private fun filterPdfs(query: String) {
        filteredPdfs.clear()
        if (query.isEmpty()) {
            filteredPdfs.addAll(allPdfs)
        } else {
            val lower = query.lowercase(Locale.getDefault())
            filteredPdfs.addAll(allPdfs.filter { it.name.lowercase(Locale.getDefault()).contains(lower) })
        }
        pdfAdapter.notifyDataSetChanged()
        
        if (filteredPdfs.isEmpty() && allPdfs.isNotEmpty()) {
            binding.llEmptyDiscovered.visibility = View.VISIBLE
        } else {
            binding.llEmptyDiscovered.visibility = View.GONE
        }
    }

    private fun sharePdf(pdf: DiscoveredPdf) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, pdf.uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share Discovered PDF"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
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
