package io.github.pk5ls20.tailscaled

import android.content.Context
import appctr.Appctr
import androidx.core.content.edit

object ProxyState {
    private const val PREF = "proxy_state"
    private const val KEY_DESIRED = "desired_running"

    fun setUserState(context: Context, running: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_DESIRED, running)
            }
    }

    fun isUserLetRunning(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_DESIRED, false)
    }

    fun isActualRunning(): Boolean = Appctr.isRunning()
}