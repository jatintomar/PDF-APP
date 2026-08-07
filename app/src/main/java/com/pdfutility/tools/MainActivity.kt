package com.pdfutility.tools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.pdfutility.tools.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            com.google.android.material.color.DynamicColors.applyIfAvailable(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        try {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        super.onCreate(savedInstanceState)
        
        // Initialize PDFBox resource loader for Android
        try {
            PdfProcessor.init(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setSupportActionBar(binding.toolbar)

            val pdfUri = getPdfUri(intent)
            if (pdfUri != null) {
                handleIncomingIntent(intent)
            } else if (savedInstanceState == null) {
                loadFragment(DashboardFragment())
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun getPdfUri(intent: android.content.Intent?): android.net.Uri? {
        if (intent == null) return null
        try {
            if (intent.data != null) return intent.data
            @Suppress("DEPRECATION")
            val streamUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (streamUri != null) return streamUri
            val clipData = intent.clipData
            if (clipData != null && clipData.itemCount > 0) {
                val clipUri = clipData.getItemAt(0).uri
                if (clipUri != null) return clipUri
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return null
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        val uri = getPdfUri(intent)
        if (uri != null) {
            val readerFragment = ReaderFragment.newInstance(uri)
            loadFragment(readerFragment)
        }
    }

    fun loadFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }
}
