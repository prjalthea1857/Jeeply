package com.minerva.jeeply

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.minerva.jeeply.databinding.FragmentRoutesBinding
import com.minerva.jeeply.helper.Utility
import com.minerva.jeeply.osm.CurrentLocationOverlay
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
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

    lateinit var utility: Utility

    var latitude: Double = 0.0
    var longitude: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutesBinding.inflate(inflater, container, false)

        utility = Utility(requireContext())
        utility.locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Handle location change here
                utility.getCurrentLocation()

                latitude = utility.location.latitude
                longitude = utility.location.longitude
            }

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        val mapView = binding.mapView // use binding to access views
        // mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        // Add zoom controls
        mapView.isClickable = true // remove "binding" from these lines
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)

        val mapController: IMapController = mapView.controller

        // Set the maximum zoom level to prevent the user from seeing the tiled maps when zoomed out
        mapView.maxZoomLevel = 20.0
        mapView.minZoomLevel = 5.0

        // Set the boundaries of the map to Antarctica
        val south = -85.05
        val west = -180.0
        val north = 85.05
        val east = 180.0
        val boundingBox = BoundingBox(north, east, south, west)
        mapView.setScrollableAreaLimitDouble(boundingBox)

        // Set the initial map center and zoom level
        val startPoint = GeoPoint(latitude, longitude)
        mapController.setCenter(startPoint)
        mapController.setZoom(17.5)

        // TODO: make your cursor overlay static size,
        //  it means that the circle should be in fixed size state when zooming in or out.
        val myLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)

        mapView.overlays.add(CurrentLocationOverlay(requireContext(), mapView, utility.location))
        mapView.overlays.add(myLocationNewOverlay)

        myLocationNewOverlay.enableMyLocation()
        myLocationNewOverlay.enableFollowLocation()

        mapView.invalidate()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}