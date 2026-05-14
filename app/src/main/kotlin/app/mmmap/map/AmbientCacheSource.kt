package app.mmmap.map

interface AmbientCacheSource {
    suspend fun setMaxBytes(bytes: Long)
    suspend fun invalidate()
    suspend fun clear()
}
