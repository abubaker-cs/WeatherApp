package org.abubaker.weatherapp.network

import retrofit2.Call
import org.abubaker.weatherapp.models.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * An Interface which defines the HTTP operations Functions.
 */
interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?
    ): Call<WeatherResponse>
}