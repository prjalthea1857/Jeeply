package com.minerva.jeeply.osm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import androidx.core.content.ContextCompat
import com.minerva.jeeply.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

class TemporaryLocationOverlay(private val context: Context, var mapView: MapView, var location: Location) : Overlay() {
    private val marker = Marker(mapView)
    private val circleFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_theme_dark_secondary)
    }
    private val circleBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = ContextCompat.getColor(context, R.color.md_theme_dark_outline)
    }

    // Add a boolean flag to indicate whether to follow the location or not
    private var followLocation = false

    init {
        marker.position = GeoPoint(location.latitude, location.longitude)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) {
            return
        }

        marker.position = GeoPoint(location.latitude, location.longitude)

        val projection = mapView.projection

        // Convert 64dp to pixels based on the device's screen density
        val sizeInPx = context.resources.getDimensionPixelSize(R.dimen.marker_size)
        val accuracyRadius = sizeInPx / 2

        val point = projection.toPixels(marker.position, null)

        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circleFillPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circleBorderPaint)

        // If followLocation is enabled, center the map view on the current location
        if (followLocation) {
            mapView.controller.setCenter(marker.position)
        }
    }

    fun updateLocation(newLocation: Location) {
        location = newLocation
        marker.position = GeoPoint(location.latitude, location.longitude)

        // Center the map view on the new location
        mapView.controller.setCenter(marker.position)
    }

    // Add a function to enable or disable following the location
    fun enableFollowLocation(enable: Boolean) {
        followLocation = enable
    }
}