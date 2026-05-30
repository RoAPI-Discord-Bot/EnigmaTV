package com.enigma.tv.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface OmdbApi {
    @GET("/")
    suspend fun getRatings(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String = "8b1a5800"
    ): OmdbResponse
}

data class OmdbResponse(
    @SerializedName("Ratings") val ratings: List<OmdbRating>? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null
)

data class OmdbRating(
    @SerializedName("Source") val source: String,
    @SerializedName("Value") val value: String
)
