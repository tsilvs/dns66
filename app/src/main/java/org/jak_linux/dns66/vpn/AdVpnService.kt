/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.jak_linux.dns66.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.NotificationChannels
import org.jak_linux.dns66.R

class AdVpnService : VpnService(), Handler.Callback {
    companion object {
        private const val TAG = "VpnService"

        const val NOTIFICATION_ID_STATE = 10
        const val REQUEST_CODE_START = 43

        const val REQUEST_CODE_PAUSE = 42

        const val VPN_STATUS_STARTING = 0
        const val VPN_STATUS_RUNNING = 1
        const val VPN_STATUS_STOPPING = 2
        const val VPN_STATUS_WAITING_FOR_NETWORK = 3
        const val VPN_STATUS_RECONNECTING = 4
        const val VPN_STATUS_RECONNECTING_NETWORK_ERROR = 5

        const val VPN_STATUS_STOPPED = 6
        const val VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS"

        const val VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS"
        const val VPN_MSG_STATUS_UPDATE = 0

        const val VPN_MSG_NETWORK_CHANGED = 1

        @StringRes
        fun vpnStatusToTextId(status: Int): Int =
            when (status) {
                VPN_STATUS_STARTING -> R.string.notification_starting
                VPN_STATUS_RUNNING -> R.string.notification_running
                VPN_STATUS_STOPPING -> R.string.notification_stopping
                VPN_STATUS_WAITING_FOR_NETWORK -> R.string.notification_waiting_for_net
                VPN_STATUS_RECONNECTING -> R.string.notification_reconnecting
                VPN_STATUS_RECONNECTING_NETWORK_ERROR -> R.string.notification_reconnecting_error
                VPN_STATUS_STOPPED -> R.string.notification_stopped
                else -> throw IllegalArgumentException("Invalid vpnStatus value - $status")
            }

        fun checkStartVpnOnBoot(context: Context) {
            Log.i("BOOT", "Checking whether to start ad buster on boot")
            val config = FileHelper.loadCurrentSettings(context)
            if (config == null || !config.autoStart) {
                return
            }
            if (!context.getSharedPreferences("state", Context.MODE_PRIVATE)
                    .getBoolean("isActive", false)
            ) {
                return
            }

            if (VpnService.prepare(context) != null) {
                Log.i("BOOT", "VPN preparation not confirmed by user, changing enabled to false")
            }

            Log.i("BOOT", "Starting ad buster from boot")
            NotificationChannels.onCreate(context)

            val intent = getStartIntent(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun getStartIntent(context: Context): Intent =
            Intent(context, AdVpnService::class.java).apply {
                putExtra("COMMAND", Command.START.ordinal)

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent =
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                putExtra("NOTIFICATION_INTENT", pendingIntent)
            }

        private fun getResumeIntent(context: Context): Intent =
            Intent(context, AdVpnService::class.java).apply {
                putExtra("COMMAND", Command.RESUME.ordinal)

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent =
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                putExtra("NOTIFICATION_INTENT", pendingIntent)
            }

        var vpnStatus = VPN_STATUS_STOPPED
    }

    private val handler = Handler(Looper.myLooper()!!, this)

    private var vpnThread: AdVpnThread? = AdVpnThread(this, object : Notify {
        override fun run(value: Int) {
            handler.sendMessage(handler.obtainMessage(VPN_MSG_STATUS_UPDATE, value, 0))
        }
    })

    private val connectivityChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.sendMessage(handler.obtainMessage(VPN_MSG_NETWORK_CHANGED, intent))
        }
    }

    private val notificationBuilder =
        NotificationCompat.Builder(this, NotificationChannels.SERVICE_RUNNING)
            .setSmallIcon(R.drawable.ic_state_deny) // TODO: Notification icon
            .setPriority(Notification.PRIORITY_MIN)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.onCreate(this)

        val intent = Intent(this, AdVpnService::class.java)
            .putExtra("COMMAND", Command.PAUSE.ordinal)
        val pendingIntent =
            PendingIntent.getService(this, REQUEST_CODE_PAUSE, intent, PendingIntent.FLAG_IMMUTABLE)
        notificationBuilder
            .addAction(
                R.drawable.ic_pause_black_24dp,
                getString(R.string.notification_action_pause),
                pendingIntent
            )
            .setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand$intent")
        val command = if (intent == null) {
            Command.START
        } else {
            Command.entries[intent.getIntExtra("COMMAND", Command.START.ordinal)]
        }

