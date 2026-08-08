package com.pdfutility.tools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.pdfutility.tools.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.android.material.color.DynamicColors.applyIfAvailable(this)
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
        super.onCreate(savedInstanceState)
        
        // Initialize PDFBox resource loader for Android
        PdfProcessor.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val pdfUri = getPdfUri(intent)
        if (pdfUri != null) {
            handleIncomingIntent(intent)
        } else if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun getPdfUri(intent: android.content.Intent?): android.net.Uri? {
        if (intent == null) return null
        if (intent.data != null) return intent.data
        @Suppress("DEPRECATION")
        val streamUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        if (streamUri != null) return streamUri
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val clipUri = clipData.getItemAt(0).uri
            if (clipUri != null) return clipUri
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val toggleItem = menu.findItem(R.id.action_toggle_theme)
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        if (isDarkMode) {
            toggleItem?.setIcon(R.drawable.ic_sun)
        } else {
            toggleItem?.setIcon(R.drawable.ic_moon)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_theme) {
            val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
            val isDarkMode = sharedPref.getBoolean("dark_mode", false)
            val newMode = !isDarkMode
            sharedPref.edit().putBoolean("dark_mode", newMode).apply()
            
            if (newMode) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            }
            recreate()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
