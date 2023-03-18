package com.minerva.jeeply.helper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class Utility(private val context: Context) {
    var locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // update the location and redraw the overlay
            _location = location
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private var _location: Location? = null

    private val ACCURACY_THRESHOLD = 100.0 // Or whatever value you want to use

    var location: Location
        get() = _location ?: throw IllegalStateException("Location not available")
        private set(value) {
            _location = value
        }

    companion object {
        const val PERMISSIONS_REQUEST = 1
    }

    init {
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            getCurrentLocation()
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

    private fun getCurrentLocation() {
        // Check if the app has location permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            // If not, request for location permissions
            requestPermissions(context as Activity, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), PERMISSIONS_REQUEST)
            return
        }

        // Get the last known location from the GPS provider
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (gpsLocation != null) {
            location = gpsLocation

            return
        }

        // Get the last known location from the network provider
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (networkLocation != null) {
            location = networkLocation

            return
        }

        // If no good enough location was found, request for location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
    }
}