        val start = {
            getSharedPreferences("state", MODE_PRIVATE).edit()
                .putBoolean("isActive", true)
                .apply()

            val notificationIntent = if (intent == null) {
                null
            } else {
                intent.getParcelableExtra<Parcelable>("NOTIFICATION_INTENT") as PendingIntent?
            }
            startVpn(notificationIntent)
        }

        when (command) {
            Command.RESUME -> {
                with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
                    cancelAll()
                }
                start()
            }

            Command.START -> start()
            Command.STOP -> {
                getSharedPreferences("state", Context.MODE_PRIVATE).edit()
                    .putBoolean("isActive", false)
                    .apply()
            }

            Command.PAUSE -> pauseVpn()
        }

        return Service.START_STICKY
    }

    private fun pauseVpn() {
        stopVpn()
        with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
            val pendingIntent = PendingIntent.getService(
                this@AdVpnService,
                REQUEST_CODE_START,
                getResumeIntent(this@AdVpnService),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification =
                NotificationCompat.Builder(this@AdVpnService, NotificationChannels.SERVICE_PAUSED)
                    .setSmallIcon(R.drawable.ic_state_deny) // TODO: Notification icon
                    .setPriority(Notification.PRIORITY_LOW)
                    .setColor(ContextCompat.getColor(this@AdVpnService, R.color.colorPrimaryDark))
                    .setContentTitle(getString(R.string.notification_paused_title))
                    .setContentText(getString(R.string.notification_paused_text))
                    .setContentIntent(pendingIntent)
                    .build()
            notify(NOTIFICATION_ID_STATE, notification)
        }
    }

    private fun updateVpnStatus(status: Int) {
        vpnStatus = status
        val notificationTextId = vpnStatusToTextId(status)
        notificationBuilder.setContentTitle(getString(notificationTextId))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ||
            FileHelper.loadCurrentSettings(applicationContext).showNotification
        ) {
            startForeground(NOTIFICATION_ID_STATE, notificationBuilder.build())
        }

        val intent = Intent(VPN_UPDATE_STATUS_INTENT)
            .putExtra(VPN_UPDATE_STATUS_EXTRA, status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun startVpn(notificationIntent: PendingIntent?) {
        notificationBuilder.setContentTitle(getString(R.string.notification_title))
        if (notificationIntent != null) {
            notificationBuilder.setContentIntent(notificationIntent)
        }
        updateVpnStatus(VPN_STATUS_STARTING)

        registerReceiver(
            connectivityChangedReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )

        restartVpnThread()
    }

    private fun restartVpnThread() {
        if (vpnThread == null) {
            Log.i(TAG, "restartVpnThread: Not restarting thread, could not find thread.")
            return
        }

        vpnThread?.stopThread()
        vpnThread?.startThread()
    }

    private fun stopVpnThread() = vpnThread?.stopThread()

    private fun waitForNetVpn() {
        stopVpnThread()
        updateVpnStatus(VPN_STATUS_WAITING_FOR_NETWORK)
    }

    private fun reconnect() {
        updateVpnStatus(VPN_STATUS_RECONNECTING)
        restartVpnThread()
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping Service")
        if (vpnThread != null) {
            stopVpnThread()
        }
        vpnThread = null

        try {
            unregisterReceiver(connectivityChangedReceiver)
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Ignoring exception on unregistering receiver")
        }
        updateVpnStatus(VPN_STATUS_STOPPED)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroyed, shutting down")
        stopVpn()
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            VPN_MSG_STATUS_UPDATE -> updateVpnStatus(msg.arg1)
            VPN_MSG_NETWORK_CHANGED -> connectivityChanged(msg.obj as Intent)
            else -> throw IllegalArgumentException("Invalid message with what = ${msg.what}")
        }
        return true
    }

    private fun connectivityChanged(intent: Intent) {
        if (intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, 0) ==
            ConnectivityManager.TYPE_VPN
        ) {
            Log.i(TAG, "Ignoring connectivity changed for our own network")
            return
        }

        if (ConnectivityManager.CONNECTIVITY_ACTION != intent.action) {
            Log.e(TAG, "Got bad intent on connectivity changed ${intent.action}")
        }
        if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
            Log.i(TAG, "Connectivity changed to no connectivity, wait for a network")
            waitForNetVpn()
        } else {
            Log.i(TAG, "Network changed, try to reconnect")
            reconnect()
        }
    }
}
