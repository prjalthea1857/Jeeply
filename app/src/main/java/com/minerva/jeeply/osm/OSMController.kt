package com.minerva.jeeply.osm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import androidx.core.content.ContextCompat
import com.minerva.jeeply.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class OSMController {

    companion object {
        var location: Location? = null

        fun drawCircleMarker(context: Context): Bitmap? {
            val width = context.resources.getDimensionPixelSize(R.dimen.marker_size) // width of the bitmap
            val height = context.resources.getDimensionPixelSize(R.dimen.marker_size) // height of the bitmap

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val circleFillPaint = Paint().apply {
                style = Paint.Style.FILL
                color = ContextCompat.getColor(context, R.color.md_theme_dark_secondary)
            }

            val circleBorderPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                color = ContextCompat.getColor(context, R.color.md_theme_dark_outline)
            }

            canvas.drawCircle(width / 2f, height / 2f, (width / 2f) - 4f, circleFillPaint)
            canvas.drawCircle(width / 2f, height / 2f, (width / 2f) - 4f, circleBorderPaint)

            return bitmap
        }
    }
}