package com.enigma.tv.data

import retrofit2.http.GET
import retrofit2.http.Path

interface StreamedApi {
    @GET("api/matches/live")
    suspend fun liveMatches(): List<StreamedMatchDto>

    @GET("api/matches/{sport}")
    suspend fun sportMatches(@Path("sport") sport: String): List<StreamedMatchDto>

    @GET("api/stream/{source}/{id}")
    suspend fun streams(
        @Path("source") source: String,
        @Path("id") id: String
    ): List<StreamedStreamDto>
}

data class StreamedMatchDto(
    val id: String,
    val title: String,
    val category: String,
    val date: Long = 0,
    val poster: String? = null,
    val popular: Boolean = false,
    val sources: List<StreamedSourceDto> = emptyList()
)

data class StreamedSourceDto(
    val source: String,
    val id: String
)

data class StreamedStreamDto(
    val embedUrl: String,
    val streamNo: Int = 1,
    val language: String = "",
    val hd: Boolean = false,
    val source: String = ""
)
