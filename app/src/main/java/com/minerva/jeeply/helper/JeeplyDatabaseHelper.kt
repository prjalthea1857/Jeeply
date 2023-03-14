package com.minerva.jeeply.helper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.minerva.jeeply.openAPIs.Forecast
import com.minerva.jeeply.openAPIs.Hourly
import com.minerva.jeeply.openAPIs.HourlyUnits

class JeeplyDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "Jeeply.db"
        private const val DATABASE_VERSION = 1

        // Define table and column names
        const val FORECAST_TABLE_NAME = "forecast"
        const val HOURLY_UNITS_COLUMNS_TABLE_NAME = "hourly_units"
        const val HOURLY_TABLE_NAME = "hourly"
        const val ID_COLUMN_NAME = "_id"

        // Define column names for Forecast table
        const val LATITUDE_COLUMN_NAME = "latitude"
        const val LONGITUDE_COLUMN_NAME = "longitude"
        const val GENERATION_TIME_COLUMN_NAME = "generationtime_ms"
        const val UTC_OFFSET_COLUMN_NAME = "utc_offset_seconds"
        const val TIMEZONE_COLUMN_NAME = "timezone"
        const val TIMEZONE_ABBREVIATION_COLUMN_NAME = "timezone_abbreviation"
        const val ELEVATION_COLUMN_NAME = "elevation"
        const val HOURLY_UNIT_ID_COLUMN_NAME = "hourly_unit_id"
        const val HOURLY_ID_COLUMN_NAME = "hourly_id"

        // Define column names for HourlyUnits table
        const val ID_HOURLY_UNITS_COLUMN_NAME = "hourly_units_id"
        const val TIME_UNIT_COLUMN_NAME = "time"
        const val TEMPERATURE_2M_UNIT_COLUMN_NAME = "temperature_2m"
        const val WEATHERCODE_UNIT_COLUMN_NAME = "weathercode"

        // Define column names for Hourly table
        const val ID_HOURLY_COLUMN_NAME = "hourly_id"
        const val TIME_COLUMN_NAME = "time"
        const val TEMPERATURE_2M_COLUMN_NAME = "temperature_2m"
        const val WEATHERCODE_COLUMN_NAME = "weathercode"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create tables here
        db.execSQL("""
            CREATE TABLE $FORECAST_TABLE_NAME (
                $ID_COLUMN_NAME INTEGER PRIMARY KEY,
                $LATITUDE_COLUMN_NAME REAL NOT NULL,
                $LONGITUDE_COLUMN_NAME REAL NOT NULL,
                $GENERATION_TIME_COLUMN_NAME REAL NOT NULL,
                $UTC_OFFSET_COLUMN_NAME INTEGER NOT NULL,
                $TIMEZONE_COLUMN_NAME TEXT NOT NULL,
                $TIMEZONE_ABBREVIATION_COLUMN_NAME TEXT NOT NULL,
                $ELEVATION_COLUMN_NAME REAL NOT NULL,
                $HOURLY_UNIT_ID_COLUMN_NAME INTEGER NOT NULL,
                $HOURLY_ID_COLUMN_NAME INTEGER NOT NULL,
                FOREIGN KEY ($HOURLY_UNIT_ID_COLUMN_NAME) REFERENCES $HOURLY_UNITS_COLUMNS_TABLE_NAME ($ID_HOURLY_UNITS_COLUMN_NAME),
                FOREIGN KEY ($HOURLY_ID_COLUMN_NAME) REFERENCES $HOURLY_TABLE_NAME ($ID_HOURLY_COLUMN_NAME)
            )
        """)

        db.execSQL("""
            CREATE TABLE $HOURLY_UNITS_COLUMNS_TABLE_NAME (
                $ID_COLUMN_NAME INTEGER PRIMARY KEY,
                $ID_HOURLY_UNITS_COLUMN_NAME INTEGER NOT NULL,
                $TIME_UNIT_COLUMN_NAME TEXT NOT NULL,
                $TEMPERATURE_2M_UNIT_COLUMN_NAME TEXT NOT NULL,
                $WEATHERCODE_UNIT_COLUMN_NAME TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE $HOURLY_TABLE_NAME (
                $ID_COLUMN_NAME INTEGER PRIMARY KEY,
                $ID_HOURLY_COLUMN_NAME INTEGER NOT NULL,
                $TIME_COLUMN_NAME TEXT NOT NULL,
                $TEMPERATURE_2M_COLUMN_NAME REAL NOT NULL,
                $WEATHERCODE_COLUMN_NAME INTEGER NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Upgrade database here
        db.execSQL("DROP TABLE IF EXISTS $FORECAST_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $HOURLY_UNITS_COLUMNS_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $HOURLY_TABLE_NAME")
        onCreate(db)
    }

    fun saveCurrentForecast(forecast: Forecast) {
        if (!hasForecast()) {
            insertForecast(forecast)
        } else {
            updateForecast(forecast)
        }
    }

    fun getCurrentForecast(): Forecast? {
        val db = this.writableDatabase
        val forecastQuery = "SELECT * FROM $FORECAST_TABLE_NAME"
        // Execute the SELECT statement for the forecast table
        val forecastCursor = db.rawQuery(forecastQuery, null)

        // Retrieve the ID for the forecast record
        val forecastId = if (forecastCursor.moveToFirst()) {
            forecastCursor.getLong(forecastCursor.getColumnIndexOrThrow(ID_COLUMN_NAME))
        } else {
            null
        }

        // If forecast ID is null, return null for the forecast object
        if (forecastId == null) {
            return null
        }

        // Execute the SELECT statements for the hourly units and hourly tables with their respective IDs
        val hourlyUnitsQuery = "SELECT * FROM $HOURLY_UNITS_COLUMNS_TABLE_NAME WHERE $ID_HOURLY_UNITS_COLUMN_NAME = ?"
        val hourlyQuery = "SELECT * FROM $HOURLY_TABLE_NAME WHERE $ID_HOURLY_COLUMN_NAME = ?"
        val hourlyUnitsCursor = db.rawQuery(hourlyUnitsQuery, arrayOf(forecastId.toString()))
        val hourlyCursor = db.rawQuery(hourlyQuery, arrayOf(forecastId.toString()))

        // Map the Cursor objects to instances of the data classes
        val forecast = (if (hourlyUnitsCursor.moveToFirst()) {
            HourlyUnits(
                time = hourlyUnitsCursor.getString(hourlyUnitsCursor.getColumnIndexOrThrow(TIME_UNIT_COLUMN_NAME)),
                temperature_2m = hourlyUnitsCursor.getString(hourlyUnitsCursor.getColumnIndexOrThrow(TEMPERATURE_2M_UNIT_COLUMN_NAME)),
                weathercode = hourlyUnitsCursor.getString(hourlyUnitsCursor.getColumnIndexOrThrow(WEATHERCODE_UNIT_COLUMN_NAME))
            )
        } else {
            null
        })?.let { hourlyUnits ->
            (if (hourlyCursor.moveToFirst()) {
                val timeList = mutableListOf<String>()
                val temperatureList = mutableListOf<Double>()
                val weatherCodeList = mutableListOf<Int>()
                do {
                    timeList.add(hourlyCursor.getString(hourlyCursor.getColumnIndexOrThrow(TIME_COLUMN_NAME)))
                    temperatureList.add(hourlyCursor.getDouble(hourlyCursor.getColumnIndexOrThrow(TEMPERATURE_2M_COLUMN_NAME)))
                    weatherCodeList.add(hourlyCursor.getInt(hourlyCursor.getColumnIndexOrThrow(WEATHERCODE_COLUMN_NAME)))
                } while (hourlyCursor.moveToNext())
                    Hourly(
                        time = timeList,
                        temperature_2m = temperatureList,
                        weathercode = weatherCodeList
                    )
            } else {
                null
            })?.let { hourly ->
                Forecast(
                    latitude = forecastCursor.getDouble(forecastCursor.getColumnIndexOrThrow(LATITUDE_COLUMN_NAME)),
                    longitude = forecastCursor.getDouble(forecastCursor.getColumnIndexOrThrow(LONGITUDE_COLUMN_NAME)),
                    generationtime_ms = forecastCursor.getDouble(forecastCursor.getColumnIndexOrThrow(GENERATION_TIME_COLUMN_NAME)),
                    utc_offset_seconds = forecastCursor.getInt(forecastCursor.getColumnIndexOrThrow(UTC_OFFSET_COLUMN_NAME)),
                    timezone = forecastCursor.getString(forecastCursor.getColumnIndexOrThrow(TIMEZONE_COLUMN_NAME)),
                    timezone_abbreviation = forecastCursor.getString(forecastCursor.getColumnIndexOrThrow(TIMEZONE_ABBREVIATION_COLUMN_NAME)),
                    elevation = forecastCursor.getDouble(forecastCursor.getColumnIndexOrThrow(ELEVATION_COLUMN_NAME)),
                    hourly_units = hourlyUnits,
                    hourly = hourly
                )
            }
        }

        // Close the forecast cursor
        forecastCursor.close()

        // Close the Cursor objects
        hourlyUnitsCursor.close()
        hourlyCursor.close()

        // Return the forecast object
        return forecast
    }

    private fun hasForecast(): Boolean {
        val db = this.writableDatabase
        val forecastTableNotEmpty = try {
            val countQuery = "SELECT COUNT(*) FROM $FORECAST_TABLE_NAME"
            val cursor = db.rawQuery(countQuery, null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            count > 0
        } catch (e: Exception) {
            false
        }

        return forecastTableNotEmpty
    }

    private fun insertForecast(forecast: Forecast) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(LATITUDE_COLUMN_NAME, forecast.latitude)
            put(LONGITUDE_COLUMN_NAME, forecast.longitude)
            put(GENERATION_TIME_COLUMN_NAME, forecast.generationtime_ms)
            put(UTC_OFFSET_COLUMN_NAME, forecast.utc_offset_seconds)
            put(TIMEZONE_COLUMN_NAME, forecast.timezone)
            put(TIMEZONE_ABBREVIATION_COLUMN_NAME, forecast.timezone_abbreviation)
            put(ELEVATION_COLUMN_NAME, forecast.elevation)
            put(HOURLY_UNIT_ID_COLUMN_NAME, insertHourlyUnits(db, forecast.hourly_units))
            put(HOURLY_ID_COLUMN_NAME, insertHourly(db, forecast.hourly))
        }
        db.insert(FORECAST_TABLE_NAME, null, values)
    }

    private fun insertHourlyUnits(db: SQLiteDatabase, hourlyUnits: HourlyUnits): Int {
        val values = ContentValues().apply {
            put(ID_HOURLY_UNITS_COLUMN_NAME, 1)
            put(TIME_UNIT_COLUMN_NAME, hourlyUnits.time)
            put(TEMPERATURE_2M_UNIT_COLUMN_NAME, hourlyUnits.temperature_2m)
            put(WEATHERCODE_UNIT_COLUMN_NAME, hourlyUnits.weathercode)
        }

        db.insert(HOURLY_UNITS_COLUMNS_TABLE_NAME, null, values)

        return 1
    }

    private fun insertHourly(db: SQLiteDatabase, hourly: Hourly): Int {
        for (i in hourly.time.indices) {
            val values = ContentValues().apply {
                put(ID_HOURLY_COLUMN_NAME, 1)
                put(TIME_COLUMN_NAME, hourly.time[i])
                put(TEMPERATURE_2M_COLUMN_NAME, hourly.temperature_2m[i])
                put(WEATHERCODE_COLUMN_NAME, hourly.weathercode[i])
            }

            db.insert(HOURLY_TABLE_NAME, null, values)
        }

        return 1
    }

    private fun updateForecast(forecast: Forecast) {
        val db = this.writableDatabase

        val forecastID = "1"
        // Update the forecast data
        val forecastValues = ContentValues().apply {
            put(LATITUDE_COLUMN_NAME, forecast.latitude)
            put(LONGITUDE_COLUMN_NAME, forecast.longitude)
            put(GENERATION_TIME_COLUMN_NAME, forecast.generationtime_ms)
            put(UTC_OFFSET_COLUMN_NAME, forecast.utc_offset_seconds)
            put(TIMEZONE_COLUMN_NAME, forecast.timezone)
            put(TIMEZONE_ABBREVIATION_COLUMN_NAME, forecast.timezone_abbreviation)
            put(ELEVATION_COLUMN_NAME, forecast.elevation)
        }

        db.update(
            FORECAST_TABLE_NAME,
            forecastValues,
            "$ID_COLUMN_NAME = ?",
            arrayOf(forecastID)
        )

        // Delete the existing hourly data for the given forecast ID
        db.delete(
            HOURLY_UNITS_COLUMNS_TABLE_NAME,
            "$ID_HOURLY_UNITS_COLUMN_NAME = ?",
            arrayOf(forecastID)
        )

        db.delete(
            HOURLY_TABLE_NAME,
            "$ID_HOURLY_COLUMN_NAME = ?",
            arrayOf(forecastID)
        )

        // Insert new hourly data for the given forecast ID
        forecast.hourly_units.let { hourlyUnits ->
            if (hourlyUnits.time.isNotBlank() && hourlyUnits.temperature_2m.isNotBlank() && hourlyUnits.weathercode.isNotBlank()) {
                val hourlyUnitsValues = ContentValues().apply {
                    put(ID_HOURLY_UNITS_COLUMN_NAME, forecastID)
                    put(TIME_UNIT_COLUMN_NAME, hourlyUnits.time)
                    put(TEMPERATURE_2M_UNIT_COLUMN_NAME, hourlyUnits.temperature_2m)
                    put(WEATHERCODE_UNIT_COLUMN_NAME, hourlyUnits.weathercode)
                }
                db.insert(HOURLY_UNITS_COLUMNS_TABLE_NAME, null, hourlyUnitsValues)
            }
        }

        forecast.hourly.let { hourly ->
            if (hourly.time.isNotEmpty() && hourly.temperature_2m.isNotEmpty() && hourly.weathercode.isNotEmpty()) {
                val hourlyList = mutableListOf<ContentValues>()
                for (i in hourly.time.indices) {
                    val hourlyValues = ContentValues().apply {
                        put(ID_HOURLY_COLUMN_NAME, forecastID)
                        put(TIME_COLUMN_NAME, hourly.time[i])
                        put(TEMPERATURE_2M_COLUMN_NAME, hourly.temperature_2m[i])
                        put(WEATHERCODE_COLUMN_NAME, hourly.weathercode[i])
                    }
                    hourlyList.add(hourlyValues)
                }
                db.beginTransaction()
                try {
                    for (hourlyValues in hourlyList) {
                        db.insert(HOURLY_TABLE_NAME, null, hourlyValues)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }
}