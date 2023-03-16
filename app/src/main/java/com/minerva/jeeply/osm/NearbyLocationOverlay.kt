package com.minerva.jeeply.osm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import androidx.core.content.ContextCompat
import com.minerva.jeeply.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos

class NearbyLocationOverlay(val context: Context, val mapView: MapView, val location: Location) : Overlay() {
    private var marker: Marker? = null
    private val circlePaint = Paint()
    private val accuracy = location.accuracy.toDouble()

    init {
        circlePaint.color = Color.BLUE
        circlePaint.style = Paint.Style.STROKE
        circlePaint.strokeWidth = 2f
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) {
            return
        }

        if (marker == null) {
            marker = Marker(mapView)
            // marker?.position = GeoPoint(location.latitude, location.longitude)
            // marker?.icon = ContextCompat.getDrawable(context, R.drawable.ic_location)
            //mapView.overlays.add(marker)
        } else {
            marker?.position = GeoPoint(location.latitude, location.longitude)
        }

        val projection = mapView.projection
        val accuracyRadius = projection.metersToPixels(accuracy.toFloat()) * (1 / cos(Math.toRadians(location.latitude)))
        val point = projection.toPixels(marker?.position ?: GeoPoint(0.0, 0.0), null)

        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circlePaint)
    }
}