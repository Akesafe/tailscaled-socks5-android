package io.github.pk5ls20.tailscaled

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.content.ContextCompat

class ToggleActivity : Activity() {
    companion object {
        const val EXTRA_TOGGLE = "toggle"
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            var mService = Messenger(service)
            if (intent.getBooleanExtra(EXTRA_TOGGLE, false)) {
                Log.d(
                    "ToggleActivity",
                    "Toggling proxy state set=${ProxyState.isUserLetRunning(this@ToggleActivity)} real=${ProxyState.isActualRunning()}"
                )
                if (ProxyState.isActualRunning()) {
                    mService.send(Message.obtain(null, TailscaledService.MSG_STOP, 0, 0))
                } else {
                    ContextCompat.startForegroundService(
                        this@ToggleActivity,
                        Intent(this@ToggleActivity, TailscaledService::class.java)
                    )
                }
            }
            finish()
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // first bind mService to the service
        Intent(this, TailscaledService::class.java).also { intent ->
            this.bindService(intent, mConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mConnection)
    }
}