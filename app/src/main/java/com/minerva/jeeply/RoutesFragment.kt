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
import androidx.fragment.app.Fragment
import com.minerva.jeeply.databinding.FragmentRoutesBinding
import com.minerva.jeeply.helper.UtilityManager
import com.minerva.jeeply.osm.MyCustomLocationNewOverlay
import com.minerva.jeeply.osm.OSMController
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import kotlin.math.cos
import kotlin.math.sin


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class RoutesFragment : Fragment() {

    private var _binding: FragmentRoutesBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var context: Context

    private lateinit var utilityManager: UtilityManager

    private lateinit var mapView: MapView
    private lateinit var myCustomLocationNewOverlay: MyCustomLocationNewOverlay
    lateinit var currentLocationProvider: GpsMyLocationProvider
    private var initZoomMarker = false

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutesBinding.inflate(inflater, container, false)

        context = requireContext()

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

        val circleCurrentMarker = OSMController.drawCircleMarker(requireContext())

        myCustomLocationNewOverlay = MyCustomLocationNewOverlay(GpsMyLocationProvider(context), mapView)
        myCustomLocationNewOverlay.overlayName = "mylocation"
        myCustomLocationNewOverlay.setPersonIcon(circleCurrentMarker)
        myCustomLocationNewOverlay.setPersonHotspot(22.25f, 22.25f)

        myCustomLocationNewOverlay.enableMyLocation()
        myCustomLocationNewOverlay.enableFollowLocation()

        currentLocationProvider = GpsMyLocationProvider(context)
        currentLocationProvider.startLocationProvider { location, source ->
            if (location != null) {
                OSMController.location = location
                if (!initZoomMarker) {
                    mapController.animateTo(GeoPoint(location),17.5, 1550)
                    removeTemporaryOverlay()
                    initZoomMarker = true
                }
            }
        }

        if (OSMController.location != null) {
            mapController.setCenter(GeoPoint(OSMController.location))
            mapController.setZoom(17.5)

            // TODO: After the app successfully retrieves the saved location,
            //  implemented a function to create a temporary marker similar to the previous one used,
            //  and dynamically placed it at the center of the map to optimize the user's map viewing experience.
            addTemporaryMarker(OSMController.location!!)
        }

        mapView.overlays.add(myCustomLocationNewOverlay)
        mapView.invalidate()

        return binding.root
    }

    private fun addTemporaryMarker(location: Location) {
        val temporaryOverlay = object : Overlay() {
            override fun draw(canvas: Canvas?, projection: Projection?) {
                super.draw(canvas, projection)
                if (canvas != null && projection != null) {
                    val point = projection.toPixels(GeoPoint(location), null)
                    val bitmap: Bitmap = OSMController.drawCircleMarker(requireContext())!! // replace with your own marker icon
                    canvas.drawBitmap(bitmap, point.x.toFloat() - 22.25f, point.y.toFloat() - 22.25f, null)
                }
            }
        }
        mapView.overlays.add(temporaryOverlay)
        mapView.invalidate()
    }

    private fun removeTemporaryOverlay() {
        val overlaysToDelete = mapView.overlays.filter { overlay ->
            !(overlay is MyCustomLocationNewOverlay && overlay.overlayName == "mylocation")
        }
        mapView.overlays.removeAll(overlaysToDelete)
        mapView.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentLocationProvider.stopLocationProvider()
        _binding = null
    }
}