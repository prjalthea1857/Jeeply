package com.minerva.jeeply

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.minerva.jeeply.databinding.FragmentRoutesBinding
import com.minerva.jeeply.helper.UtilityManager
import com.minerva.jeeply.osm.OSMController
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
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

    private lateinit var utilityManager: UtilityManager

    private lateinit var mapView: MapView
    private lateinit var myLocationNewOverlay: MyLocationNewOverlay
    lateinit var gpsMyLocationProvider: GpsMyLocationProvider
    private var initZoomMarker = false

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutesBinding.inflate(inflater, container, false)

        utilityManager = UtilityManager(requireContext())

        val conf = Configuration.getInstance()
        conf.load(context, PreferenceManager.getDefaultSharedPreferences(context))
        conf.cacheMapTileCount = 5

        mapView = binding.mapView  // use binding to access views
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
        val startPoint = GeoPoint(12.879721, 121.77401699999996)
        mapController.setCenter(startPoint)
        mapController.setZoom(7.0)

        // Set the boundaries of the map to Antarctica
        val south = -85.05
        val west = -180.0
        val north = 85.05
        val east = 180.0
        val boundingBox = BoundingBox(north, east, south, west)
        mapView.setScrollableAreaLimitDouble(boundingBox)

        myLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
        myLocationNewOverlay.setPersonIcon(drawCircleMarker())
        myLocationNewOverlay.setPersonHotspot(25.0f, 25.0f)

        myLocationNewOverlay.enableMyLocation()
        myLocationNewOverlay.enableFollowLocation()

        gpsMyLocationProvider = GpsMyLocationProvider(context)
        gpsMyLocationProvider.startLocationProvider { location, source ->
            if (location != null) {
                if (!initZoomMarker) {
                    mapController.animateTo(GeoPoint(location),17.5, 1550)
                    OSMController.location = location
                    initZoomMarker = true
                }
            }
        }

        if (OSMController.location != null) {
            mapController.setCenter(GeoPoint(OSMController.location))
            mapController.setZoom(17.5)
        }

        mapView.overlays.add(myLocationNewOverlay)
        mapView.invalidate()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gpsMyLocationProvider.stopLocationProvider()
        _binding = null
    }

    private fun drawCircleMarker(): Bitmap? {
        val context: Context = requireContext()

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