package com.pdfutility.tools

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdfutility.tools.databinding.FragmentDashboardBinding
import com.pdfutility.tools.databinding.ItemRecentHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var isQuickMenuOpen = false
    private lateinit var recentAdapter: RecentHistoryAdapter
    private val recentList = mutableListOf<HistoryItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "PDF Utility Workspace"

        setupCoreClicks()
        setupQuickActionWidgets()
        setupRecentCarousel()
        refreshDashboardData()
        animateStaggeredGrid()
    }

    private fun setupCoreClicks() {
        binding.cardLock.setOnClickListener { navigateTo(LockFragment()) }
        binding.cardUnlock.setOnClickListener { navigateTo(UnlockFragment()) }
        binding.cardMerge.setOnClickListener { navigateTo(MergeFragment()) }
        binding.cardSplit.setOnClickListener { navigateTo(SplitFragment()) }
        binding.cardImgToPdf.setOnClickListener { navigateTo(ImgToPdfFragment()) }
        binding.cardPdfToImg.setOnClickListener { navigateTo(PdfToImgFragment()) }
        binding.cardPdfCompress.setOnClickListener { navigateTo(PdfCompressFragment()) }
        binding.cardImgCompress.setOnClickListener { navigateTo(ImgCompressFragment()) }

        binding.cardPdfDiscoveryBanner.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            navigateTo(PdfListFragment())
        }
    }

    private fun setupQuickActionWidgets() {
        binding.fabMasterQuickAction.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggleQuickMenu()
        }

        binding.fabQuickCompressImage.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            toggleQuickMenu()
            navigateTo(ImgCompressFragment())
        }

        binding.fabQuickCompressPdf.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            toggleQuickMenu()
            navigateTo(PdfCompressFragment())
        }
    }

    private fun toggleQuickMenu() {
        isQuickMenuOpen = !isQuickMenuOpen
        if (isQuickMenuOpen) {
            binding.llQuickActionsMenu.visibility = View.VISIBLE
            binding.fabMasterQuickAction.animate().rotation(45f).setDuration(250).start()
            binding.llQuickActionsMenu.animate().alpha(1.0f).translationY(0f).setDuration(250).start()
        } else {
            binding.fabMasterQuickAction.animate().rotation(0f).setDuration(250).start()
            binding.llQuickActionsMenu.animate().alpha(0.0f).translationY(50f).setDuration(250).withEndAction {
                binding.llQuickActionsMenu.visibility = View.GONE
            }.start()
        }
    }

    private fun setupRecentCarousel() {
        recentAdapter = RecentHistoryAdapter(
            recentList,
            onViewClicked = { item ->
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (item.uriString != null) {
                    val uri = Uri.parse(item.uriString)
                    navigateTo(ReaderFragment.newInstance(uri))
                } else {
                    Toast.makeText(requireContext(), "Opening ${item.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onShareClicked = { item ->
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                shareFile(item)
            },
            onDeleteClicked = { item ->
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                HistoryManager.deleteHistoryItem(requireContext(), item.id)
                refreshDashboardData()
                Toast.makeText(requireContext(), "Removed from activity history", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvRecentCarousel.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentCarousel.adapter = recentAdapter
    }

    private fun refreshDashboardData() {
        val context = requireContext()
        val history = HistoryManager.getHistory(context)

        // Bind Carousel Data
        recentList.clear()
        recentList.addAll(history)
        recentAdapter.notifyDataSetChanged()

        if (recentList.isEmpty()) {
            binding.llRecentCarouselContainer.visibility = View.GONE
        } else {
            binding.llRecentCarouselContainer.visibility = View.VISIBLE
        }
    }

    private fun animateStaggeredGrid() {
        // Sequentially animate core utilities
        val grid = binding.glTools
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            child.alpha = 0f
            child.translationY = 60f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay(i * 60L)
                .start()
        }

        binding.llRecentCarouselContainer.alpha = 0f
        binding.llRecentCarouselContainer.animate().alpha(1f).setDuration(500).setStartDelay(150).start()
    }

    private fun shareFile(item: HistoryItem) {
        if (item.uriString == null) {
            Toast.makeText(requireContext(), "No file location stored to share.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = if (item.toolType.contains("Image", ignoreCase = true)) "image/*" else "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share Processed File"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateTo(fragment: Fragment) {
        (activity as? MainActivity)?.loadFragment(fragment, addToBackStack = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class RecentHistoryAdapter(
    private val list: List<HistoryItem>,
    private val onViewClicked: (HistoryItem) -> Unit,
    private val onShareClicked: (HistoryItem) -> Unit,
    private val onDeleteClicked: (HistoryItem) -> Unit
) : RecyclerView.Adapter<RecentHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvRecentName.text = item.name
        holder.binding.tvRecentTool.text = item.toolType

        // Savings string
        val savedKb = item.spaceSaved / 1024
        val savingsStr = if (savedKb >= 1024) {
            String.format(Locale.US, "Saved: %.2f MB", savedKb / 1024f)
        } else {
            "Saved: $savedKb KB"
        }
        holder.binding.tvRecentSavings.text = savingsStr

        // Icons
        if (item.toolType.contains("Image", ignoreCase = true)) {
            holder.binding.ivRecentIcon.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            holder.binding.ivRecentIcon.setImageResource(android.R.drawable.ic_menu_save)
        }

        holder.binding.btnRecentView.setOnClickListener { onViewClicked(item) }
        holder.binding.btnRecentShare.setOnClickListener { onShareClicked(item) }
        holder.binding.btnRecentDelete.setOnClickListener { onDeleteClicked(item) }
    }

    override fun getItemCount(): Int = list.size
}
