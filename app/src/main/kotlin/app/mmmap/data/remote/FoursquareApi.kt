package app.mmmap.data.remote

import app.mmmap.data.remote.models.FsqPlaceResponse
import app.mmmap.data.remote.models.FsqSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface FoursquareApi {

    @GET("v3/places/search")
    suspend fun searchPlaces(
        @Header("Authorization") apiKey: String,
        @Query("ll") latLon: String,
        @Query("query") query: String,
        @Query("radius") radius: Int = 200,
        @Query("limit") limit: Int = 5,
        @Query("fields") fields: String = "fsq_id,name,geocodes,location,distance",
    ): FsqSearchResponse

    @GET("v3/places/{fsqId}")
    suspend fun getPlace(
        @Header("Authorization") apiKey: String,
        @Path("fsqId") fsqId: String,
        @Query("fields") fields: String = "fsq_id,name,photos,hours,tel,rating",
    ): FsqPlaceResponse

    companion object {
        const val BASE_URL = "https://api.foursquare.com/"
    }
}
