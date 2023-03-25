package com.minerva.jeeply.openAPIs

data class Forecast(
    var latitude: Double,
    var longitude: Double,
    val generationtime_ms: Double,
    val utc_offset_seconds: Int,
    val timezone: String,
    val timezone_abbreviation: String,
    val elevation: Double,
    val hourly_units: HourlyUnits,
    val hourly: Hourly
)

data class HourlyUnits(
    val time: String,
    val temperature_2m: String,
    val weathercode: String
)

data class Hourly(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val weathercode: List<Int>
)