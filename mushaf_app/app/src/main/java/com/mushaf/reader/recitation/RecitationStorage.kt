package com.mushaf.reader.recitation

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.mushaf.reader.data.recitation.RecitationRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Where recitation audio lives on the device: one cache shared by playback and downloads, so a
 * surah heard once (or the part of it that was heard) plays again without the network, and a
 * repeated ayah is fetched only the first time.
 *
 * The cache never evicts on its own (downloads must stay). Audio cached only by listening is kept
 * to [AUTO_LIMIT_BYTES] by [RecitationDownloads.trimAutoCache], oldest first.
 *
 * Process-wide singletons: a second cache over the same folder would throw, and the playback and
 * download services run in the app's one process.
 */
@OptIn(UnstableApi::class)
object RecitationStorage {

    const val AUTO_LIMIT_BYTES = 500L * 1024 * 1024

    private var cache: SimpleCache? = null
    private var database: StandaloneDatabaseProvider? = null
    private var downloads: DownloadManager? = null

    @Synchronized
    fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.filesDir, "recitation/audio"),
        NoOpCacheEvictor(),
        database(context),
    ).also { cache = it }

    @Synchronized
    private fun database(context: Context): StandaloneDatabaseProvider =
        database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

    fun httpFactory(): DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(RecitationRepository.USER_AGENT)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    /** For the player: the cache first, the network only for what is missing — and not even that
     *  on mobile data unless the reader allowed it. */
    fun playbackDataSourceFactory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        val network = DefaultDataSource.Factory(app, httpFactory())
        return CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(MeteredGuard.Factory(app, network))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Must be called on the main thread, the manager's thread. */
    @Synchronized
    fun downloadManager(context: Context): DownloadManager {
        downloads?.let { return it }
        val app = context.applicationContext
        return DownloadManager(
            app, database(app), cache(app), httpFactory(), Executors.newFixedThreadPool(2),
        ).apply {
            maxParallelDownloads = 2
            requirements = MobileData.downloadRequirements(app)
        }.also { downloads = it }
    }

    /** The whole file at [url] is on the device. Blocking: call off the main thread. */
    fun isFullyCached(context: Context, url: String): Boolean {
        val c = cache(context)
        val length = ContentMetadata.getContentLength(c.getContentMetadata(url))
        return length > 0 && c.isCached(url, 0, length)
    }

    /** Refuses to open the network when the reader chose not to stream on mobile data. */
    private class MeteredGuard(
        private val context: Context,
        private val upstream: DataSource,
    ) : DataSource by upstream {

        override fun open(dataSpec: DataSpec): Long {
            if (MobileData.blocksStreaming(context)) throw MobileDataBlockedException()
            return upstream.open(dataSpec)
        }

        class Factory(private val context: Context, private val upstream: DataSource.Factory) :
            DataSource.Factory {
            override fun createDataSource(): DataSource =
                MeteredGuard(context, upstream.createDataSource())
        }
    }

    class MobileDataBlockedException : IOException("Streaming on mobile data is not allowed")
}
