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
import kotlin.math.cos

class CurrentLocationOverlay(private val context: Context, mapView: MapView, private val location: Location) : Overlay() {
    private val marker = Marker(mapView)
    private val circleFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_theme_dark_primary)
        strokeWidth = 2f
    }
    private val circleBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.md_theme_dark_onBackground)
    }
    private val accuracy = location.accuracy.toDouble() / 16

    init {
        marker.position = GeoPoint(location.latitude, location.longitude)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) {
            return
        }

        marker.position = GeoPoint(location.latitude, location.longitude)

        val projection = mapView.projection
        val accuracyRadius = projection.metersToPixels(accuracy.toFloat()) * (1 / cos(Math.toRadians(location.latitude)))
        val point = projection.toPixels(marker.position, null)

        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circleFillPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circleBorderPaint)
    }
}