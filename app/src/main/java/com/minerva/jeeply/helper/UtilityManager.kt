package com.minerva.jeeply.helper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat

class UtilityManager(private val context: Context) {
    private var connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    companion object {
        const val PERMISSIONS_REQUEST = 1
        var temporal: Temporal? = null
    }

    init {
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            // Check if the app has location permissions
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                // If not, request for location permissions
                requestPermissions(context as Activity, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), PERMISSIONS_REQUEST)
            }
        }
    }

    fun hasWifi(): Boolean {
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun checkPermissions(): Boolean {
        val wifiPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE)
        val locationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

        return wifiPermission == PackageManager.PERMISSION_GRANTED && locationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissions(context as Activity,
            arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSIONS_REQUEST
        )
    }
}

data class Temporal(
    val dashboardCache: DashboardCache?
)

data class DashboardCache(
    var weather: Drawable?,
    var degree: String,
    var condition: String,
    var shortAddress: String
)