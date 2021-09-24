package org.abubaker.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import org.abubaker.weatherapp.utils.Constants
import org.abubaker.weatherapp.R
import org.abubaker.weatherapp.databinding.ActivityMainBinding
import org.abubaker.weatherapp.models.WeatherResponse
import org.abubaker.weatherapp.network.WeatherService
import retrofit2.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Binding Object
    private lateinit var binding: ActivityMainBinding

    // A fused location client variable which is further used to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    // A global variable for the Progress Dialog
    private var mProgressDialog: Dialog? = null

    // A global variable for the SharedPreferences
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this@MainActivity, R.layout.activity_main)

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the SharedPreferences variable (it requires a NAME and a MODE,
        // which we will define in the constants.kt file
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        // Check here whether GPS is ON or OFF using the method which we have created
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)

        } else {

            // Asking the location permission on runtime.
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {

                            // Call the location request function here.
                            requestLocationData()

                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()

        }

    }

    /**
     * inflate menu_main.xml file and add functionality for the clicked item
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_refresh -> {
                // getLocationWeatherDetails()
                requestLocationData()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * A function which is used to verify that the location or GPS is enabled or not.
     */
    private fun isLocationEnabled(): Boolean {

        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager


        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }


    /**
     * A function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
     */
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    /**
     * A function to request the current location. Using the fused location provider client.
     */
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        // val mLocationRequest = LocationRequest()
        // mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        //
        val mLocationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        //
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }


        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    /**
     * A location callback object of used location provider client where we will get the current location details.
     */
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")

            // Call the api calling function here
            getLocationWeatherDetails(latitude, longitude)

            // This will ensure that the data will be refreshed while displaying the LOADING animation
            mFusedLocationClient.removeLocationUpdates(this);
        }
    }


    /**
     * Function is used to get the weather details of the current location based on the latitude longitude
     */
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {

        if (Constants.isNetworkAvailable(this@MainActivity)) {

            /**
             * Add the built-in converter factory first. This prevents overriding its
             * behavior but also ensures correct behavior when using converters that consume all types.
             */
            val retrofit: Retrofit = Retrofit.Builder()

                // API base URL.
                .baseUrl(Constants.BASE_URL)

                /** Add converter factory for serialization and deserialization of objects. */
                /**
                 * Create an instance using a default {@link Gson} instance for conversion. Encoding to JSON and
                 * decoding from JSON (when no charset is specified by a header) will use UTF-8.
                 */
                .addConverterFactory(GsonConverterFactory.create())

                /** Create the Retrofit instances. */
                .build()

            /**
             * Here we map the service interface in which we declares the end point and the API type
             *i.e GET, POST and so on along with the request parameter which are required.
             */
            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            /** An invocation of a Retrofit method that sends a request to a web-server and returns a response.
             * Here we pass the required param in the service
             */
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,
                longitude,
                Constants.METRIC_UNIT,
                Constants.APP_ID
            )

            // Used to show the progress dialog
            showCustomProgressDialog()

            // Callback methods are executed using the Retrofit callback executor.
            listCall.enqueue(object : Callback<WeatherResponse> {

                @SuppressLint("SetTextI18n")

                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {

                    // Check weather the response is success or not.
                    if (response.isSuccessful) {

                        // Hides the progress dialog
                        hideProgressDialog()

                        /** The de-serialized response body of a successful response. */
                        val weatherList: WeatherResponse? = response.body()

                        // Store the data using SharedPreferences
                        val weatherResponseJSONString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()

                        // weatherResponseJSONString = We are storing weather_response_data as a String
                        // at position = WEATHER_RESPONSE_DATA
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJSONString)

                        // Commit the changes
                        editor.apply()

                        // Populate values received from weatherList (Response) into the UI
                        if (weatherList != null) {
                            // setupUI(weatherList)
                            setupUI()
                        }

                        //
                        Log.i("Response Result", "$weatherList")

                    } else {

                        // If the response is not success then we check the response code.
                        when (response.code()) {

                            // Bad Request
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }

                            // Not Found
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }

                            // Generic
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }

                }

                // On failure
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrrr", t.message.toString())
                }

            })

        } else {

            // No Connection
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()

        }
    }

    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    /**
     * Function is used to set the result in the UI elements.
     */

    // private fun setupUI(weatherList: WeatherResponse)
    private fun setupUI() {

        // Get list from our SharedPreferences
        val weatherResponseJSONString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJSONString.isNullOrEmpty()) {

            // Get DATA back from the SharedPreferences
            val weatherList =
                Gson().fromJson(weatherResponseJSONString, WeatherResponse::class.java)

            // For loop to get the required data. And all are populated in the UI.
            for (z in weatherList.weather.indices) {
                Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

                binding.tvMain.text = weatherList.weather[z].main
                binding.tvMainDescription.text = weatherList.weather[z].description

                // Note: Locale.getDefault() can return country code
                binding.tvTemp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country
                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong()) + " AM"
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong()) + " PM"


                // Reference for Icons List (Codes): https://openweathermap.org/weather-conditions
                when (weatherList.weather[z].icon) {

                    // Clear Sky
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)

                    // Few Clouds
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)

                    // Scattered Clouds
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)

                    // Broken Clouds
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)

                    // Rain
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)

                    // Thunderstorm
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)

                    // Snow
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)

                }
            }
        }


    }

    /**
     * Function is used to get the temperature unit value.
     */
    private fun getUnit(value: String): String {

        // Log for reference
        Log.i("Temperature Unit", value)

        // Default Value
        var value = "°C"

        // US -> USA
        // LR -> Liberia
        // MM -> Myanmar
        // Reference: https://www.iso.org/obp/ui/#search
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }

        // Return chosen UNIT
        return value
    }

    /**
     * The function is used to get the formatted time based on the Format and the LOCALE we pass to it.
     * Reference: Epoch & Unix Timestamp Conversion Tools - https://www.epochconverter.com/
     */
    private fun unixTime(timex: Long): String? {

        // We are going to pass the time as a Long Value:
        // i.e. 1632462389 = (Friday, September 24, 2021 5:46:24 AM)

        // ... * 1000L will help in converting time from milliseconds
        val date = Date(timex * 1000L)

        // Reference: How to set 24-hours format for date on java?
        // https://stackoverflow.com/questions/8907509/how-to-set-24-hours-format-for-date-on-java

        // hh:mm:ss = 12 Hour Format
        // HH:mm:ss = 24 Hour Format

        // Using Locale.US is a safeguard, as received data might be corrupt
        @SuppressLint("SimpleDateFormat")
        val sdf = SimpleDateFormat("hh:mm:ss", Locale.US)

        // Timezone = Convert to DefaultK Timezone
        sdf.timeZone = TimeZone.getDefault()

        // It will return final formatted and converted time/date
        return sdf.format(date)
    }

}