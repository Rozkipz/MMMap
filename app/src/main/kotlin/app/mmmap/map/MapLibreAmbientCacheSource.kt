package app.mmmap.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.offline.OfflineManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class MapLibreAmbientCacheSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : AmbientCacheSource {

    override suspend fun setMaxBytes(bytes: Long) = mainCoroutine {
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(bytes, callbackFor(it))
    }

    override suspend fun invalidate() = mainCoroutine {
        OfflineManager.getInstance(context).invalidateAmbientCache(callbackFor(it))
    }

    override suspend fun clear() = mainCoroutine {
        OfflineManager.getInstance(context).clearAmbientCache(callbackFor(it))
    }

    private fun callbackFor(cont: kotlin.coroutines.Continuation<Unit>) =
        object : OfflineManager.FileSourceCallback {
            override fun onSuccess() = cont.resume(Unit)
            override fun onError(message: String) = cont.resumeWithException(RuntimeException(message))
        }

    private suspend fun mainCoroutine(block: (kotlin.coroutines.Continuation<Unit>) -> Unit) =
        withContext(Dispatchers.Main) { suspendCoroutine(block) }
}
