package io.github.pk5ls20.tailscaled

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import appctr.Appctr
import com.google.android.material.color.MaterialColors
import io.github.pk5ls20.tailscaled.databinding.DialogAboutBinding
import io.github.pk5ls20.tailscaled.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private var isRunning = false

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTailscaledService()
        } else {
            Log.w(TAG, "Notification permission denied, starting service anyway")
            startTailscaledService()
        }
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive: ${intent.action}")
            when (intent.action) {
                "START" -> {
                    isRunning = true
                    updateStatusCard(true)
                }
                "STOP" -> {
                    isRunning = false
                    updateStatusCard(false)
                }
            }
        }
    }

    private var mService: Messenger? = null
    private var bound: Boolean = false

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = Messenger(service)
            bound = true
            mService?.send(
                Message.obtain(null, TailscaledService.MSG_SAY_HELLO, 0, 0)
            )
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mService = null
            bound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBroadcastReceiver()
        bindToService()
        setupClickListeners()
        updateLandscapePadding()
        syncRunningState()
    }

    override fun onResume() {
        super.onResume()
        syncRunningState()
    }

    private fun syncRunningState() {
        isRunning = this.context?.let { c -> ProxyState.isUserLetRunning(c) } ?: Appctr.isRunning()
        updateStatusCard(isRunning)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLandscapePadding()
    }

    private fun updateLandscapePadding() {
        val displayMetrics = resources.displayMetrics
        val minWidth = resources.getDimensionPixelSize(R.dimen.surface_landscape_min_width)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        if (width > height && width > minWidth) {
            val expectedWidth = width.coerceAtMost(height.coerceAtLeast(minWidth))
            val padding = (width - expectedWidth).coerceAtLeast(0) / 2
            val basePadding = resources.getDimensionPixelSize(R.dimen.main_padding_horizontal)

            binding.contentContainer.setPadding(
                padding.coerceAtLeast(basePadding),
                binding.contentContainer.paddingTop,
                padding.coerceAtLeast(basePadding),
                binding.contentContainer.paddingBottom
            )
        } else {
            val basePadding = resources.getDimensionPixelSize(R.dimen.main_padding_horizontal)
            binding.contentContainer.setPadding(
                basePadding,
                binding.contentContainer.paddingTop,
                basePadding,
                binding.contentContainer.paddingBottom
            )
        }
    }

    private fun setupBroadcastReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction("START")
            addAction("STOP")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.registerReceiver(bReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            activity?.let {
                ContextCompat.registerReceiver(
                    it,
                    bReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        }
    }

    private fun bindToService() {
        Intent(activity, TailscaledService::class.java).also { intent ->
            activity?.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupClickListeners() {
        // Status card click - toggle service
        binding.statusCard.setOnClickListener {
            Log.d(TAG, "is running: $isRunning")
            if (isRunning) {
                mService?.send(Message.obtain(null, TailscaledService.MSG_STOP, 0, 0))
            } else {
                checkNotificationPermissionAndStart()
            }
        }

        // Logs click
        binding.logsLabel.setOnClickListener {
            startActivity(Intent(activity, LogsActivity::class.java))
        }

        // Settings click
        binding.settingsLabel.setOnClickListener {
            startActivity(Intent(activity, SettingsActivity::class.java))
        }

        // About click
        binding.aboutLabel.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun updateStatusCard(running: Boolean) {
        val ctx = context ?: return

        if (running) {
            binding.statusCard.text = getString(R.string.running)
            binding.statusCard.subtext = getString(R.string.tap_to_stop)
            binding.statusCard.icon =
                ContextCompat.getDrawable(ctx, R.drawable.ic_outline_check_circle)
            val colorRunning = MaterialColors.getColor(
                ctx,
                android.R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.color_running)
            )
            binding.statusCard.setCardBackgroundColor(colorRunning)
        } else {
            binding.statusCard.text = getString(R.string.stopped)
            binding.statusCard.subtext = getString(R.string.tap_to_start)
            binding.statusCard.icon =
                ContextCompat.getDrawable(ctx, R.drawable.ic_outline_not_interested)
            val colorStopped = MaterialColors.getColor(
                ctx,
                R.attr.colorStopped,
                ContextCompat.getColor(ctx, R.color.color_stopped)
            )
            binding.statusCard.setCardBackgroundColor(colorStopped)
        }
    }

    private fun showAboutDialog() {
        val ctx = context ?: return
        val dialogBinding = DialogAboutBinding.inflate(LayoutInflater.from(ctx))

        val versionName = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown Version"
        }
        dialogBinding.versionText.text = getString(R.string.version_format, versionName)

        AlertDialog.Builder(ctx)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val ctx = context ?: return
            when {
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startTailscaledService()
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.notification_permission_title)
                        .setMessage(R.string.notification_permission_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            startTailscaledService()
                        }
                        .show()
                }

                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startTailscaledService()
        }
    }

    private fun startTailscaledService() {
        activity?.startService(Intent(activity, TailscaledService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            activity?.unregisterReceiver(bReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        if (bound) {
            activity?.unbindService(mConnection)
            bound = false
        }
    }

    companion object {
        private const val TAG = "FirstFragment"
    }
}