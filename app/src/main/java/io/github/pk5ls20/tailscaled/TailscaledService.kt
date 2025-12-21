package io.github.pk5ls20.tailscaled

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import appctr.Appctr
import appctr.Closer
import appctr.StartOptions

class TailscaledService : Service() {
    private val notification by lazy { application.getSystemService<NotificationManager>()!! }
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TailscaledService: onCreate")
        mMessenger = Messenger(IncomingHandler(this))
        sharedPreferences = getSharedPreferences("appctr", MODE_PRIVATE)
        if (ProxyState.isUserLetRunning(this) && !ProxyState.isActualRunning()) {
            Log.d(TAG, "TailscaledService: ProxyState last is running, try to start tailscaled")
            // whether we need to start tailscaled
            if (sharedPreferences.getBoolean(
                    "force_bg",
                    false
                )
            ) {
                // keep state & restart service silently
                mMessenger.send(Message.obtain().apply {
                    what = MSG_START
                })
            } else {
                // reset state
                ProxyState.setUserState(this, false)
            }
        }
    }

    private fun startNotification() {
        // Notifications on Oreo and above need a channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notification.createNotificationChannel(
                NotificationChannel(
                    packageName,
                    "Tailscaled",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )

        startForeground(
            1,
            NotificationCompat.Builder(this, packageName)
                .setContentTitle(getString(R.string.notification_title))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "starting")
        ProxyState.setUserState(this, true)
        TileService.requestListeningState(
            applicationContext,
            ComponentName(applicationContext, ProxyTileService::class.java)
        )
        if (ProxyState.isActualRunning()) return START_STICKY
        start()
        startNotification()
        applicationContext.sendBroadcast(Intent("START"))
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return mMessenger.binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "try to stopMe")
        stopMe()
    }

    private fun stopMe() {
        Log.d(TAG, "try to stopMe")
        stopForeground(STOP_FOREGROUND_REMOVE)
        Appctr.stop()
        stopSelf()
        ProxyState.setUserState(this, false)
        TileService.requestListeningState(
            applicationContext,
            ComponentName(applicationContext, ProxyTileService::class.java)
        )
        applicationContext.sendBroadcast(Intent("STOP"))
    }

    private lateinit var mMessenger: Messenger

    private class IncomingHandler(
        var context: TailscaledService,
        private val applicationContext: Context = context.applicationContext
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Log.d(TAG, "receive message: ${msg.what}")
            when (msg.what) {
                MSG_SAY_HELLO -> {
                    applicationContext.sendBroadcast(Intent(if (ProxyState.isUserLetRunning(this.context)) "START" else "STOP"))
                }

                MSG_STOP -> {
                    context.stopMe()
                }

                MSG_START -> context.onStartCommand(null, 0, 0)
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun start() = Appctr.start(StartOptions().apply {
        socks5Server = sharedPreferences.getString("socks5", "0.0.0.0:1055")
        authKey = sharedPreferences.getString("authkey", "")
        execPath = "${applicationInfo.nativeLibraryDir}/libtailscaled.so"
        socketPath = "${applicationInfo.dataDir}/tailscaled.sock"
        statePath = "${applicationInfo.dataDir}/state"
        closeCallBack = Closer { stopMe() }
    })


    companion object {
        private val TAG = TailscaledService::class.java.simpleName
        const val MSG_SAY_HELLO = 0
        const val MSG_STOP = 1
        const val MSG_START = 2
    }
}
