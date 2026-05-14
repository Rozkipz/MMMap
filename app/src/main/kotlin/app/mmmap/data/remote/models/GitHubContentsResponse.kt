package app.mmmap.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class GitHubContentsResponse(val sha: String)
