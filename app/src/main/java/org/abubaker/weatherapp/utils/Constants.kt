package org.abubaker.weatherapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.abubaker.weatherapp.BuildConfig

object Constants {

    // Add the API key and Base URL and Metric unit here from openweathermap
    // URL: https://api.openweathermap.org/data/2.5/forecast?callback=response&q={CITY}&appid={API_KEY}
    const val APP_ID: String = BuildConfig.WeatherApp_KEY
    const val BASE_URL: String = "http://api.openweathermap.org/data/"
    const val METRIC_UNIT: String = "metric"

    /**
     * This function is used check the weather the device is connected to the Internet or not.
     */
    fun isNetworkAvailable(context: Context): Boolean {

        // It answers the queries about the state of network connectivity.
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // For SDK > 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // If we have an active network
            val network = connectivityManager.activeNetwork ?: return false

            // Get information about the NETWORK availability
            val activeNetWork = connectivityManager.getNetworkCapabilities(network) ?: return false


            // Inform the user which TYPE of connection is available
            return when {

                // WIFI
                activeNetWork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

                // SIM DATA Connection
                activeNetWork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

                // LAN
                activeNetWork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true

                // No Network Available
                else -> false

            }

        } else {

            // For SDK < 23
            // Returns details about the currently active default data network.
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }

}