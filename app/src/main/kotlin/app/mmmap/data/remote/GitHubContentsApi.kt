package app.mmmap.data.remote

import app.mmmap.data.remote.models.GitHubContentsResponse
import retrofit2.http.GET

interface GitHubContentsApi {

    @GET("repos/ngshiheng/michelin-my-maps/contents/data/michelin_my_maps.csv")
    suspend fun csvMetadata(): GitHubContentsResponse

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val CSV_RAW_URL =
            "https://raw.githubusercontent.com/ngshiheng/michelin-my-maps/main/data/michelin_my_maps.csv"
    }
}
