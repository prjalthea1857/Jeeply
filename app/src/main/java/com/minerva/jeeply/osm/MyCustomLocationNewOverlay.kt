package com.minerva.jeeply.osm

import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MyCustomLocationNewOverlay(locationProvider: IMyLocationProvider, mapView: MapView)
    : MyLocationNewOverlay(locationProvider, mapView) {
    var overlayName: String = "Custom Overlay"
}