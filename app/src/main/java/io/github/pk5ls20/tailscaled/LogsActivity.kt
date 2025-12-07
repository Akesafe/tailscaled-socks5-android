package io.github.pk5ls20.tailscaled

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import appctr.Appctr
import io.github.pk5ls20.tailscaled.adapter.LogsAdapter
import io.github.pk5ls20.tailscaled.databinding.ActivityLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private lateinit var logsAdapter: LogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupImmersiveToolbar()
        setupRecyclerView()
        setupSwipeRefresh()

        // Load logs when activity is created
        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        // Refresh logs every time the activity is resumed
        refreshLogs()
    }

    private fun setupRecyclerView() {
        logsAdapter = LogsAdapter()
        binding.recyclerView.apply {
            adapter = logsAdapter
            layoutManager = LinearLayoutManager(this@LogsActivity)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshLogs()
        }
    }

    private fun refreshLogs() {
        binding.swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                try {
                    val logString = Appctr.getLogs()
                    if (logString.isNullOrEmpty()) {
                        emptyList()
                    } else {
                        logString.split("\n").filter { it.isNotEmpty() }
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            logsAdapter.submitList(logs) {
                // Scroll to bottom after list is updated
                if (logs.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(logs.size - 1)
                }
            }

            // Update empty state visibility
            binding.emptyState.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupImmersiveToolbar() {
        // Set title
        binding.activityBar.activityBarTitleView.text = getString(R.string.logs_title)

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
            view.updatePadding(top = insets.top + toolbarHeight)
            windowInsets
        }
    }
}