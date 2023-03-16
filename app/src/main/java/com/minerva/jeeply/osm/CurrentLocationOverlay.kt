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

class CurrentLocationOverlay(val context: Context, val mapView: MapView, val location: Location) : Overlay() {
    private var marker: Marker? = null
    private val circleFillPaint = Paint()
    private val circleBorderPaint = Paint()
    private val accuracy = location.accuracy.toDouble() / 16

    init {
        circleFillPaint.style = Paint.Style.FILL
        circleFillPaint.color = ContextCompat.getColor(context, R.color.md_theme_dark_primary)
        circleFillPaint.strokeWidth = 2f

        circleBorderPaint.style = Paint.Style.STROKE
        circleBorderPaint.strokeWidth = 4f
        circleBorderPaint.color = ContextCompat.getColor(context, R.color.md_theme_dark_onBackground)
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

        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circleFillPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), accuracyRadius.toFloat(), circleBorderPaint)
    }
}