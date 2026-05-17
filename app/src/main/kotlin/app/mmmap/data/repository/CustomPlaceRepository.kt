package app.mmmap.data.repository

import android.content.Context
import app.mmmap.data.places.CustomPlaceCatalog
import app.mmmap.domain.model.CustomPlace
import app.mmmap.domain.model.CustomPlaceCollection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

private fun JsonElement?.nullableString(): String? =
    if (this == null || this is JsonNull) null
    else jsonPrimitive.content.takeIf { it.isNotEmpty() }

@Singleton
class CustomPlaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cache: List<CustomPlace>? = null

    val activeCollection: CustomPlaceCollection? get() = CustomPlaceCatalog.ACTIVE

    suspend fun loadActive(): List<CustomPlace> = mutex.withLock {
        cache ?: parseAsset(CustomPlaceCatalog.ACTIVE?.assetPath ?: return@withLock emptyList())
            .also { cache = it }
    }

    private suspend fun parseAsset(path: String): List<CustomPlace> = withContext(Dispatchers.IO) {
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            CustomPlace(
                id        = obj["id"]!!.jsonPrimitive.content,
                name      = obj["name"]!!.jsonPrimitive.content,
                latitude  = obj["latitude"]!!.jsonPrimitive.double,
                longitude = obj["longitude"]!!.jsonPrimitive.double,
                address   = obj["address"].nullableString(),
                notes     = obj["notes"].nullableString(),
            )
        }
    }
}
