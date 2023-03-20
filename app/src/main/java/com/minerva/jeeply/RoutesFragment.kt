package com.minerva.jeeply

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.minerva.jeeply.databinding.FragmentRoutesBinding
import com.minerva.jeeply.helper.PermissionManager
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class RoutesFragment : Fragment() {

    private var _binding: FragmentRoutesBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    lateinit var permissionManager: PermissionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutesBinding.inflate(inflater, container, false)

        permissionManager = PermissionManager(requireContext())

        val conf = Configuration.getInstance()
        conf.load(context, PreferenceManager.getDefaultSharedPreferences(context))
        conf.cacheMapTileCount = 5

        val mapView = binding.mapView // use binding to access views
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        // Add zoom controls
        mapView.isClickable = true // remove "binding" from these lines
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)

        val mapController: IMapController = mapView.controller

        // Set the maximum zoom level to prevent the user from seeing the tiled maps when zoomed out
        mapView.maxZoomLevel = 20.0
        mapView.minZoomLevel = 5.0

        // Set the initial map center and zoom level
        val manilaStartPoint = GeoPoint(14.52, 120.43)
        mapController.setCenter(manilaStartPoint)
        mapController.setZoom(17.5)

        // Set the boundaries of the map to Antarctica
        val south = -85.05
        val west = -180.0
        val north = 85.05
        val east = 180.0
        val boundingBox = BoundingBox(north, east, south, west)
        mapView.setScrollableAreaLimitDouble(boundingBox)

        /**
         * Require Location/GPS Access - START
         */

        GpsMyLocationProvider(context).startLocationProvider { location, source ->
            if (location != null) {
                // TODO: make your cursor overlay static size,
                //  it means that the circle should be in fixed size state when zooming in or out.
                val myLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                myLocationNewOverlay.setPersonIcon(drawCircleMarker(location, mapView))
                myLocationNewOverlay.setPersonHotspot(25.0f, 25.0f)

                myLocationNewOverlay.enableMyLocation()
                myLocationNewOverlay.enableFollowLocation()

                mapView.overlays.add(myLocationNewOverlay)
                mapView.invalidate()
            }
        }

        /**
         * Require Location/GPS Access - END
         */
//        object : android.location.LocationListener {
//            override fun onLocationChanged(location: Location) {
//                Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
//
//                val mapView = binding.mapView // use binding to access views
//                // mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
//                mapView.setTileSource(TileSourceFactory.MAPNIK)
//
//                // Add zoom controls
//                mapView.isClickable = true // remove "binding" from these lines
//                mapView.setBuiltInZoomControls(false)
//                mapView.setMultiTouchControls(true)
//
//                val mapController: IMapController = mapView.controller
//
//                // Set the maximum zoom level to prevent the user from seeing the tiled maps when zoomed out
//                mapView.maxZoomLevel = 20.0
//                mapView.minZoomLevel = 5.0
//
//                // Set the boundaries of the map to Antarctica
//                val south = -85.05
//                val west = -180.0
//                val north = 85.05
//                val east = 180.0
//                val boundingBox = BoundingBox(north, east, south, west)
//                mapView.setScrollableAreaLimitDouble(boundingBox)
//
//                // Set the initial map center and zoom level
//                val startPoint = GeoPoint(location.latitude, location.longitude)
//                mapController.setCenter(startPoint)
//                mapController.setZoom(17.5)
//
//                // TODO: make your cursor overlay static size,
//                //  it means that the circle should be in fixed size state when zooming in or out.
//                val myLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
//                myLocationNewOverlay.setPersonIcon(drawCircleMarker(location, mapView))
//                myLocationNewOverlay.setPersonHotspot(25.0f, 25.0f)
//
//                myLocationNewOverlay.enableMyLocation()
//                myLocationNewOverlay.enableFollowLocation()
//
//                mapView.overlays.add(myLocationNewOverlay)
//                mapView.invalidate()
//            }
//
//            override fun onProviderEnabled(provider: String) {}
//
//            override fun onProviderDisabled(provider: String) {}
//
//            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
//        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GpsMyLocationProvider(requireContext()).stopLocationProvider()
        _binding = null
    }

    fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        val bitmap = drawable?.let {
            Bitmap.createBitmap(
                it.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }

        val canvas = bitmap?.let { Canvas(it) }
        if (canvas != null) {
            drawable.setBounds(0, 0, canvas.width, canvas.height)
        }
        canvas?.let { drawable.draw(it) }

        return bitmap
    }

    fun drawCircleMarker(location: Location, mapView: MapView): Bitmap? {
        val context: Context = requireContext()

        val marker = Marker(mapView)

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