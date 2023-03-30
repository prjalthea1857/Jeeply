package com.minerva.jeeply.openAPIs

import org.json.JSONObject

data class Restaurant(
    val type: String,
    val id: Double,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String?>
) {
    val amenity: String? get() = tags["amenity"]
    val cuisine: String? get() = tags["cuisine"]
    val name: String? get() = tags["name"]
    val openingHours: String? get() = tags["opening_hours"]
    val description: String? get() = tags["description"]
}