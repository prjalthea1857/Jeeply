package com.minerva.jeeply.openAPIs

data class Restaurant(
    val name: String,
    val address: String,
    val distance: Float
) : Comparable<Restaurant> {
    override fun compareTo(other: Restaurant): Int {
        return this.distance.compareTo(other.distance)
    }
}