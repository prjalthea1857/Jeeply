package com.minerva.jeeply

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.minerva.jeeply.databinding.FragmentSocialBinding
import com.minerva.jeeply.helper.*
import com.minerva.jeeply.openAPIs.Forecast
import com.minerva.jeeply.openAPIs.Restaurant
import com.minerva.jeeply.osm.OSMController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.roundToInt


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
@Suppress("NAME_SHADOWING")
class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var context: Context

    private var keepRunning = true
    private var lastNightMode: Int? = null

    private var searchRestaurantOnce = false

    lateinit var utilityManager: UtilityManager
    lateinit var jeeplyDatabaseHelper: JeeplyDatabaseHelper

    lateinit var gpsMyLocationProvider: GpsMyLocationProvider

    var dashboardCache: DashboardCache? = null

    var sampleImages = intArrayOf(
        R.drawable.sample1,
        R.drawable.sample2,
        R.drawable.sample3
    )

    val restaurantPreviewList = ArrayList<Bitmap>()

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)

        if (isAdded) {
            context = requireContext()

            // Initialize device permissions
            utilityManager = UtilityManager(context)

            // Initialize database
            jeeplyDatabaseHelper = JeeplyDatabaseHelper(context)

            // Toggles day/night mode icons and updates the UI.
            toggleDayNightIcons()

            // Display basic greetings based on time quarter.
            startGreetings()

            // Checks the database if it has existing forecast data to display into the UI.
            findPresetData()

            // Initialize write post.
            writePost()

            /**
             * Require Location/GPS Access - START
             */

            gpsMyLocationProvider = GpsMyLocationProvider(context)
            gpsMyLocationProvider.startLocationProvider { location, source ->
                if (location != null) {
                    OSMController.location = location

                    // Displays the current weather information.
                    displayCurrentWeather(location)

                    // Finds the user current address.
                    val address = findMyLocationAddress(location)

                    // Finds the nearby restaurant in current location.
                    findNearbyRestaurants(location, address)
                }
            }

            /**
             * Require Location/GPS Access - END
             */
        }

        return binding.root
    }

    /***
     * Finds nearby restaurants using the given current location and address
     */
    private fun findNearbyRestaurants(currentLocation: Location, address: String) {
        // Searches and retrieves an image of the given restaurant
        suspend fun searchRestaurantImage(restaurant: Restaurant): Bitmap? {
            var bitmap: Bitmap? = null

            return withContext(Dispatchers.IO) {
                val query = restaurant.tags["name"]?.trim()?.replace(" ", "+") + "+" + address.trim().replace(" ", "+")
                val url = "https://www.google.com/search?q=$query"

                val doc = Jsoup.connect(url).get()

                doc.allElements.select("script").firstOrNull { it.data().contains("data:image/jpeg;base64,") && it.data().contains("dimg") }
                    ?.data()?.let { scriptText ->
                        val regex = Regex("'([^']*)'")
                        val matchResult = regex.find(scriptText)

                        var previewImageBase64 = ""
                        if (matchResult != null) {
                            val searchArray = matchResult.groupValues
                            searchArray.forEach { search ->
                                if (search.contains("data:image/jpeg;base64,")) {
                                    previewImageBase64 = search.removePrefix("data:image/jpeg;base64,")
                                }
                            }

                            var decodedByte: ByteArray? = null
                            var base64String = previewImageBase64
                            while (decodedByte == null && base64String.isNotEmpty()) {
                                try {
                                    // Convert the Base64 string to a byte array
                                    decodedByte = Base64.getDecoder().decode(base64String)
                                } catch (ex: Exception) {
                                    base64String = base64String.dropLast(1)
                                }
                            }

                            bitmap = if (decodedByte != null) {
                                // Create a BitmapFactory.Options object to configure the decoding process
                                val options = BitmapFactory.Options().apply {
                                    // Set the inSampleSize to a value greater than 1 to reduce the size of the decoded image
                                    inSampleSize = 2
                                    // Set the inPreferredConfig to ARGB_8888 to improve the quality of the decoded image
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                }

                                BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size, options)
                            } else {
                                null
                            }
                        }
                    }

                bitmap
            }
        }

        // Finds a bounding box around the given location with the given radius
        fun findBoundingBox(location: Location, radius: Double): BoundingBox {
            val lat = location.latitude
            val lon = location.longitude

            val earthRadius = 6371.0 // Earth's radius in kilometers

            // radius, in kilometer
            val dLat = radius / earthRadius
            val dLon = radius / (earthRadius * cos(Math.PI * lat / 180))
            val northLat = lat - dLat * 180 / Math.PI
            val southLat = lat + dLat * 180 / Math.PI
            val eastLon = lon - dLon * 180 / Math.PI
            val westLon = lon + dLon * 180 / Math.PI

            return BoundingBox(northLat, eastLon, southLat, westLon)
        }

        // Retrieves a list of restaurants within the given bounding box
        suspend fun getRestaurants(boundingBox: BoundingBox): List<Restaurant> {
            return withContext(Dispatchers.IO) {
                val overpassQuery = "[out:json];node[amenity=restaurant](${boundingBox.toOverpassBBoxString()});out;"
                val overpassUrl = "http://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(overpassQuery, "UTF-8")

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(overpassUrl)
                    .build()
                val response = client.newCall(request).execute()
                val json = response.body?.string()

                val gson = Gson()
                val data = gson.fromJson(json, Map::class.java)
                val elements = data["elements"] as List<Map<String, Any>>
                val restaurants = elements.filter { it["type"] == "node" && it["tags"] is Map<*, *> }
                    .mapNotNull { element ->
                        val tags = element["tags"] as Map<String, String>
                        if (tags.containsKey("name")) {
                            Restaurant(
                                type = element["type"] as String,
                                id = element["id"] as Double,
                                lat = element["lat"] as Double,
                                lon = element["lon"] as Double,
                                tags = tags
                            )
                        } else {
                            null
                        }
                    }
                restaurants
            }
        }

        // Function to display the previews that have been loaded so far
        fun displayPreviews(previewList: List<Bitmap>) {
            binding.simpleViewFlipper.removeAllViews()
            previewList.forEach { preview ->
                val imageView = ImageView(context)
                val params = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                imageView.layoutParams = params
                binding.simpleViewFlipper.addView(imageView)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageBitmap(preview)
            }
            binding.simpleViewFlipper.animateFirstView = false
            binding.simpleViewFlipper.setInAnimation(context, R.anim.slide_in_bottom)
            binding.simpleViewFlipper.setOutAnimation(context, R.anim.slide_out_top)
            binding.simpleViewFlipper.flipInterval = 5000
            binding.simpleViewFlipper.isAutoStart = true
            binding.simpleViewFlipper.startFlipping()
        }

        if (!searchRestaurantOnce) {
            lifecycleScope.launch {
                // Create a coroutine scope for the image loading operations
                val coroutineScope = CoroutineScope(Dispatchers.Main)

                // Create a list to hold the restaurant previews
                val restaurantPreviewList = mutableListOf<Bitmap>()

                // Create a HashMap to hold the cached previews
                val previewCache = HashMap<String, Bitmap>()

                // Get the list of restaurants to fetch images for
                val restaurants = getRestaurants(findBoundingBox(currentLocation, 1.5))

                // Fetch the previews for each restaurant in sequence
                for (restaurant in restaurants) {
                    coroutineScope.launch {
                        // Check if the preview is already in the cache
                        var preview = previewCache[restaurant.tags["name"]]
                        if (preview == null) {
                            // If not, fetch the preview from the network
                            preview = withContext(Dispatchers.IO) {
                                searchRestaurantImage(restaurant)
                            }
                            // Cache the preview
                            preview?.let { previewCache[restaurant.tags["name"]!!] = it }
                        }
                        // Add the preview to the list and display the previews
                        if (preview != null) {
                            restaurantPreviewList.add(preview)
                            displayPreviews(restaurantPreviewList)
                        }
                    }
                }
            }

            searchRestaurantOnce = true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun writePost() {

    }

    private fun saveToCache(dashboardCache: DashboardCache) {
        UtilityManager.temporal = Temporal(dashboardCache)
    }

    private fun displayCache() {
        UtilityManager.temporal?.dashboardCache?.apply {
            binding.weatherIconImageView.setImageDrawable(weather)
            binding.weatherIconImageView.alpha = if (degree == "--°") 0.5f else 1.0f
            binding.tempDegreeTextView.text = degree
            binding.weatherStatusTextView.text = condition
            binding.locationTextView.text = shortAddress
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.simpleViewFlipper.stopFlipping()
        gpsMyLocationProvider.stopLocationProvider()

        keepRunning = false
        _binding = null
    }

    /**
     * This function finds the preset data, displays the current weather information,
     * and finds the user's current address. If cache exists, it displays it as well.
     */
    private fun findPresetData() {
        jeeplyDatabaseHelper.getCurrentForecast().let { forecast ->
            if (forecast != null) {
                val location = Location("")
                location.latitude = forecast.latitude
                location.longitude = forecast.longitude

                OSMController.location = location

                // Displays the current weather information.
                displayCurrentWeather(location)

                // Finds the user current address.
                val address = findMyLocationAddress(location)

                // Finds the nearby restaurant in current location.
                findNearbyRestaurants(location, address)
            }
        }

        if (UtilityManager.temporal?.dashboardCache != null) {
            displayCache()
        }
    }

    /**
     * This function toggles day/night mode icons by setting their color and updating the UI.
     * It runs on a separate thread to continuously check for changes in night mode and update the UI accordingly.
     */
    private fun toggleDayNightIcons() {
        fun setDrawableColor(color: Int, drawableResId: Int, imageView: ImageView?) {
            val drawable: Drawable? = ContextCompat.getDrawable(context, drawableResId)
            drawable?.setTint(color)
            imageView?.setImageDrawable(drawable)
        }

        fun nightMode() {
            val color = ContextCompat.getColor(context, R.color.md_theme_light_onPrimary)

            setDrawableColor(color, R.drawable.ic_day_and_night, _binding?.weatherIconImageView)
        }

        fun dayMode() {
            val color = ContextCompat.getColor(context, R.color.md_theme_dark_onPrimary)

            setDrawableColor(color, R.drawable.ic_day_and_night, _binding?.weatherIconImageView)
        }

        val currentNightMode = context.resources?.configuration?.uiMode?.and(
            Configuration.UI_MODE_NIGHT_MASK)

        // Update UI on the main thread only when night mode changes
        if (currentNightMode != lastNightMode) {
            lastNightMode = currentNightMode
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) nightMode() else dayMode()
        }

        val handler = Handler(Looper.getMainLooper())
        val thread = Thread {
            while (keepRunning) {
                // Update UI on the main thread only when night mode changes
                if (currentNightMode != lastNightMode) {
                    lastNightMode = currentNightMode
                    handler.post {
                        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) nightMode() else dayMode()
                    }
                }

                Thread.sleep(100)
            }
        }
        thread.start()
    }

    /**
     * Starts a separate thread that updates the text of a TextView with a greeting based on the current time of day.
     * The thread runs indefinitely until the value of the `keepRunning` variable is set to false.
     * It utilizes a `Handler` and the main looper to update the UI on the main thread.
     */
    private fun startGreetings() {
        val calendar = Calendar.getInstance()
        val greeting = when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Magandang Umaga!"
            in 12..15 -> "Magandang Tanghali!"
            in 16 .. 21 -> "Magandang Hapon!"
            else -> "Magandang Gabi!"
        }
        _binding?.greetingsTextView?.text = greeting

        val handler = Handler(Looper.getMainLooper())
        val thread = Thread {
            while (keepRunning) {
                val calendar = Calendar.getInstance()
                val greeting = when (calendar.get(Calendar.HOUR_OF_DAY)) {
                    in 0..11 -> "Magandang Umaga!"
                    in 12..15 -> "Magandang Tanghali!"
                    in 16 .. 21 -> "Magandang Hapon!"
                    else -> "Magandang Gabi!"
                }
                handler.post {
                    _binding?.greetingsTextView?.text = greeting
                }

                Thread.sleep(100)
            }
        }
        thread.start()
    }

    /**
     * Schedules periodic updates using a Handler and an updateRunnable object that calls fetchForecast() and displayForecastData()
     */
    private fun displayCurrentWeather(location: Location) {
        /**
         * takes a date-time string and a Forecast object, and returns a weather condition and a drawable image
         * based on the matching hour's weather code in the Forecast object.
         */
        suspend fun fetchForecast(latitude: Double, longitude: Double): Forecast {
            return withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&hourly=temperature_2m,weathercode&timezone=auto".format(latitude, longitude))
                    .build()
                val response = client.newCall(request).execute()
                val json = response.body?.string()

                // re-correct location parameter for better accuracy
                val forecast = Gson().fromJson(json, Forecast::class.java)
                forecast.latitude = latitude
                forecast.longitude = longitude
                forecast
            }
        }

        /**
         * updates the UI with the current weather information by looping through the hourly forecast data
         * and using getWeatherCondition() to update the temperature, weather condition, and drawable image.
         */
        fun displayForecastData(forecast: Forecast) {
            val h = forecast.hourly

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.getDefault())
            val currentDate = sdf.format(Date())

            for (i in 0 until h.time.size) {
                if (h.time[i] == currentDate) {
                    val degree = h.temperature_2m[i].roundToInt().toString() + "°"
                    _binding?.tempDegreeTextView?.text = degree

                    val weatherCondition = getWeatherCondition(h.time[i], forecast)
                    _binding?.weatherIconImageView?.setImageDrawable(weatherCondition.second)
                    _binding?.weatherIconImageView?.alpha = 1.0F
                    _binding?.weatherStatusTextView?.text = weatherCondition.first
                }
            }
        }

        fun updateForecast() {
            lifecycleScope.launch {
                val forecast = fetchForecast(location.latitude, location.longitude)
                jeeplyDatabaseHelper.saveCurrentForecast(forecast)
                displayForecastData(forecast)
            }
        }

        val handler = Handler(Looper.getMainLooper())

        val updateRunnable = object : Runnable {
            override fun run() {
                jeeplyDatabaseHelper.getCurrentForecast()?.let { displayForecastData(it) }

                if (utilityManager.hasWifi())
                    updateForecast()

                // Get the current time and calculate the delay until the next hour
                val calendar = Calendar.getInstance()
                val currentMinute = calendar.get(Calendar.MINUTE)
                val delay = (60 - currentMinute) * 60 * 1000L // Delay until the start of the next hour

                // Schedule the next update at the start of the next hour
                handler.postDelayed(this, delay)
            }
        }

        updateRunnable.run()
    }

    /**
     * Uses reverse geocoding to find the most common location address based on the device's current location.
     */
    private fun findMyLocationAddress(location: Location): String {
        var address = ""

        fun getMostCommonLocation(addresses: List<Address>): String {
            val localities = mutableMapOf<String, Int>()
            val subAdminAreas = mutableMapOf<String, Int>()

            // Iterate through the list of addresses and count the occurrences of each locality and sub-admin area
            for (address in addresses) {
                val locality = address.locality
                if (locality != null) {
                    localities[locality] = localities.getOrDefault(locality, 0) + 1
                }

                val subAdminArea = address.subAdminArea
                if (subAdminArea != null) {
                    subAdminAreas[subAdminArea] = subAdminAreas.getOrDefault(subAdminArea, 0) + 1
                }
            }

            // Find the most common locality and sub-admin area
            var mostCommonLocality: String? = null
            var localityCount = 0
            for ((locality, count) in localities) {
                if (count > localityCount) {
                    mostCommonLocality = locality
                    localityCount = count
                }
            }

            var mostCommonSubAdminArea: String? = null
            var subAdminAreaCount = 0
            for ((subAdminArea, count) in subAdminAreas) {
                if (count > subAdminAreaCount) {
                    mostCommonSubAdminArea = subAdminArea
                    subAdminAreaCount = count
                }
            }

            // Construct and return the text string
            return when {
                mostCommonLocality != null && mostCommonSubAdminArea != null ->
                    "$mostCommonLocality, $mostCommonSubAdminArea"
                mostCommonLocality != null -> mostCommonLocality
                mostCommonSubAdminArea != null -> mostCommonSubAdminArea
                else -> ""
            }
        }

        // Use reverse geocoding to get the address
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 10)

        if (addresses!!.isNotEmpty()) {
            address = getMostCommonLocation(addresses)
            binding.locationTextView.text = address

            jeeplyDatabaseHelper.getCurrentForecast()?.let { forecast ->
                val h = forecast.hourly

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.getDefault())
                val currentDate = sdf.format(Date())

                for (i in 0 until h.time.size) {
                    if (h.time[i] == currentDate) {
                        val degree = h.temperature_2m[i].roundToInt().toString() + "°"
                        val weatherCondition = getWeatherCondition(h.time[i], forecast)

                        dashboardCache = DashboardCache(weatherCondition.second, degree, weatherCondition.first, address)
                        saveToCache(dashboardCache!!)
                    }
                }
            }
        }

        return address
    }

    /**
     * takes a date-time string and a Forecast object, and returns a weather condition and a drawable
     * image based on the matching hour's weather code in the Forecast object.
     */
    private fun getWeatherCondition(dateTimeLocale: String, forecast: Forecast): Pair<String, Drawable?> {
        val h = forecast.hourly

        for (i in 0 until h.time.size) {
            if (h.time[i] == dateTimeLocale) {
                val weatherCode = h.weathercode[i]
                val isDaytime = dateTimeLocale.substringAfter('T').substring(0, 2).toInt() in 6..17

                return when (weatherCode) {
                    0 -> Pair("Clear sky",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sunny)
                        else ContextCompat.getDrawable(context, R.drawable.ic_nightly)
                    )
                    1 -> Pair("Mainly clear",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sun_small_cloud)
                        else ContextCompat.getDrawable(context, R.drawable.ic_moon_small_cloud)
                    )
                    2 -> Pair("Partly cloudy",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sun_big_cloud)
                        else ContextCompat.getDrawable(context, R.drawable.ic_moon_big_cloud)
                    )
                    3 -> Pair("Mostly cloudy",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_cloudy_sun)
                        else ContextCompat.getDrawable(context, R.drawable.ic_cloudy_moon)
                    )
                    51 -> Pair("Drizzling lightly",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sun_drizzle_low)
                        else ContextCompat.getDrawable(context, R.drawable.ic_moon_drizzle_low)
                    )
                    53 -> Pair("Drizzling moderately",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sun_drizzle_mid)
                        else ContextCompat.getDrawable(context, R.drawable.ic_moon_drizzle_mid)
                    )
                    55 -> Pair("Drizzling heavily",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sun_drizzle_max)
                        else ContextCompat.getDrawable(context, R.drawable.ic_moon_drizzle_max)
                    )
                    61 -> Pair("Raining slightly",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_rainy_low)
                        else ContextCompat.getDrawable(context, R.drawable.ic_rainy_low)
                    )
                    63 -> Pair("Raining moderately",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_rainy_mid)
                        else ContextCompat.getDrawable(context, R.drawable.ic_rainy_mid)
                    )
                    65 -> Pair("Raining heavily",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_rainy_max)
                        else ContextCompat.getDrawable(context, R.drawable.ic_rainy_max)
                    )
                    80 -> Pair("Showers",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sun_drizzle_max)
                        else ContextCompat.getDrawable(context, R.drawable.ic_moon_drizzle_max)
                    )
                    95, 96 -> Pair("Thunderstorms (lightly)",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sunny_rainy_thunder_mid)
                        else ContextCompat.getDrawable(context, R.drawable.ic_nightly_rainy_thunder_mid)
                    )
                    97 -> Pair("Thunderstorms (heavy)",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sunny_rainy_thunder_max)
                        else ContextCompat.getDrawable(context, R.drawable.ic_nightly_rainy_thunder_max))
                    else -> Pair("",
                        if (isDaytime) ContextCompat.getDrawable(context, R.drawable.ic_sunny)
                        else ContextCompat.getDrawable(context, R.drawable.ic_nightly))
                }
            }
        }

        return Pair("Unknown weather", null)
    }
}
