package app.mmmap.data.remote

import app.mmmap.data.remote.models.GithubRelease
import retrofit2.http.GET

interface GitHubReleasesApi {

    @GET("repos/ngshiheng/michelin-my-maps/releases/latest")
    suspend fun latestRelease(): GithubRelease

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}
