package io.github.aedev.flow.data.music.smart

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SmartDownloadEnvironment
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun availableBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

        fun chargingAllowed(chargingOnly: Boolean): Boolean =
            !chargingOnly || context.getSystemService(BatteryManager::class.java).isCharging

        fun batteryAllowed(): Boolean =
            context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let {
                it !in
                    0..14
            }

        fun networkAllowed(wifiOnly: Boolean): Boolean {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            return connectivity.activeNetwork != null && (!wifiOnly || !connectivity.isActiveNetworkMetered)
        }
    }
