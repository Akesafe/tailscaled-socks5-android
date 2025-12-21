package io.github.pk5ls20.tailscaled

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import io.github.pk5ls20.tailscaled.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    private companion object {
        private const val PREFS_NAME = "appctr"
        private const val KEY_SOCKS5 = "socks5"
        private const val KEY_AUTHKEY = "authkey"
        private const val KEY_FORCE_BG = "force_bg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupImmersiveToolbar()
        loadSettings()
        setupTextWatchers()
    }

    private fun setupImmersiveToolbar() {
        // Set title
        binding.activityBar.activityBarTitleView.text = getString(R.string.settings_title)

        // Set back button click
        binding.activityBar.activityBarCloseView.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Handle window insets for immersive mode
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityBarLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarHeight = resources.getDimensionPixelSize(R.dimen.toolbar_height)
            val basePadding = resources.getDimensionPixelSize(R.dimen.settings_item_padding)
            view.updatePadding(
                top = insets.top + toolbarHeight + basePadding,
                bottom = insets.bottom + basePadding,
                left = basePadding,
                right = basePadding
            )
            windowInsets
        }
    }

    private fun loadSettings() {
        binding.socks5Input.setText(sharedPreferences.getString(KEY_SOCKS5, "0.0.0.0:1055"))
        binding.tokenInput.setText(sharedPreferences.getString(KEY_AUTHKEY, ""))
        binding.forceBgSwitch.isChecked = sharedPreferences.getBoolean(KEY_FORCE_BG, false)
    }

    private fun setupTextWatchers() {
        binding.socks5Input.doAfterTextChanged { text ->
            sharedPreferences.edit().apply {
                putString(KEY_SOCKS5, text.toString())
                apply()
            }
        }

        binding.tokenInput.doAfterTextChanged { text ->
            sharedPreferences.edit().apply {
                putString(KEY_AUTHKEY, text.toString())
                apply()
            }
        }

        binding.forceBgSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().apply {
                putBoolean(KEY_FORCE_BG, isChecked)
                apply()
            }
        }
    }
}