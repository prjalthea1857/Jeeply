package com.minerva.jeeply

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
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
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.minerva.jeeply.databinding.FragmentSocialBinding
import com.minerva.jeeply.helper.*
import com.minerva.jeeply.openAPIs.Forecast
import com.minerva.jeeply.osm.OSMController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var context: Context

    private var keepRunning = true
    private var lastNightMode: Int? = null

    lateinit var utilityManager: UtilityManager
    lateinit var jeeplyDatabaseHelper: JeeplyDatabaseHelper

    lateinit var gpsMyLocationProvider: GpsMyLocationProvider

    var dashboardCache: DashboardCache? = null

    var sampleImages = intArrayOf(
        R.drawable.sample1,
        R.drawable.sample2,
        R.drawable.sample3
    )

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

            sampleImages.forEach { drawable ->
                val imageView = ImageView(context) // create a ImageView
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageResource(drawable) // set resource image in ImageView

                binding.simpleViewFlipper.addView(imageView)
            }

            binding.simpleViewFlipper.flipInterval = 5000
            binding.simpleViewFlipper.isAutoStart = true
            binding.simpleViewFlipper.startFlipping()

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
                    findMyLocationAddress(location)
                }
            }

            /**
             * Require Location/GPS Access - END
             */
        }

        return binding.root
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
                findMyLocationAddress(location)
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
            // binding.postCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_outline))
        }

        fun dayMode() {
            val color = ContextCompat.getColor(context, R.color.md_theme_dark_onPrimary)

            setDrawableColor(color, R.drawable.ic_day_and_night, _binding?.weatherIconImageView)
            // binding.postCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_dark_onSurfaceVariant))
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
    private fun findMyLocationAddress(location: Location) {
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
            val address = getMostCommonLocation(addresses)
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